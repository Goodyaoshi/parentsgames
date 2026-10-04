package com.goodyaoshi.parentsgames.feature.games.xiangqi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.BLACK
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.Move
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.XiangqiAi
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.XiangqiDifficulty
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.XiangqiEngine
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.XiangqiSnapshot
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.XiangqiStatus
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

/** 对局模式。 */
enum class XiangqiMode(val label: String) {
    AI_MODE("人机对战"),
    TWO_PLAYER("双人对战"),
}

data class XiangqiUiState(
    val board: List<List<Int>> = emptyList(),
    val selected: Pair<Int, Int>? = null,
    val legalTargets: List<Move> = emptyList(),
    val lastMove: Move? = null,
    val hintMove: Move? = null,
    val mode: XiangqiMode = XiangqiMode.AI_MODE,
    val difficulty: XiangqiDifficulty = XiangqiDifficulty.EASY,
    val status: XiangqiStatus = XiangqiStatus.RED_TURN,
    val isThinking: Boolean = false,
    val inCheck: Boolean = false,
    val elapsedMs: Long = 0,
)

@HiltViewModel
class XiangqiViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
) : ViewModel() {

    private var engine = XiangqiEngine()
    private var mode = XiangqiMode.AI_MODE
    private var difficulty = XiangqiDifficulty.EASY
    private var startTime = System.currentTimeMillis()
    private var finished = false
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null
    private var aiJob: Job? = null

    private val _uiState = MutableStateFlow(XiangqiUiState())
    val uiState: StateFlow<XiangqiUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = stateRepo.loadSnapshot(GameType.XIANGQI)
            if (raw != null) {
                runCatching {
                    val snap = json.decodeFromString<XiangqiSnapshot>(raw)
                    mode = runCatching { XiangqiMode.valueOf(snap.mode) }.getOrDefault(XiangqiMode.AI_MODE)
                    difficulty = snap.difficulty
                        ?.let { runCatching { XiangqiDifficulty.valueOf(it) }.getOrNull() }
                        ?: XiangqiDifficulty.EASY
                    engine = XiangqiEngine()
                    engine.loadFromSnapshot(snap)
                    startTime = System.currentTimeMillis() - snap.elapsedMs
                }
            }
            publish()
            // 恢复对局时若轮到黑方（人机模式），让 AI 接着走
            if (mode == XiangqiMode.AI_MODE && engine.turn == BLACK && isPlaying()) triggerAi()
            // 每秒刷新一次用时
            while (true) {
                delay(1000)
                if (isPlaying()) {
                    _uiState.value = _uiState.value.copy(elapsedMs = System.currentTimeMillis() - startTime)
                }
            }
        }
    }

    fun setMode(m: XiangqiMode) {
        if (m == mode && isPlaying()) return
        startNewGame(m)
    }

    /** 切换人机难度：与扫雷一致，直接开新一局（双人模式下不启用难度）。 */
    fun setDifficulty(d: XiangqiDifficulty) {
        if (d == difficulty) return
        difficulty = d
        startNewGame(mode)
    }

    fun onRestart() = startNewGame(mode)

    fun onCellTap(r: Int, c: Int) {
        val s = _uiState.value
        if (!isPlaying() || s.isThinking) return
        if (mode == XiangqiMode.AI_MODE && engine.turn == BLACK) return
        val target = s.legalTargets.firstOrNull { it.toR == r && it.toC == c }
        if (s.selected != null && target != null) {
            engine.makeMove(target)
            _uiState.value = _uiState.value.copy(selected = null, legalTargets = emptyList(), hintMove = null)
            publish()
            if (isPlaying()) {
                scheduleSave()
                if (mode == XiangqiMode.AI_MODE && engine.turn == BLACK) triggerAi()
            } else {
                finishGame()
            }
            return
        }
        val p = engine.pieceAt(r, c)
        if (p != 0 && (p > 0) == (engine.turn > 0)) {
            val targets = engine.generateMoves().filter { it.fromR == r && it.fromC == c }
            _uiState.value = _uiState.value.copy(selected = r to c, legalTargets = targets, hintMove = null)
        } else {
            _uiState.value = _uiState.value.copy(selected = null, legalTargets = emptyList())
        }
    }

    /** 人机模式回退两步（对方一步 + 自己一步），双人模式回退一步。 */
    fun undo() {
        if (!isPlaying() || _uiState.value.isThinking) return
        if (engine.moveHistory.isEmpty()) return
        engine.undoMove()
        if (mode == XiangqiMode.AI_MODE && engine.turn == BLACK && engine.moveHistory.isNotEmpty()) {
            engine.undoMove()
        }
        _uiState.value = _uiState.value.copy(selected = null, legalTargets = emptyList(), hintMove = null)
        publish()
        scheduleSave()
    }

    /** 让 AI 替当前行动方算一步，用于高亮提示。 */
    fun hint() {
        if (!isPlaying() || _uiState.value.isThinking) return
        if (mode == XiangqiMode.AI_MODE && engine.turn == BLACK) return
        val board = engine.copyBoard()
        val side = engine.turn
        viewModelScope.launch {
            val move = withContext(Dispatchers.Default) { XiangqiAi.findBestMove(board, side, XiangqiAi.HARD_DEPTH) }
            if (move != null && engine.turn == side && isPlaying()) {
                _uiState.value = _uiState.value.copy(hintMove = move)
            }
        }
    }

    private fun startNewGame(m: XiangqiMode) {
        aiJob?.cancel()
        saveJob?.cancel()
        engine = XiangqiEngine()
        mode = m
        startTime = System.currentTimeMillis()
        finished = false
        _uiState.value = XiangqiUiState(mode = m, difficulty = difficulty)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.XIANGQI) }
        publish()
    }

    private fun triggerAi() {
        _uiState.value = _uiState.value.copy(isThinking = true)
        val board = engine.copyBoard()
        val side = engine.turn
        aiJob = viewModelScope.launch {
            // AI 思考放在后台线程，避免困难档搜索卡 UI
            val move = withContext(Dispatchers.Default) { XiangqiAi.findMove(board, side, difficulty) }
            // 计算期间若被重开 / 换模式，engine 已换成新实例，此时不再落子
            if (move != null && engine.turn == side && isPlaying()) {
                engine.makeMove(move)
                publish()
                if (isPlaying()) scheduleSave() else finishGame()
            }
            _uiState.value = _uiState.value.copy(isThinking = false)
        }
    }

    private fun isPlaying(): Boolean =
        engine.status == XiangqiStatus.RED_TURN || engine.status == XiangqiStatus.BLACK_TURN

    private fun scheduleSave() {
        if (!isPlaying()) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            stateRepo.saveSnapshot(
                GameType.XIANGQI,
                json.encodeToString(
                    engine.toSnapshot(mode.name, System.currentTimeMillis() - startTime, difficulty.name),
                ),
            )
        }
    }

    private fun publish() {
        _uiState.value = _uiState.value.copy(
            board = engine.boardView(),
            status = engine.status,
            lastMove = engine.moveHistory.lastOrNull(),
            inCheck = isPlaying() && engine.isInCheck(engine.turn),
            mode = mode,
            difficulty = difficulty,
            elapsedMs = System.currentTimeMillis() - startTime,
        )
    }

    /** 终局（将死 / 困毙）时存一次战绩并清掉快照。 */
    private fun finishGame() {
        if (finished) return
        finished = true
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.XIANGQI,
                    score = null,
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = mode.label,
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.XIANGQI)
        }
    }
}
