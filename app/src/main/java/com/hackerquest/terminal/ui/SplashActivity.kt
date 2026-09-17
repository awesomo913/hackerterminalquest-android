package com.hackerquest.terminal.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hackerquest.terminal.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    private val lines = listOf(
        "INITIALIZING SHADOWNET UPLINK...",
        "",
        "  ██╗  ██╗ █████╗  ██████╗██╗  ██╗███████╗██████╗ ",
        "  ██║  ██║██╔══██╗██╔════╝██║ ██╔╝██╔════╝██╔══██╗",
        "  ███████║███████║██║     █████╔╝ █████╗  ██████╔╝",
        "  ██╔══██║██╔══██║██║     ██╔═██╗ ██╔══╝  ██╔══██╗",
        "  ██║  ██║██║  ██║╚██████╗██║  ██╗███████╗██║  ██║",
        "  ╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝",
        "",
        "  TERMINAL QUEST",
        "",
        "─────────────────────────────────────────────────────",
        "",
        "[NET] Encryption verified    ✓",
        "[NET] Identity masked        ✓",
        "[NET] Signal routed: 7 hops  ✓",
        "",
        "─────────────────────────────────────────────────────",
        "",
        "  5 levels of corporate hacking.",
        "  A story about power, data, and accountability.",
        "  Real terminal commands. No cheats.",
        "",
        "─────────────────────────────────────────────────────"
    )

    private var currentLine = 0
    private var currentChar = 0
    private val handler = Handler(Looper.getMainLooper())
    private var fullText = StringBuilder()
    private var typewriterDone = false

    private val typewriterRunnable = object : Runnable {
        override fun run() {
            if (currentLine >= lines.size) {
                finishTypewriter()
                return
            }
            val line = lines[currentLine]
            if (currentChar < line.length) {
                fullText.append(line[currentChar])
                binding.tvSplash.text = fullText.toString()
                currentChar++
                handler.postDelayed(this, CHAR_DELAY_MS)
            } else {
                fullText.append('\n')
                binding.tvSplash.text = fullText.toString()
                currentLine++
                currentChar = 0
                handler.postDelayed(this, LINE_DELAY_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvPressEnter.visibility = View.INVISIBLE
        handler.post(typewriterRunnable)

        binding.btnStart.setOnClickListener { launchGame() }
        binding.root.setOnClickListener {
            if (!typewriterDone) skipToEnd() else launchGame()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (!typewriterDone) skipToEnd() else launchGame()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun finishTypewriter() {
        typewriterDone = true
        binding.tvPressEnter.visibility = View.VISIBLE
        blinkPressEnter()
    }

    private fun skipToEnd() {
        handler.removeCallbacks(typewriterRunnable)
        fullText.clear()
        lines.forEach { fullText.append(it).append('\n') }
        binding.tvSplash.text = fullText.toString()
        finishTypewriter()
    }

    private fun blinkPressEnter() {
        var visible = true
        val blink = object : Runnable {
            override fun run() {
                if (!typewriterDone) return
                binding.tvPressEnter.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                visible = !visible
                handler.postDelayed(this, 600)
            }
        }
        handler.post(blink)
    }

    private fun launchGame() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val CHAR_DELAY_MS = 12L
        private const val LINE_DELAY_MS = 40L
    }
}
