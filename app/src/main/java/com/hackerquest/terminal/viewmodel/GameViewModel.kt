package com.hackerquest.terminal.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hackerquest.terminal.data.GameState
import com.hackerquest.terminal.data.LineType
import com.hackerquest.terminal.data.TerminalLine
import com.hackerquest.terminal.engine.GameEngine

class GameViewModel : ViewModel() {

    private val engine = GameEngine()

    private val _lines = MutableLiveData<List<TerminalLine>>(emptyList())
    val lines: LiveData<List<TerminalLine>> = _lines

    private val _gameState = MutableLiveData<GameState>(GameState.Idle)
    val gameState: LiveData<GameState> = _gameState

    private val _prompt = MutableLiveData<String>("cipher@gateway:~$ ")
    val prompt: LiveData<String> = _prompt

    private val _totalScore = MutableLiveData(0)
    val totalScore: LiveData<Int> = _totalScore

    private var cwd = "/home/cipher"
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1
    private var pendingLevelId = -1

    fun startGame() {
        loadLevel(1)
    }

    fun submitCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) {
            commandHistory.add(0, trimmed)
            historyIndex = -1
        }

        // Echo the input line
        val currentState = _gameState.value
        val hostname = when (val s = currentState) {
            is GameState.Playing -> promptFor(s)
            else -> "cipher@terminal:~$ "
        }
        appendLines(listOf(TerminalLine("$hostname$trimmed", LineType.INPUT)))

        if (currentState is GameState.Playing) {
            val result = engine.processCommand(
                input = trimmed,
                state = currentState,
                cwd = cwd,
                accumulatedScore = _totalScore.value ?: 0
            )

            cwd = result.newCwd

            if (result.clearScreen) {
                _lines.value = emptyList()
            } else {
                appendLines(result.lines)
            }

            when (val newState = result.newState) {
                is GameState.Playing -> {
                    _gameState.value = newState
                    updatePrompt(newState)
                }
                is GameState.LevelComplete -> {
                    _gameState.value = newState
                    _totalScore.value = newState.totalScore
                    val completionLines = engine.levelCompleteLines(
                        newState.levelId, newState.levelScore, newState.totalScore
                    )
                    appendLines(completionLines)
                    pendingLevelId = newState.levelId + 1
                    if (newState.levelId < 5) {
                        // Short delay handled by observing state in Activity
                    } else {
                        appendLines(engine.victoryLines(newState.totalScore))
                        _gameState.value = GameState.Victory(newState.totalScore)
                    }
                }
                is GameState.Victory -> {
                    _gameState.value = newState
                    appendLines(engine.victoryLines(newState.totalScore))
                }
                else -> {}
            }
        }
    }

    fun advanceToNextLevel() {
        if (pendingLevelId > 0) {
            loadLevel(pendingLevelId)
            pendingLevelId = -1
        }
    }

    fun historyUp(): String? {
        if (commandHistory.isEmpty()) return null
        historyIndex = minOf(historyIndex + 1, commandHistory.size - 1)
        return commandHistory[historyIndex]
    }

    fun historyDown(): String? {
        if (historyIndex <= 0) {
            historyIndex = -1
            return ""
        }
        historyIndex--
        return commandHistory[historyIndex]
    }

    private fun loadLevel(levelId: Int) {
        val output = engine.startLevel(levelId)
        cwd = output.newCwd
        _gameState.value = output.newState
        appendLines(output.lines)
        if (output.newState is GameState.Playing) {
            updatePrompt(output.newState as GameState.Playing)
        }
    }

    private fun appendLines(newLines: List<TerminalLine>) {
        val current = _lines.value ?: emptyList()
        _lines.value = current + newLines
    }

    private fun promptFor(state: GameState.Playing): String {
        val levelHostname = when (state.levelId) {
            1 -> "gateway"
            2 -> "archive"
            3 -> "archive"
            4 -> "vault"
            5 -> "vault"
            else -> "unknown"
        }
        val dir = cwd.replace("/home/${getUserForLevel(state.levelId)}", "~")
        return "${getUserForLevel(state.levelId)}@$levelHostname:$dir\$ "
    }

    private fun updatePrompt(state: GameState.Playing) {
        _prompt.value = promptFor(state)
    }

    private fun getUserForLevel(levelId: Int) = when (levelId) {
        1 -> "cipher"
        2, 3 -> "archive_admin"
        4, 5 -> "vault_root"
        else -> "cipher"
    }
}
