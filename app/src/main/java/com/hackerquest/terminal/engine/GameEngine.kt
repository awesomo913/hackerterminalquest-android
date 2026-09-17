package com.hackerquest.terminal.engine

import com.hackerquest.terminal.data.GameState
import com.hackerquest.terminal.data.Level
import com.hackerquest.terminal.data.LevelRepository
import com.hackerquest.terminal.data.LineType
import com.hackerquest.terminal.data.TerminalLine

data class EngineOutput(
    val lines: List<TerminalLine>,
    val newState: GameState,
    val newCwd: String,
    val clearScreen: Boolean = false
)

class GameEngine {

    private val parser = CommandParser()

    fun startLevel(levelId: Int): EngineOutput {
        val level = LevelRepository.getLevel(levelId)
            ?: return gameOver(0, levelId - 1)
        val state = GameState.Playing(
            levelId = levelId,
            objectiveIndex = 0,
            score = 0,
            levelStartMs = System.currentTimeMillis(),
            hintsUsed = 0
        )
        val lines = buildIntroLines(level)
        return EngineOutput(lines, state, defaultCwd(level))
    }

    fun processCommand(
        input: String,
        state: GameState.Playing,
        cwd: String,
        accumulatedScore: Int
    ): EngineOutput {
        val level = LevelRepository.getLevel(state.levelId)
            ?: return gameOver(accumulatedScore, state.levelId)

        val currentObjective = level.objectives.getOrNull(state.objectiveIndex)
        val result = parser.process(input, level, currentObjective, cwd)

        if (result.clearScreen) {
            return EngineOutput(emptyList(), state, cwd, clearScreen = true)
        }

        if (result.showHint) {
            val hint = level.hints.getOrElse(state.hintsUsed) { "No more hints available." }
            val hintLines = listOf(
                TerminalLine("", LineType.OUTPUT),
                TerminalLine("[HINT] $hint", LineType.WARNING),
                TerminalLine("[HINT] -100 pts penalty applied.", LineType.WARNING),
                TerminalLine("", LineType.OUTPUT)
            )
            val newState = state.copy(
                score = maxOf(0, state.score - 100),
                hintsUsed = state.hintsUsed + 1
            )
            return EngineOutput(hintLines, newState, result.newCwd ?: cwd)
        }

        val newCwd = result.newCwd ?: cwd
        var newObjectiveIndex = state.objectiveIndex
        var newState: GameState = state

        if (result.advanceObjective && currentObjective != null) {
            newObjectiveIndex = state.objectiveIndex + 1
            val isLastObjective = currentObjective.isWin ||
                newObjectiveIndex >= level.objectives.size

            newState = if (isLastObjective) {
                val levelScore = calculateLevelScore(level, state)
                val total = accumulatedScore + levelScore
                GameState.LevelComplete(levelId = state.levelId, levelScore = levelScore, totalScore = total)
            } else {
                state.copy(objectiveIndex = newObjectiveIndex)
            }
        }

        return EngineOutput(result.lines, newState, newCwd)
    }

    fun getHint(state: GameState.Playing): String {
        val level = LevelRepository.getLevel(state.levelId) ?: return "No hints available."
        return level.hints.getOrElse(state.hintsUsed) { "No more hints available." }
    }

    fun levelCompleteLines(levelId: Int, levelScore: Int, totalScore: Int): List<TerminalLine> {
        val isLast = levelId == LevelRepository.LEVELS.size
        val lines = mutableListOf(
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("╔════════════════════════════════╗", LineType.SUCCESS),
            TerminalLine("║   ★  LEVEL COMPLETE  ★         ║", LineType.SUCCESS),
            TerminalLine("╠════════════════════════════════╣", LineType.SUCCESS),
            TerminalLine("║  Level score:  ${levelScore.toString().padEnd(14)}   ║", LineType.SUCCESS),
            TerminalLine("║  Total score:  ${totalScore.toString().padEnd(14)}   ║", LineType.SUCCESS),
            TerminalLine("╚════════════════════════════════╝", LineType.SUCCESS),
            TerminalLine("", LineType.OUTPUT)
        )
        if (!isLast) {
            lines.add(TerminalLine("[SYS] Loading next mission...", LineType.SYSTEM))
        }
        return lines
    }

    fun victoryLines(totalScore: Int): List<TerminalLine> {
        return listOf(
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("╔══════════════════════════════════════╗", LineType.SUCCESS),
            TerminalLine("║                                      ║", LineType.SUCCESS),
            TerminalLine("║   ██╗   ██╗ ██████╗ ██╗   ██╗       ║", LineType.SUCCESS),
            TerminalLine("║    ╚██╗██╔╝██╔═══██╗██║   ██║       ║", LineType.SUCCESS),
            TerminalLine("║     ╚███╔╝ ██║   ██║██║   ██║       ║", LineType.SUCCESS),
            TerminalLine("║     ██╔██╗ ██║   ██║██║   ██║       ║", LineType.SUCCESS),
            TerminalLine("║    ██╔╝ ██╗╚██████╔╝╚██████╔╝       ║", LineType.SUCCESS),
            TerminalLine("║    ╚═╝  ╚═╝ ╚═════╝  ╚═════╝        ║", LineType.SUCCESS),
            TerminalLine("║                                      ║", LineType.SUCCESS),
            TerminalLine("║   OPERATION SHADOWNET COMPLETE       ║", LineType.SUCCESS),
            TerminalLine("╠══════════════════════════════════════╣", LineType.SUCCESS),
            TerminalLine("║   FINAL SCORE:  ${totalScore.toString().padEnd(20)}  ║", LineType.SUCCESS),
            TerminalLine("╚══════════════════════════════════════╝", LineType.SUCCESS),
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("[NET] Evidence received by:", LineType.STORY),
            TerminalLine("[NET]  → The Guardian", LineType.STORY),
            TerminalLine("[NET]  → Electronic Frontier Foundation", LineType.STORY),
            TerminalLine("[NET]  → International Human Rights Watch", LineType.STORY),
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("[NET] MegaCorp stock halted. CEO arrested.", LineType.STORY),
            TerminalLine("[NET] 12.8 million people will know their data was stolen.", LineType.STORY),
            TerminalLine("", LineType.OUTPUT),
            TerminalLine("Thanks for playing HACKER TERMINAL QUEST.", LineType.SYSTEM)
        )
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun calculateLevelScore(level: Level, state: GameState.Playing): Int {
        val elapsedMs = System.currentTimeMillis() - state.levelStartMs
        val timeBonus = when {
            elapsedMs < 60_000L  -> 500
            elapsedMs < 180_000L -> 200
            elapsedMs < 300_000L -> 100
            else                 -> 0
        }
        val hintPenalty = state.hintsUsed * 100
        return maxOf(0, level.scoreBase + timeBonus - hintPenalty)
    }

    private fun gameOver(score: Int, levelsComplete: Int): EngineOutput {
        return EngineOutput(
            lines = listOf(TerminalLine("[SYS] Critical error. Session terminated.", LineType.ERROR)),
            newState = GameState.Victory(score),
            newCwd = "/"
        )
    }

    private fun defaultCwd(level: Level) = "/home/${level.username}"

    private fun buildIntroLines(level: Level): List<TerminalLine> {
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("", LineType.OUTPUT))
        lines.add(TerminalLine("─── ${level.title} ───", LineType.SYSTEM))
        lines.add(TerminalLine("", LineType.OUTPUT))
        level.intro.forEach { line ->
            val type = when {
                line.startsWith("[ALERT]")   -> LineType.WARNING
                line.startsWith("[SSH]")     -> LineType.SYSTEM
                line.startsWith("[NET]")     -> LineType.SYSTEM
                line.startsWith("[SYS]")     -> LineType.SYSTEM
                line.startsWith("OBJECTIVE") -> LineType.SUCCESS
                line.startsWith("BRIEFING")  -> LineType.SUCCESS
                line.startsWith("FINAL")     -> LineType.SUCCESS
                line.startsWith("WARNING")   -> LineType.WARNING
                line.startsWith("╔") || line.startsWith("║") || line.startsWith("╚") -> LineType.SUCCESS
                else -> LineType.STORY
            }
            lines.add(TerminalLine(line, type))
        }
        lines.add(TerminalLine("", LineType.OUTPUT))
        lines.add(TerminalLine("OBJECTIVE: ${LevelRepository.getLevel(level.id)?.objective}", LineType.SYSTEM))
        lines.add(TerminalLine("", LineType.OUTPUT))
        return lines
    }
}
