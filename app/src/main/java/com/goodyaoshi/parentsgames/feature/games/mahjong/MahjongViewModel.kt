package com.goodyaoshi.parentsgames.feature.games.mahjong

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.mahjong.domain.MahjongBoardLayout
import com.goodyaoshi.parentsgames.feature.games.mahjong.domain.MahjongEngine
import com.goodyaoshi.parentsgames.feature.games.mahjong.domain.MahjongLayoutBuilder
import com.goodyaoshi.parentsgames.feature.games.mahjong.domain.MahjongSnapshot
import com.goodyaoshi.parentsgames.feature.games.mahjong.domain.MahjongTile
import com.goodyaoshi.parentsgames.core.feedback.HapticController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class MahjongUiState(
    val tiles: List<MahjongTile> = emptyList(),
    val selectedId: Int? = null,
    val hintPair: Pair<Int, Int>? = null,
    val remaining: Int = 0,
    val elapsedMs: Long = 0,
    val completed: Boolean = false,
    val noMoves: Boolean = false,
    /** 死局洗牌确认弹窗（无解自动检测或点提示时触发；玩家可关闭，但盘面仍死时点提示会再弹）。 */
    val showReshuffleDialog: Boolean = false,
    val layout: MahjongBoardLayout = MahjongBoardLayout.SMALL,
)

@HiltViewModel
class MahjongViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
    private val haptic: HapticController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MahjongUiState())
    val uiState: StateFlow<MahjongUiState> = _uiState.asStateFlow()

    /** 玩家关闭死局弹窗后暂不重复打扰；盘面恢复有解（洗牌/撤销）时自动复位。 */
    private var deadAlertDismissed = false

    private var engine: MahjongEngine = MahjongEngine(MahjongLayoutBuilder.build())
    private var currentLayout: MahjongBoardLayout = MahjongBoardLayout.SMALL
    private var startTime = System.currentTimeMillis()
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null

    init {
        loadOrFresh()
    }

    private fun loadOrFresh() {
        viewModelScope.launch {
            val raw = stateRepo.loadSnapshot(GameType.MAHJONG)
            var loaded: MahjongEngine? = null
            if (raw != null) {
                runCatching {
                    val snap = json.decodeFromString<MahjongSnapshot>(raw)
                    // 按牌数判断快照布局；牌数与任何已知布局不符（旧档损坏/版本变更）则丢弃开新局
                    val snapLayout = MahjongBoardLayout.entries.firstOrNull { it.tileCount == snap.tiles.size }
                    if (snapLayout != null) {
                        currentLayout = snapLayout
                        startTime = System.currentTimeMillis() - snap.elapsedMs
                        loaded = MahjongEngine(snap.tiles)
                    } else {
                        stateRepo.clearSnapshot(GameType.MAHJONG)
                    }
                }
            }
            if (loaded == null) {
                currentLayout = MahjongBoardLayout.SMALL
                loaded = MahjongEngine(MahjongLayoutBuilder.build(currentLayout))
            }
            engine = loaded!!
            publish()
        }
    }

    fun onTileTap(id: Int) {
        val tile = engine.snapshot().firstOrNull { it.id == id && !it.removed } ?: return
        if (!engine.isFree(tile)) return
        // 提示高亮保持到玩家点击任意可动棋子为止，避免老人还没看清就消失
        val selected = _uiState.value.selectedId
        if (selected == null) {
            _uiState.value = _uiState.value.copy(selectedId = id, hintPair = null)
            return
        }
        if (selected == id) {
            _uiState.value = _uiState.value.copy(selectedId = null, hintPair = null)
            return
        }
        val ok = engine.tryMatch(selected, id)
        if (ok) {
            haptic.snap()
            _uiState.value = _uiState.value.copy(selectedId = null, hintPair = null)
            publish()
            scheduleSave()
            if (engine.isComplete()) {
                haptic.win()
                finishGame()
            }
        } else {
            haptic.error()
            _uiState.value = _uiState.value.copy(selectedId = id, hintPair = null)
        }
    }

    fun onUndo() { engine.undo(); publish(); scheduleSave() }

    /**
     * 提示按钮一律有响应：
     *  - 有可配对 → 高亮一对（现有逻辑）；
     *  - 无可配对 → 走死局流程，弹「要不要洗个牌」确认弹窗。
     */
    fun onHint() {
        val pair = engine.useHint()
        if (pair == null) {
            haptic.error()
            deadAlertDismissed = false
            publish()
            return
        }
        _uiState.value = _uiState.value.copy(hintPair = pair)
    }

    fun onReshuffle() { engine.reshuffle(); deadAlertDismissed = false; publish(); scheduleSave() }

    /** 死局弹窗确认：洗牌（引擎保证洗完至少一对可配）。 */
    fun onReshuffleConfirmed() {
        engine.reshuffle()
        deadAlertDismissed = false
        publish()
        scheduleSave()
    }

    /** 死局弹窗关闭：暂不再弹，盘面仍死时点「提示」会再次触发。 */
    fun onReshuffleDialogDismissed() {
        deadAlertDismissed = true
        publish()
    }

    /** 切换盘面规格：即开新局（同规格点击不重开，避免老人误触丢局）。 */
    fun onLayoutSelected(layout: MahjongBoardLayout) {
        if (layout == currentLayout) return
        currentLayout = layout
        startNewGame()
    }

    fun onRestart() {
        startNewGame()
    }

    private fun startNewGame() {
        engine = MahjongEngine(MahjongLayoutBuilder.build(currentLayout))
        startTime = System.currentTimeMillis()
        _uiState.value = MahjongUiState(layout = currentLayout)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.MAHJONG) }
        publish()
    }

    private fun publish() {
        val dead = !engine.isComplete() && !engine.hasMoves()
        if (!dead) deadAlertDismissed = false
        _uiState.value = _uiState.value.copy(
            tiles = engine.snapshot(),
            remaining = engine.remainingCount(),
            completed = engine.isComplete(),
            elapsedMs = System.currentTimeMillis() - startTime,
            noMoves = dead,
            showReshuffleDialog = dead && !deadAlertDismissed,
            layout = currentLayout,
        )
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000) // debounce
            val snap = MahjongSnapshot(
                tiles = engine.snapshot(),
                hintsUsed = engine.hintsUsed,
                undosUsed = engine.undosUsed,
                reshufflesUsed = engine.reshufflesUsed,
                elapsedMs = System.currentTimeMillis() - startTime,
            )
            stateRepo.saveSnapshot(GameType.MAHJONG, json.encodeToString(snap))
        }
    }

    private fun finishGame() {
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.MAHJONG,
                    score = computeScore(),
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = if (currentLayout == MahjongBoardLayout.SMALL) "SMALL_72" else "TURTLE_MVP",
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.MAHJONG)
        }
    }

    private fun computeScore(): Long {
        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000
        val base = 10_000L
        val timePenalty = maxOf(0L, (elapsedSec - 600L)) * 5L
        val hintPenalty = engine.hintsUsed * 50L
        val undoPenalty = engine.undosUsed * 10L
        val reshufflePenalty = engine.reshufflesUsed * 100L
        return (base - timePenalty - hintPenalty - undoPenalty - reshufflePenalty).coerceAtLeast(0)
    }
}
