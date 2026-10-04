package com.goodyaoshi.parentsgames.feature.games.frogger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.frogger.domain.FrogStatus
import com.goodyaoshi.parentsgames.feature.games.frogger.domain.Frog
import com.goodyaoshi.parentsgames.feature.games.frogger.domain.FroggerEngine
import com.goodyaoshi.parentsgames.feature.games.frogger.domain.Lane
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FroggerUiState(
    val frog: Frog = Frog(12, 4),
    val facingDeg: Float = 0f,
    val goalSlots: List<Boolean> = List(5) { false },
    val lanes: List<Lane> = emptyList(),
    val obstaclePositions: Map<Int, List<Pair<Float, Int>>> = emptyMap(),
    val lives: Int = 3,
    val score: Long = 0,
    val status: FrogStatus = FrogStatus.ALIVE,
    val goalsReached: Int = 0,
)

@HiltViewModel
class FroggerViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val haptic: com.goodyaoshi.parentsgames.core.feedback.HapticController,
) : ViewModel() {

    private var engine = FroggerEngine()
    private var loopJob: Job? = null
    private val startTime = System.currentTimeMillis()
    private var finished = false
    private var paused = false

    private val _uiState = MutableStateFlow(FroggerUiState())
    val uiState: StateFlow<FroggerUiState> = _uiState.asStateFlow()

    init {
        publish()
        startLoop()
    }

    fun onLifecyclePause() {
        paused = true
        loopJob?.cancel()
    }

    fun onLifecycleResume() {
        paused = false
        if (engine.status == FrogStatus.ALIVE && !finished) startLoop()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            val dt = 50L
            var prevLives = engine.lives
            while (true) {
                delay(dt)
                if (paused) continue
                engine.update(dt / 1000f)
                if (engine.lives < prevLives) {
                    haptic.error()
                    prevLives = engine.lives
                }
                publish()
                if (engine.status != FrogStatus.ALIVE) {
                    if (engine.status == FrogStatus.WON) haptic.win()
                    else haptic.error()
                    saveRecord()
                    break
                }
            }
        }
    }

    fun moveUp() { engine.moveUp(); haptic.tick(); publish() }
    fun moveDown() { engine.moveDown(); haptic.tick(); publish() }
    fun moveLeft() { engine.moveLeft(); haptic.tick(); publish() }
    fun moveRight() { engine.moveRight(); haptic.tick(); publish() }

    fun restart() {
        loopJob?.cancel()
        finished = false
        engine = FroggerEngine()
        publish()
        startLoop()
    }

    private fun publish() {
        _uiState.value = _uiState.value.copy(
            frog = engine.frog,
            facingDeg = engine.facingDeg,
            goalSlots = engine.goalSlots.toList(),
            lanes = engine.lanes,
            obstaclePositions = engine.obstaclePositions(),
            lives = engine.lives,
            score = engine.score,
            status = engine.status,
            goalsReached = engine.goalsReached,
        )
    }

    private fun saveRecord() {
        if (finished) return
        finished = true
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.FROGGER,
                    score = engine.score,
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = "CLASSIC",
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
