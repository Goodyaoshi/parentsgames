package com.goodyaoshi.parentsgames.feature.games.tetris

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.core.feedback.HapticController
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.tetris.domain.BOARD_COLS
import com.goodyaoshi.parentsgames.feature.games.tetris.domain.BOARD_ROWS
import com.goodyaoshi.parentsgames.feature.games.tetris.domain.FallingPiece
import com.goodyaoshi.parentsgames.feature.games.tetris.domain.Tetromino
import com.goodyaoshi.parentsgames.feature.games.tetris.domain.TetrisEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TetrisUiState(
    val grid: List<List<Int>> = emptyList(),
    val current: FallingPiece? = null,
    val next: Tetromino? = null,
    val score: Long = 0L,
    val level: Int = 1,
    val lines: Int = 0,
    val gameOver: Boolean = false,
    val paused: Boolean = false,
    val clearingRows: List<Int> = emptyList(),
    val lastClearScore: Long = 0L,
    val showFloater: Boolean = false,
)

@HiltViewModel
class TetrisViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val haptic: HapticController,
) : ViewModel() {

    private var engine = TetrisEngine()
    private var loopJob: Job? = null
    private val startTime = System.currentTimeMillis()
    private var finished = false

    private val _uiState = MutableStateFlow(TetrisUiState())
    val uiState: StateFlow<TetrisUiState> = _uiState.asStateFlow()

    init {
        engine.spawnNext()
        publish()
        startLoop()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            while (true) {
                // Tick interno mais granular para checagem de lock grace
                delay(100L)
                if (_uiState.value.paused) continue
                if (engine.gameOver) break

                if (engine.current == null) {
                    engine.spawnNext()
                    publish()
                    continue
                }

                // Decide se é hora de descer
                val now = System.currentTimeMillis()
                val tickMs = engine.tickInterval()
                if (engine.pieceOnGround()) {
                    // Sob lock grace
                    if (engine.maybeLockAfterGrace(graceMs = 500L)) {
                        // FIX X06: animação de line clear
                        if (engine.lastClearedRows.isNotEmpty()) {
                            haptic.snap()
                            val cleared = engine.lastClearedRows
                            val gained = engine.pointsLastLineClear
                            _uiState.value = _uiState.value.copy(
                                clearingRows = cleared,
                                lastClearScore = gained,
                                showFloater = gained > 0,
                            )
                            delay(220)
                            _uiState.value = _uiState.value.copy(
                                clearingRows = emptyList(),
                            )
                            launch {
                                delay(800)
                                _uiState.value = _uiState.value.copy(showFloater = false)
                            }
                        }
                        engine.spawnNext()
                    }
                } else {
                    // Tick normal — desce baseado em interval
                    if (lastTick + tickMs < now) {
                        engine.tick()
                        lastTick = now
                    }
                }
                publish()
                if (engine.gameOver) {
                    haptic.error()
                    saveRecord()
                    break
                }
            }
        }
    }
    private var lastTick = 0L

    fun moveLeft() { if (!_uiState.value.paused) { engine.moveLeft(); publish() } }
    fun moveRight() { if (!_uiState.value.paused) { engine.moveRight(); publish() } }
    fun rotate() {
        if (_uiState.value.paused) return
        if (engine.rotate()) haptic.tick()
        publish()
    }
    fun softDrop() {
        if (_uiState.value.paused) return
        engine.softDrop()
        if (engine.current == null) engine.spawnNext()
        publish()
    }
    fun hardDrop() {
        if (_uiState.value.paused) return
        engine.hardDrop()
        haptic.snap()
        if (engine.lastClearedRows.isNotEmpty()) {
            val cleared = engine.lastClearedRows
            val gained = engine.pointsLastLineClear
            _uiState.value = _uiState.value.copy(
                clearingRows = cleared,
                lastClearScore = gained,
                showFloater = gained > 0,
            )
            viewModelScope.launch {
                delay(220)
                _uiState.value = _uiState.value.copy(clearingRows = emptyList())
                delay(700)
                _uiState.value = _uiState.value.copy(showFloater = false)
            }
        }
        engine.spawnNext()
        publish()
        if (engine.gameOver) saveRecord()
    }

    fun togglePause() {
        _uiState.value = _uiState.value.copy(paused = !_uiState.value.paused)
    }

    fun restart() {
        loopJob?.cancel()
        finished = false
        engine = TetrisEngine()
        engine.spawnNext()
        lastTick = 0L
        _uiState.value = TetrisUiState()
        publish()
        startLoop()
    }

    private fun publish() {
        _uiState.value = _uiState.value.copy(
            grid = engine.board.map { it.toList() },
            current = engine.current,
            next = engine.next,
            score = engine.score,
            level = engine.level,
            lines = engine.lines,
            gameOver = engine.gameOver,
        )
    }

    private fun saveRecord() {
        if (finished) return
        finished = true
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.TETRIS,
                    score = engine.score,
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = "LV${engine.level}",
                    finishedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        loopJob?.cancel()
    }
}
