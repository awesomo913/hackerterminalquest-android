package com.hackerquest.terminal.engine

import com.hackerquest.terminal.data.Level
import com.hackerquest.terminal.data.LevelObjective
import com.hackerquest.terminal.data.LineType
import com.hackerquest.terminal.data.TerminalLine

data class CommandResult(
    val lines: List<TerminalLine>,
    val advanceObjective: Boolean = false,
    val newCwd: String? = null,
    val clearScreen: Boolean = false,
    val showHint: Boolean = false
)

class CommandParser {

    fun process(
        rawInput: String,
        level: Level,
        currentObjective: LevelObjective?,
        cwd: String
    ): CommandResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return CommandResult(emptyList())

        val spaceIdx = trimmed.indexOf(' ')
        val cmd = if (spaceIdx == -1) trimmed.lowercase() else trimmed.substring(0, spaceIdx).lowercase()
        val args = if (spaceIdx == -1) "" else trimmed.substring(spaceIdx + 1).trim()

        val lines = mutableListOf<TerminalLine>()
        var advanceObjective = false
        var newCwd: String? = null

        when (cmd) {
            "help" -> lines.addAll(buildHelp())
            "clear" -> return CommandResult(emptyList(), clearScreen = true)
            "whoami" -> lines.add(TerminalLine(level.username, LineType.OUTPUT))
            "hostname" -> lines.add(TerminalLine(level.hostname, LineType.OUTPUT))
            "pwd" -> lines.add(TerminalLine(cwd, LineType.OUTPUT))
            "uname" -> lines.add(TerminalLine("Linux vault-node 5.15.0-generic #72-Ubuntu SMP x86_64", LineType.OUTPUT))
            "date" -> lines.add(TerminalLine("Mon Jan 15 22:47:33 UTC 2024", LineType.OUTPUT))
            "id" -> lines.add(TerminalLine("uid=1337(${level.username}) gid=1337(${level.username}) groups=1337(${level.username}),4(adm),27(sudo)", LineType.OUTPUT))

            "ls" -> {
                val path = if (args.isBlank()) cwd else resolvePath(cwd, args.split(" ").last())
                lines.addAll(buildLs(path, level.filesystem, args.contains("-la") || args.contains("-l")))
            }
            "cd" -> {
                if (args.isBlank() || args == "~") {
                    newCwd = "/home/${level.username}"
                } else {
                    val target = resolvePath(cwd, args)
                    if (dirExists(target, level.filesystem)) {
                        newCwd = target
                    } else {
                        lines.add(TerminalLine("cd: $args: No such file or directory", LineType.ERROR))
                    }
                }
            }
            "cat" -> {
                if (args.isBlank()) {
                    lines.add(TerminalLine("cat: missing operand", LineType.ERROR))
                } else {
                    val path = resolvePath(cwd, args)
                    val content = level.filesystem[path]
                    if (content != null) {
                        content.split("\n").forEach { line ->
                            lines.add(TerminalLine(line, LineType.OUTPUT))
                        }
                    } else {
                        lines.add(TerminalLine("cat: $args: No such file or directory", LineType.ERROR))
                    }
                }
            }
            "grep" -> lines.addAll(handleGrep(args, cwd, level.filesystem))
            "find" -> lines.addAll(handleFind(args, level.filesystem))

            "ssh" -> lines.addAll(handleSsh(args, level))
            "nmap" -> lines.addAll(handleNmap(args))
            "ping" -> lines.addAll(handlePing(args))
            "netstat" -> lines.addAll(handleNetstat(level))
            "ps" -> lines.addAll(handlePs(level))
            "kill" -> lines.addAll(handleKill(args, level))
            "crack" -> lines.addAll(handleCrack(args, level))
            "decrypt" -> lines.addAll(handleDecrypt(args))
            "upload" -> lines.addAll(handleUpload(args))
            "wget" -> lines.addAll(handleWget(args))
            "sudo" -> lines.addAll(handleSudo(args))

            "history" -> {
                lines.add(TerminalLine("    1  whoami", LineType.OUTPUT))
                lines.add(TerminalLine("    2  ls", LineType.OUTPUT))
                lines.add(TerminalLine("    3  help", LineType.OUTPUT))
            }
            "hint" -> return CommandResult(emptyList(), showHint = true)
            "exit", "quit", "logout" -> {
                lines.add(TerminalLine("logout", LineType.SYSTEM))
                lines.add(TerminalLine("Connection to ${level.hostname} closed.", LineType.SYSTEM))
            }
            else -> lines.add(TerminalLine("$cmd: command not found  (try 'help')", LineType.ERROR))
        }

        // Check if this command matches the current level objective
        if (currentObjective != null) {
            val argMatch = currentObjective.triggerArgContains.isEmpty() ||
                args.contains(currentObjective.triggerArgContains, ignoreCase = true)
            if (cmd == currentObjective.triggerCommand && argMatch) {
                currentObjective.response.forEach { r ->
                    val lineType = when {
                        r.startsWith("[STORY]")   -> LineType.STORY
                        r.startsWith("[SUCCESS]") -> LineType.SUCCESS
                        r.startsWith("[WARNING]") -> LineType.WARNING
                        r.startsWith("[NET]")     -> LineType.SYSTEM
                        r.isEmpty()               -> LineType.OUTPUT
                        else                      -> LineType.SYSTEM
                    }
                    lines.add(TerminalLine(r, lineType))
                }
                advanceObjective = true
            }
        }

        return CommandResult(lines, advanceObjective = advanceObjective, newCwd = newCwd)
    }

    // ── Path utilities ────────────────────────────────────────────────────────

    private fun resolvePath(cwd: String, path: String): String {
        if (path.startsWith("/")) return normalizePath(path)
        return normalizePath("$cwd/$path")
    }

    private fun normalizePath(path: String): String {
        val parts = mutableListOf<String>()
        path.split("/").forEach { segment ->
            when (segment) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.add(segment)
            }
        }
        return "/" + parts.joinToString("/")
    }

    private fun dirExists(path: String, fs: Map<String, String>): Boolean {
        if (path == "/") return true
        val prefix = if (path.endsWith("/")) path else "$path/"
        return fs.keys.any { it.startsWith(prefix) || it == path }
    }

    // ── Filesystem commands ───────────────────────────────────────────────────

    private fun buildLs(
        path: String,
        fs: Map<String, String>,
        longFormat: Boolean
    ): List<TerminalLine> {
        val normalized = if (path.endsWith("/")) path else "$path/"
        val entries = mutableSetOf<String>()

        fs.keys.forEach { filePath ->
            if (filePath.startsWith(normalized)) {
                val remainder = filePath.removePrefix(normalized)
                if (remainder.isNotEmpty()) {
                    val topEntry = remainder.split("/").first()
                    entries.add(topEntry)
                }
            }
        }

        if (entries.isEmpty()) {
            // Check if path itself is a file
            if (fs.containsKey(path)) {
                return listOf(TerminalLine(path.split("/").last(), LineType.OUTPUT))
            }
            return listOf(TerminalLine("ls: cannot access '$path': No such file or directory", LineType.ERROR))
        }

        val lines = mutableListOf<TerminalLine>()
        if (longFormat) {
            lines.add(TerminalLine("total ${entries.size * 4}", LineType.OUTPUT))
            entries.sorted().forEach { entry ->
                val fullPath = "$normalized$entry"
                val isDir = fs.keys.any { it.startsWith("$fullPath/") }
                val perm = if (isDir) "drwxr-xr-x" else "-rw-r--r--"
                val size = fs[fullPath]?.length ?: 0
                lines.add(TerminalLine("$perm  1 root root  $size Jan 15 03:00 $entry", LineType.OUTPUT))
            }
        } else {
            lines.add(TerminalLine(entries.sorted().joinToString("  "), LineType.OUTPUT))
        }
        return lines
    }

    private fun handleGrep(args: String, cwd: String, fs: Map<String, String>): List<TerminalLine> {
        val parts = args.trim().split("\\s+".toRegex())
        if (parts.size < 2) return listOf(TerminalLine("Usage: grep <pattern> <file>", LineType.ERROR))
        val pattern = parts[0]
        val filePath = resolvePath(cwd, parts.last())
        val content = fs[filePath]
            ?: return listOf(TerminalLine("grep: $filePath: No such file or directory", LineType.ERROR))
        val matches = content.split("\n").filter { it.contains(pattern, ignoreCase = true) }
        return if (matches.isEmpty()) {
            listOf(TerminalLine("(no matches)", LineType.SYSTEM))
        } else {
            matches.map { TerminalLine(it, LineType.OUTPUT) }
        }
    }

    private fun handleFind(args: String, fs: Map<String, String>): List<TerminalLine> {
        val pattern = args.substringAfter("-name").trim().trim('"', '\'')
        return if (pattern.isBlank()) {
            fs.keys.sorted().map { TerminalLine(it, LineType.OUTPUT) }
        } else {
            fs.keys
                .filter { it.contains(pattern.replace("*", ""), ignoreCase = true) }
                .sorted()
                .map { TerminalLine(it, LineType.OUTPUT) }
                .ifEmpty { listOf(TerminalLine("(no results)", LineType.SYSTEM)) }
        }
    }

    // ── Network / hacker commands ─────────────────────────────────────────────

    private fun handleSsh(args: String, level: Level): List<TerminalLine> {
        val target = args.substringAfterLast("@").trim().split(" ").first()
        return listOf(
            TerminalLine("SSH] Connecting to $target...", LineType.SYSTEM),
            TerminalLine("[SSH] Host key verified.", LineType.SYSTEM),
            TerminalLine("[SSH] Authenticating as ${args.substringBefore("@").trimStart('-').trim()}...", LineType.SYSTEM),
            TerminalLine("[SSH] Connected.", LineType.SYSTEM)
        )
    }

    private fun handleNmap(args: String): List<TerminalLine> {
        return listOf(
            TerminalLine("Starting Nmap 7.94 ( https://nmap.org )", LineType.OUTPUT),
            TerminalLine("Scanning network...", LineType.SYSTEM),
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("Nmap scan report for 10.0.0.1 (gateway.megacorp.net)", LineType.OUTPUT),
            TerminalLine("Host is up (0.002s latency).", LineType.OUTPUT),
            TerminalLine("PORT    STATE SERVICE", LineType.OUTPUT),
            TerminalLine("22/tcp  open  ssh", LineType.OUTPUT),
            TerminalLine("443/tcp open  https", LineType.OUTPUT),
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("Nmap scan report for 10.0.0.12 (archive.megacorp.net)", LineType.OUTPUT),
            TerminalLine("Host is up (0.003s latency).", LineType.OUTPUT),
            TerminalLine("PORT     STATE SERVICE", LineType.OUTPUT),
            TerminalLine("22/tcp   open  ssh", LineType.OUTPUT),
            TerminalLine("3306/tcp open  mysql", LineType.OUTPUT),
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("Nmap scan report for 10.0.0.99", LineType.OUTPUT),
            TerminalLine("Host is up (0.001s latency).", LineType.SUCCESS),
            TerminalLine("PORT     STATE SERVICE", LineType.OUTPUT),
            TerminalLine("9443/tcp open  unknown", LineType.SUCCESS),
            TerminalLine("22/tcp   open  ssh", LineType.OUTPUT),
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("Nmap done: 256 IP addresses (3 hosts up) scanned in 4.23 seconds", LineType.OUTPUT)
        )
    }

    private fun handlePing(args: String): List<TerminalLine> {
        val host = args.trim().split(" ").first()
        return listOf(
            TerminalLine("PING $host: 56 data bytes", LineType.OUTPUT),
            TerminalLine("64 bytes from $host: icmp_seq=0 ttl=64 time=1.247 ms", LineType.OUTPUT),
            TerminalLine("64 bytes from $host: icmp_seq=1 ttl=64 time=0.983 ms", LineType.OUTPUT),
            TerminalLine("64 bytes from $host: icmp_seq=2 ttl=64 time=1.101 ms", LineType.OUTPUT),
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("--- $host ping statistics ---", LineType.OUTPUT),
            TerminalLine("3 packets transmitted, 3 received, 0.0% packet loss", LineType.SUCCESS)
        )
    }

    private fun handleNetstat(level: Level): List<TerminalLine> {
        return listOf(
            TerminalLine("Active Internet connections (w/o servers)", LineType.OUTPUT),
            TerminalLine("Proto Recv-Q Send-Q  Local Address         Foreign Address       State", LineType.OUTPUT),
            TerminalLine("tcp        0      0  10.0.0.12:22          10.0.0.1:49821        ESTABLISHED", LineType.OUTPUT),
            TerminalLine("tcp        0      0  10.0.0.99:9443        10.0.0.12:54102       ESTABLISHED", LineType.OUTPUT)
        )
    }

    private fun handlePs(level: Level): List<TerminalLine> {
        val monitorLine = if (level.id == 5) {
            TerminalLine("root      9847  0.8  0.5  98234  5120 ?    S    22:47  /opt/security/monitor_daemon", LineType.WARNING)
        } else null
        val lines = mutableListOf(
            TerminalLine("USER       PID %CPU %MEM    VSZ   RSS  STAT  TIME COMMAND", LineType.OUTPUT),
            TerminalLine("root         1  0.0  0.1  19568  1604  Ss    0:00 /sbin/init", LineType.OUTPUT),
            TerminalLine("root       423  0.0  0.3  55348  3200  Ss    0:00 /usr/sbin/sshd", LineType.OUTPUT)
        )
        if (monitorLine != null) lines.add(monitorLine)
        lines.add(TerminalLine("${level.username}  9901  0.0  0.1  20132  1820  Ss    0:00 -bash", LineType.OUTPUT))
        lines.add(TerminalLine("${level.username}  9934  0.0  0.0  17508   952  R+    0:00 ps -aux", LineType.OUTPUT))
        return lines
    }

    private fun handleKill(args: String, level: Level): List<TerminalLine> {
        val pid = args.trim().split(" ").last()
        return if (pid == "9847") {
            listOf(
                TerminalLine("[SYS] Sending SIGKILL to PID 9847...", LineType.SYSTEM),
                TerminalLine("[SYS] Process 9847 (monitor_daemon) terminated.", LineType.SUCCESS)
            )
        } else {
            listOf(TerminalLine("kill: ($pid) - No such process", LineType.ERROR))
        }
    }

    private fun handleCrack(args: String, level: Level): List<TerminalLine> {
        val file = args.trim().split(" ").last()
        if (!file.contains("evidence") && !file.contains(".enc")) {
            return listOf(TerminalLine("crack: $file: Not a recognized encrypted format", LineType.ERROR))
        }
        return listOf(
            TerminalLine("[CRACK] Analyzing cipher: AES-256-CBC", LineType.SYSTEM),
            TerminalLine("[CRACK] Loading wordlist: /usr/share/wordlists/rockyou.txt", LineType.SYSTEM),
            TerminalLine("[CRACK] Trying key derivation attack...", LineType.SYSTEM),
            TerminalLine("[CRACK] 0%   [                    ] 0/14344394", LineType.OUTPUT),
            TerminalLine("[CRACK] 34%  [███████             ] 4,877,094/14,344,394", LineType.OUTPUT),
            TerminalLine("[CRACK] 67%  [█████████████       ] 9,610,742/14,344,394", LineType.OUTPUT),
            TerminalLine("[CRACK] 91%  [██████████████████  ] 13,053,198/14,344,394", LineType.OUTPUT),
            TerminalLine("[CRACK] KEY FOUND: M3gaC0rp_V@ult_2024_AES!", LineType.SUCCESS),
            TerminalLine("[CRACK] Decrypting... DONE", LineType.SUCCESS),
            TerminalLine("[CRACK] Output: ${file.replace(".enc", ".zip")}", LineType.SUCCESS)
        )
    }

    private fun handleDecrypt(args: String): List<TerminalLine> {
        return listOf(TerminalLine("Use 'crack <file>' to decrypt encrypted files.", LineType.SYSTEM))
    }

    private fun handleUpload(args: String): List<TerminalLine> {
        val file = args.trim().split(" ").last()
        if (!file.contains("evidence")) {
            return listOf(TerminalLine("upload: $file: File not found or not ready for upload", LineType.ERROR))
        }
        return listOf(
            TerminalLine("[NET] Routing through Tor network...", LineType.SYSTEM),
            TerminalLine("[NET] Circuit built: 3 relays", LineType.SYSTEM),
            TerminalLine("[NET] Connecting to drop.0x4e.net...", LineType.SYSTEM),
            TerminalLine("[NET] Connected. Starting upload.", LineType.SYSTEM)
        )
    }

    private fun handleWget(args: String): List<TerminalLine> {
        return listOf(
            TerminalLine("wget: HTTPS request sent.", LineType.SYSTEM),
            TerminalLine("wget: 200 OK — saved.", LineType.SUCCESS)
        )
    }

    private fun handleSudo(args: String): List<TerminalLine> {
        return listOf(
            TerminalLine("[sudo] password for vault_root: ", LineType.SYSTEM),
            TerminalLine("vault_root is not in the sudoers file.", LineType.WARNING)
        )
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private fun buildHelp(): List<TerminalLine> {
        return listOf(
            TerminalLine("┌─ Available Commands ─────────────────────┐", LineType.SYSTEM),
            TerminalLine("│  Navigation                               │", LineType.SYSTEM),
            TerminalLine("│   ls [path]      list directory contents  │", LineType.OUTPUT),
            TerminalLine("│   ls -la [path]  detailed listing         │", LineType.OUTPUT),
            TerminalLine("│   cd <path>      change directory         │", LineType.OUTPUT),
            TerminalLine("│   pwd            print working directory  │", LineType.OUTPUT),
            TerminalLine("│   cat <file>     read file contents       │", LineType.OUTPUT),
            TerminalLine("│   grep <p> <f>   search file for pattern  │", LineType.OUTPUT),
            TerminalLine("│   find <path>    find files               │", LineType.OUTPUT),
            TerminalLine("│                                           │", LineType.SYSTEM),
            TerminalLine("│  Network / Hacking                        │", LineType.SYSTEM),
            TerminalLine("│   ssh <user@host> connect to server       │", LineType.OUTPUT),
            TerminalLine("│   nmap <target>   scan network            │", LineType.OUTPUT),
            TerminalLine("│   ping <host>     test connectivity       │", LineType.OUTPUT),
            TerminalLine("│   netstat         show connections        │", LineType.OUTPUT),
            TerminalLine("│   ps [-aux]       list processes          │", LineType.OUTPUT),
            TerminalLine("│   kill <pid>      terminate process       │", LineType.OUTPUT),
            TerminalLine("│   crack <file>    break encryption        │", LineType.OUTPUT),
            TerminalLine("│   upload <file>   exfiltrate data         │", LineType.OUTPUT),
            TerminalLine("│                                           │", LineType.SYSTEM),
            TerminalLine("│  Misc                                     │", LineType.SYSTEM),
            TerminalLine("│   whoami          show current user       │", LineType.OUTPUT),
            TerminalLine("│   clear           clear terminal          │", LineType.OUTPUT),
            TerminalLine("│   hint            show current hint (-pts)│", LineType.OUTPUT),
            TerminalLine("└───────────────────────────────────────────┘", LineType.SYSTEM)
        )
    }
}
