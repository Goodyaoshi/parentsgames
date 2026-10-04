package com.goodyaoshi.parentsgames.feature.games.gomoku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuAi
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuDifficulty
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuEngine
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuMode
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuSnapshot
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class GomokuUiState(
    val board: List<Int> = List(GomokuEngine.SIZE * GomokuEngine.SIZE) { GomokuEngine.EMPTY },
    val mode: GomokuMode = GomokuMode.AI_MODE,
    val difficulty: GomokuDifficulty = GomokuDifficulty.EASY,
    val status: GomokuStatus = GomokuStatus.BlackTurn,
    val lastMove: Int? = null,
    val hintMove: Int? = null,
    val isThinking: Boolean = false,
    val elapsedMs: Long = 0,
)

@HiltViewModel
class GomokuViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
) : ViewModel() {

    private var engine = GomokuEngine()
    private var difficulty = GomokuDifficulty.EASY
    private var startTime = System.currentTimeMillis()
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null
    private var aiJob: Job? = null
    private var hintJob: Job? = null
    private var recordSaved = false

    private val _uiState = MutableStateFlow(GomokuUiState())
    val uiState: StateFlow<GomokuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = stateRepo.loadSnapshot(GameType.GOMOKU)
            if (raw != null) {
                runCatching {
                    val snap = json.decodeFromString<GomokuSnapshot>(raw)
                    val mode = runCatching { GomokuMode.valueOf(snap.mode) }.getOrDefault(GomokuMode.AI_MODE)
                    difficulty = snap.difficulty
                        ?.let { runCatching { GomokuDifficulty.valueOf(it) }.getOrNull() }
                        ?: GomokuDifficulty.EASY
                    engine = GomokuEngine(mode)
                    engine.loadFromSnapshot(snap)
                }
            }
            publish()
            // 恢复到轮到白棋（人机模式）时，继续让 AI 落子
            if (engine.mode == GomokuMode.AI_MODE && engine.status == GomokuStatus.WhiteTurn) {
                startAiTurn()
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            stateRepo.saveSnapshot(GameType.GOMOKU, json.encodeToString(engine.toSnapshot(difficulty.name)))
        }
    }

    fun setMode(mode: GomokuMode) {
        if (mode == engine.mode) return
        newGame(mode)
    }

    /** 切换人机难度：与扫雷一致，直接开新一局（双人模式下不启用难度）。 */
    fun setDifficulty(d: GomokuDifficulty) {
        if (d == difficulty) return
        difficulty = d
        newGame(engine.mode)
    }

    fun onRestart() {
        newGame(engine.mode)
    }

    private fun newGame(mode: GomokuMode) {
        aiJob?.cancel()
        hintJob?.cancel()
        engine = GomokuEngine(mode)
        startTime = System.currentTimeMillis()
        recordSaved = false
        _uiState.value = GomokuUiState(mode = mode, difficulty = difficulty)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.GOMOKU) }
        publish()
    }

    fun onCellTap(r: Int, c: Int) {
        val st = _uiState.value
        if (!st.status.isPlaying || st.isThinking) return
        // 人机模式下玩家执黑，只在黑方回合可落子
        if (engine.mode == GomokuMode.AI_MODE && engine.status != GomokuStatus.BlackTurn) return
        if (!engine.place(r, c)) return
        _uiState.value = _uiState.value.copy(hintMove = null)
        scheduleSave()
        publish()
        if (!engine.status.isPlaying) {
            finishGame()
        } else if (engine.mode == GomokuMode.AI_MODE && engine.status == GomokuStatus.WhiteTurn) {
            startAiTurn()
        }
    }

    private fun startAiTurn() {
        _uiState.value = _uiState.value.copy(isThinking = true)
        aiJob = viewModelScope.launch {
            // AI 落子计算放在后台线程，困难档两层推演也不卡 UI
            val move = withContext(Dispatchers.Default) {
                GomokuAi.findMove(engine.board.copyOf(), GomokuEngine.WHITE, difficulty)
            }
            if (engine.status == GomokuStatus.WhiteTurn && move != null && engine.placeAt(move)) {
                scheduleSave()
                publish()
            }
            _uiState.value = _uiState.value.copy(isThinking = false)
            if (!engine.status.isPlaying) finishGame()
        }
    }

    /** 人机模式回退两手（AI + 玩家），双人模式回退一手。 */
    fun undo() {
        val st = _uiState.value
        if (!st.status.isPlaying || st.isThinking) return
        val steps = if (engine.mode == GomokuMode.AI_MODE) 2 else 1
        repeat(steps) { engine.undo() }
        _uiState.value = _uiState.value.copy(hintMove = null)
        scheduleSave()
        publish()
    }

    /** 给当前行动方算一步建议并高亮。 */
    fun hint() {
        val st = _uiState.value
        if (!st.status.isPlaying || st.isThinking) return
        val status = engine.status
        val side = if (status == GomokuStatus.BlackTurn) GomokuEngine.BLACK else GomokuEngine.WHITE
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            val move = withContext(Dispatchers.Default) {
                GomokuAi.findBestMove(engine.board.copyOf(), side)
            }
            // 计算期间若局面已推进（落子/悔棋/AI 回应），过期建议不再高亮
            if (move != null && engine.status == status && engine.board[move] == GomokuEngine.EMPTY) {
                _uiState.value = _uiState.value.copy(hintMove = move)
            }
        }
    }

    private fun publish() {
        _uiState.value = _uiState.value.copy(
            board = engine.board.toList(),
            mode = engine.mode,
            difficulty = difficulty,
            status = engine.status,
            lastMove = engine.lastMove,
            elapsedMs = System.currentTimeMillis() - startTime,
        )
    }

    private fun finishGame() {
        if (recordSaved) return
        recordSaved = true
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.GOMOKU,
                    score = null,
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = if (engine.mode == GomokuMode.AI_MODE) "人机对战" else "双人对战",
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.GOMOKU)
        }
    }
}
