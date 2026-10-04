package com.goodyaoshi.parentsgames.feature.games.klotski

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiEngine
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiLayout
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiPiece
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiSnapshot
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiSolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

data class KlotskiUiState(
    val layout: KlotskiLayout = KlotskiLayout.GUO_WU_GUAN,
    val pieces: List<KlotskiPiece> = emptyList(),
    val selectedPieceId: Int? = null,
    val moves: Int = 0,
    val finished: Boolean = false,
    val elapsedMs: Long = 0,
    /** 提示：把哪块滑到哪（null 表示当前没有提示）。 */
    val hint: KlotskiSolver.Hint? = null,
    /** 提示计算中（按钮显示“…”并防连点）。 */
    val hintLoading: Boolean = false,
    /** 无解提示（理论上经典布局不会出现）。 */
    val hintFailed: Boolean = false,
)

@HiltViewModel
class KlotskiViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
) : ViewModel() {

    private var engine = KlotskiEngine(KlotskiLayout.GUO_WU_GUAN)
    private var startTime = System.currentTimeMillis()
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null
    private var hintJob: Job? = null

    private val _uiState = MutableStateFlow(KlotskiUiState())
    val uiState: StateFlow<KlotskiUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = stateRepo.loadSnapshot(GameType.KLOTSKI)
            if (raw != null) {
                runCatching {
                    val snap = json.decodeFromString<KlotskiSnapshot>(raw)
                    val layout = KlotskiLayout.entries.firstOrNull { it.id == snap.layoutId }
                    if (layout == null) {
                        stateRepo.clearSnapshot(GameType.KLOTSKI)
                    } else {
                        // 布局对不上或快照损坏（版本调整过棋盘）时丢弃，开新局
                        engine = KlotskiEngine(layout)
                        if (!engine.loadFromSnapshot(snap)) {
                            engine = KlotskiEngine(layout)
                            stateRepo.clearSnapshot(GameType.KLOTSKI)
                        }
                    }
                }
            }
            startTime = System.currentTimeMillis()
            publish()
        }
        // 每秒刷新一次计时，让顶栏的时间走起来
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!engine.finished) {
                    _uiState.value = _uiState.value.copy(elapsedMs = System.currentTimeMillis() - startTime)
                }
            }
        }
    }

    private fun scheduleSave() {
        if (engine.finished) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            stateRepo.saveSnapshot(GameType.KLOTSKI, json.encodeToString(engine.toSnapshot()))
        }
    }

    fun onLayoutSelected(layout: KlotskiLayout) {
        if (engine.layout == layout) return // 同一个开局不重置，防止误触
        clearHint()
        engine = KlotskiEngine(layout)
        startTime = System.currentTimeMillis()
        _uiState.value = KlotskiUiState(layout = layout)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.KLOTSKI) }
        publish()
    }

    fun onRestart() {
        clearHint()
        engine = KlotskiEngine(engine.layout)
        startTime = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(selectedPieceId = null)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.KLOTSKI) }
        publish()
    }

    fun onPieceTap(pieceId: Int) {
        if (engine.finished) return
        engine.selectPiece(pieceId)
        publish()
    }

    fun onCellTap(x: Int, y: Int) {
        if (engine.finished) return
        var tx = x
        var ty = y
        val hint = _uiState.value.hint
        if (hint != null && engine.selectedId == hint.pieceId) {
            // 选中提示的块时，把落在提示目标区内的点击映射到目标区沿滑动方向
            // 最远的一格：引擎按“盖住所点格的最短滑动”走子，这样一次点击
            // 就能完整走出提示的那一步（而不是只滑一半）。
            val piece = engine.pieceById(hint.pieceId)
            if (piece != null &&
                x >= hint.toX && x < hint.toX + piece.type.w &&
                y >= hint.toY && y < hint.toY + piece.type.h
            ) {
                val dx = hint.toX - piece.x
                val dy = hint.toY - piece.y
                if (dx > 0) tx = hint.toX + piece.type.w - 1
                if (dx < 0) tx = hint.toX
                if (dy > 0) ty = hint.toY + piece.type.h - 1
                if (dy < 0) ty = hint.toY
            }
        }
        val changed = engine.tapCell(tx, ty)
        if (!changed) return
        clearHint() // 局面变了，旧提示不再适用（完成提示那步后也会自动清除）
        publish()
        if (engine.finished) finishGame()
    }

    fun onUndo() {
        if (engine.finished) return
        if (engine.undo()) {
            clearHint()
            publish()
            scheduleSave()
        }
    }

    /**
     * 提示：后台线程 BFS 求解当前局面，取最优解的第一步。
     * 计算期间 hintLoading 为 true（按钮显示“…”并防连点）。
     */
    fun onHint() {
        if (engine.finished || _uiState.value.hintLoading) return
        hintJob?.cancel()
        _uiState.value = _uiState.value.copy(hint = null, hintFailed = false, hintLoading = true)
        val pieces = engine.pieceList
        hintJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { KlotskiSolver.firstStep(pieces) }
            // 计算期间若重开/换局/走子导致局面变化，丢弃这次结果
            if (engine.pieceList != pieces || engine.finished) return@launch
            _uiState.value = _uiState.value.copy(
                hint = result,
                hintFailed = result == null,
                hintLoading = false,
            )
        }
    }

    /** 无解提示展示过后复位标记，保证下次触发还能再提示。 */
    fun onHintFailureShown() {
        if (_uiState.value.hintFailed) {
            _uiState.value = _uiState.value.copy(hintFailed = false)
        }
    }

    private fun clearHint() {
        hintJob?.cancel()
        val s = _uiState.value
        if (s.hint != null || s.hintLoading || s.hintFailed) {
            _uiState.value = s.copy(hint = null, hintLoading = false, hintFailed = false)
        }
    }

    private fun publish() {
        _uiState.value = _uiState.value.copy(
            layout = engine.layout,
            pieces = engine.pieceList,
            selectedPieceId = engine.selectedId,
            moves = engine.moves,
            finished = engine.finished,
            elapsedMs = System.currentTimeMillis() - startTime,
        )
    }

    private fun finishGame() {
        saveJob?.cancel()
        val end = System.currentTimeMillis()
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.KLOTSKI,
                    score = null,
                    durationMs = end - startTime,
                    difficulty = engine.layout.label,
                    finishedAt = end,
                )
            )
            stateRepo.clearSnapshot(GameType.KLOTSKI)
        }
    }
}
