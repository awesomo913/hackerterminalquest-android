package com.hackerquest.terminal.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hackerquest.terminal.data.GameState
import com.hackerquest.terminal.databinding.ActivityMainBinding
import com.hackerquest.terminal.ui.terminal.TerminalAdapter
import com.hackerquest.terminal.viewmodel.GameViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameViewModel by viewModels()
    private val adapter = TerminalAdapter()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupInput()
        observeViewModel()

        if (viewModel.gameState.value == GameState.Idle) {
            viewModel.startGame()
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvTerminal.layoutManager = layoutManager
        binding.rvTerminal.adapter = adapter
        binding.rvTerminal.itemAnimator = null
    }

    private fun setupInput() {
        binding.etInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                submitCommand()
                true
            } else false
        }

        binding.etInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        viewModel.historyUp()?.let { binding.etInput.setText(it) }
                        binding.etInput.setSelection(binding.etInput.text?.length ?: 0)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val text = viewModel.historyDown() ?: ""
                        binding.etInput.setText(text)
                        binding.etInput.setSelection(text.length)
                        true
                    }
                    else -> false
                }
            } else false
        }

        binding.btnSend.setOnClickListener { submitCommand() }
        binding.btnHint.setOnClickListener {
            viewModel.submitCommand("hint")
            binding.etInput.requestFocus()
        }

        // Focus the input field and show keyboard
        binding.etInput.requestFocus()
        binding.root.setOnClickListener {
            binding.etInput.requestFocus()
            val imm = getSystemService(InputMethodManager::class.java)
            imm.showSoftInput(binding.etInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun observeViewModel() {
        viewModel.lines.observe(this) { lines ->
            adapter.submitList(lines.toList()) {
                binding.rvTerminal.scrollToPosition(adapter.itemCount - 1)
            }
        }

        viewModel.prompt.observe(this) { prompt ->
            binding.tvPrompt.text = prompt
        }

        viewModel.totalScore.observe(this) { score ->
            binding.tvScore.text = "Score: $score"
        }

        viewModel.gameState.observe(this) { state ->
            when (state) {
                is GameState.LevelComplete -> {
                    if (state.levelId < 5) {
                        handler.postDelayed({
                            viewModel.advanceToNextLevel()
                        }, LEVEL_TRANSITION_DELAY_MS)
                    }
                    setInputEnabled(state is GameState.Playing)
                }
                is GameState.Playing -> setInputEnabled(true)
                is GameState.Victory -> {
                    setInputEnabled(false)
                    binding.tvPrompt.text = "[END]"
                }
                else -> {}
            }
        }
    }

    private fun submitCommand() {
        val input = binding.etInput.text?.toString() ?: ""
        binding.etInput.text?.clear()
        viewModel.submitCommand(input)
    }

    private fun setInputEnabled(enabled: Boolean) {
        binding.etInput.isEnabled = enabled
        binding.btnSend.isEnabled = enabled
    }

    companion object {
        private const val LEVEL_TRANSITION_DELAY_MS = 2000L
    }
}
