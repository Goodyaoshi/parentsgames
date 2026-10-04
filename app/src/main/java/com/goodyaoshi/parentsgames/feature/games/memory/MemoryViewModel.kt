package com.goodyaoshi.parentsgames.feature.games.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.memory.domain.Difficulty
import com.goodyaoshi.parentsgames.feature.games.memory.domain.GameStatus
import com.goodyaoshi.parentsgames.feature.games.memory.domain.MemoryCard
import com.goodyaoshi.parentsgames.feature.games.memory.domain.MemoryEngine
import com.goodyaoshi.parentsgames.feature.games.memory.domain.MemorySnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class MemoryUiState(
    val cards: List<MemoryCard> = emptyList(),
    val rows: Int = 3,
    val cols: Int = 4,
    val matchedPairs: Int = 0,
    val totalPairs: Int = 6,
    val moves: Int = 0,
    val difficulty: Difficulty = Difficulty.EASY,
    val status: GameStatus = GameStatus.Playing,
    val elapsedMs: Long = 0,
    val inputLocked: Boolean = false,
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
) : ViewModel() {

    private var engine = MemoryEngine(Difficulty.EASY)
    private var startTime = System.currentTimeMillis()
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null
    private var flipBackJob: Job? = null

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = stateRepo.loadSnapshot(GameType.MEMORY)
            if (raw != null) {
                runCatching {
                    val snap = json.decodeFromString<MemorySnapshot>(raw)
                    val diff = runCatching { Difficulty.valueOf(snap.difficulty) }.getOrNull() ?: Difficulty.EASY
                    // 旧快照牌数与当前难度不符（版本调整过棋盘尺寸）时丢弃，开新局
                    val sizeMatch = snap.cards.size == diff.pairs * 2
                    if (sizeMatch) {
                        engine = MemoryEngine(diff)
                        engine.loadFromSnapshot(snap)
                    } else {
                        stateRepo.clearSnapshot(GameType.MEMORY)
                    }
                }
            }
            publish()
        }
        // 每秒刷新计时
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (engine.status is GameStatus.Playing) {
                    _uiState.value = _uiState.value.copy(elapsedMs = System.currentTimeMillis() - startTime)
                }
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            stateRepo.saveSnapshot(GameType.MEMORY, json.encodeToString(engine.toSnapshot()))
        }
    }

    fun setDifficulty(d: Difficulty) {
        flipBackJob?.cancel()
        engine = MemoryEngine(d)
        startTime = System.currentTimeMillis()
        _uiState.value = MemoryUiState(difficulty = d)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.MEMORY) }
        publish()
    }

    fun onRestart() {
        flipBackJob?.cancel()
        engine = MemoryEngine(engine.difficulty)
        startTime = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(inputLocked = false)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.MEMORY) }
        publish()
    }

    fun onCardTap(r: Int, c: Int) {
        if (engine.status !is GameStatus.Playing) return
        if (_uiState.value.inputLocked) return
        if (!engine.tap(r, c)) return
        scheduleSave()
        publish()
        if (engine.isMismatchPending) {
            // 两张不匹配：停留约 900ms 让老人看清，再盖回，期间禁点
            _uiState.value = _uiState.value.copy(inputLocked = true)
            flipBackJob?.cancel()
            flipBackJob = viewModelScope.launch {
                delay(900)
                engine.resolveMismatch()
                publish()
            }
        }
        if (engine.status is GameStatus.Won) finishGame()
    }

    private fun publish() {
        _uiState.value = _uiState.value.copy(
            cards = engine.snapshot(),
            rows = engine.rows,
            cols = engine.cols,
            matchedPairs = engine.matchedPairs,
            totalPairs = engine.totalPairs,
            moves = engine.moves,
            difficulty = engine.difficulty,
            status = engine.status,
            elapsedMs = System.currentTimeMillis() - startTime,
            inputLocked = false,
        )
    }

    private fun finishGame() {
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.MEMORY,
                    score = null,
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = engine.difficulty.label,
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.MEMORY)
        }
    }
}
