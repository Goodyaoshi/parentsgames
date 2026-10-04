package com.goodyaoshi.parentsgames.feature.games.colorsort

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.colorsort.domain.ColorSortEngine
import com.goodyaoshi.parentsgames.feature.games.colorsort.domain.ColorSortStages
import com.goodyaoshi.parentsgames.feature.games.colorsort.domain.LiquidColor
import com.goodyaoshi.parentsgames.feature.games.colorsort.domain.ColorSortSnapshot
import com.goodyaoshi.parentsgames.feature.games.colorsort.domain.Tube
import com.goodyaoshi.parentsgames.core.feedback.HapticController
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PourAnimation(
    val from: Int,
    val to: Int,
    val color: LiquidColor,
    val unitsCount: Int,
    val phase: PourPhase,
)
enum class PourPhase { TILT, POURING, RETURN }

data class ColorSortUiState(
    val tubes: List<Tube> = emptyList(),
    val selectedTube: Int? = null,
    val stage: Int = 1,
    val moves: Int = 0,
    val elapsedMs: Long = 0,
    val completed: Boolean = false,
    val pour: PourAnimation? = null,
    val errorTube: Int? = null,
    val inputLocked: Boolean = false,
)

@HiltViewModel
class ColorSortViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
    private val haptic: HapticController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var engine = ColorSortEngine(ColorSortStages.stage(1))
    private var stage = 1
    private val startTime = System.currentTimeMillis()
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null

    private val _uiState = MutableStateFlow(ColorSortUiState())
    val uiState: StateFlow<ColorSortUiState> = _uiState.asStateFlow()

    init { publish() }

    fun initialize(stage: Int) {
        if (this.stage != stage) {
            this.stage = stage
            // Tenta carregar snapshot da fase atual
            viewModelScope.launch {
                val raw = stateRepo.loadSnapshot(GameType.COLOR_SORT)
                val loaded = raw?.let {
                    runCatching { json.decodeFromString<ColorSortSnapshot>(it) }.getOrNull()
                }
                engine = if (loaded != null && loaded.stage == stage) {
                    ColorSortEngine(ColorSortStages.stage(stage)).apply { loadFromSnapshot(loaded) }
                } else {
                    ColorSortEngine(ColorSortStages.stage(stage))
                }
                publish()
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            stateRepo.saveSnapshot(GameType.COLOR_SORT, json.encodeToString(engine.toSnapshot(stage)))
        }
    }

    fun onTubeTap(idx: Int) {
        if (_uiState.value.inputLocked) return
        val selected = _uiState.value.selectedTube
        if (selected == null) {
            // Não pode selecionar tubo vazio como source
            val tube = _uiState.value.tubes.getOrNull(idx) ?: return
            if (tube.isEmpty) {
                showError(idx)
                return
            }
            _uiState.value = _uiState.value.copy(selectedTube = idx)
            return
        }
        if (selected == idx) {
            _uiState.value = _uiState.value.copy(selectedTube = null)
            return
        }
        if (!engine.canTransfer(selected, idx)) {
            showError(idx)
            return
        }
        // Anima a transferência
        viewModelScope.launch {
            performPour(selected, idx)
        }
    }

    private suspend fun performPour(from: Int, to: Int) {
        val sourceTube = _uiState.value.tubes[from]
        val destTube = _uiState.value.tubes[to]
        val color = sourceTube.top ?: return
        val moveCount = minOf(sourceTube.topRunSize, destTube.freeSpace)

        // Fase 1: tubo source tilta
        _uiState.value = _uiState.value.copy(
            inputLocked = true,
            selectedTube = null,
            pour = PourAnimation(from, to, color, moveCount, PourPhase.TILT),
        )
        delay(180)

        // Fase 2: derrame
        _uiState.value = _uiState.value.copy(
            pour = PourAnimation(from, to, color, moveCount, PourPhase.POURING),
        )
        // Atualiza engine no meio (visualmente o líquido descendo na origem + subindo no destino)
        engine.transfer(from, to)
        publish()
        delay(280)

        // Fase 3: tubo source retorna à posição
        _uiState.value = _uiState.value.copy(
            pour = PourAnimation(from, to, color, moveCount, PourPhase.RETURN),
        )
        delay(160)

        _uiState.value = _uiState.value.copy(pour = null, inputLocked = false)
        haptic.tick()
        scheduleSave()
        if (engine.isWin()) {
            haptic.win()
            finishStage()
        }
    }

    private fun showError(idx: Int) {
        haptic.error()
        _uiState.value = _uiState.value.copy(errorTube = idx)
        viewModelScope.launch {
            delay(280)
            _uiState.value = _uiState.value.copy(errorTube = null)
        }
    }

    fun onUndo() {
        if (_uiState.value.inputLocked) return
        engine.undo()
        scheduleSave()
        publish()
    }

    fun onAddTube() {
        if (_uiState.value.inputLocked) return
        engine.addEmptyTube()
        scheduleSave()
        publish()
    }

    fun onRestart() {
        engine = ColorSortEngine(ColorSortStages.stage(stage))
        _uiState.value = ColorSortUiState(stage = stage)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.COLOR_SORT) }
        publish()
    }

    private fun finishStage() {
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.COLOR_SORT,
                    score = null,
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = "STAGE_$stage",
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.COLOR_SORT)
        }
    }

    private fun publish() {
        _uiState.value = _uiState.value.copy(
            tubes = engine.snapshot(),
            stage = stage,
            moves = engine.movesMade,
            completed = engine.isWin(),
            elapsedMs = System.currentTimeMillis() - startTime,
        )
    }
}
