package com.goodyaoshi.parentsgames.feature.games.minesweeper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.Cell
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.Difficulty
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.GameStatus
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.HintKind
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.HintMove
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.MinesweeperEngine
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.MinesweeperSnapshot
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MinesweeperUiState(
    val grid: List<List<Cell>> = emptyList(),
    val rows: Int = 6,
    val cols: Int = 6,
    val flagsRemaining: Int = 5,
    val flagMode: Boolean = false,
    val difficulty: Difficulty = Difficulty.EASY,
    val status: GameStatus = GameStatus.Playing,
    val elapsedMs: Long = 0,
    /** 「提示」高亮的目标格（null = 无）；hintSeq 每次提示自增，用于重启 1 秒熄灭计时。 */
    val hintTarget: Pair<Int, Int>? = null,
    val hintSeq: Int = 0,
    /** 演示模式：电脑自动走棋给老人看，任何点击退出并恢复原局面；不写战绩不改存档。 */
    val demoMode: Boolean = false,
    /** 演示提示条/结束提示文案（null = 不显示）。 */
    val demoNotice: String? = null,
)

@HiltViewModel
class MinesweeperViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
) : ViewModel() {

    private var engine = MinesweeperEngine(Difficulty.EASY)
    private var startTime = System.currentTimeMillis()
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null
    private var demoJob: Job? = null
    private var demoSnapshot: MinesweeperSnapshot? = null

    private val _uiState = MutableStateFlow(MinesweeperUiState())
    val uiState: StateFlow<MinesweeperUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = stateRepo.loadSnapshot(GameType.MINESWEEPER)
            if (raw != null) {
                runCatching {
                    val snap = json.decodeFromString<MinesweeperSnapshot>(raw)
                    val diff = runCatching { Difficulty.valueOf(snap.difficulty) }.getOrNull() ?: Difficulty.EASY
                    // 旧快照行列数与当前难度不符（版本调整过棋盘尺寸）时丢弃，开新局
                    val dimsMatch = snap.grid.size == diff.rows && snap.grid.all { it.size == diff.cols }
                    if (dimsMatch) {
                        engine = MinesweeperEngine(diff)
                        engine.loadFromSnapshot(snap)
                    } else {
                        stateRepo.clearSnapshot(GameType.MINESWEEPER)
                    }
                }
            }
            publish()
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            stateRepo.saveSnapshot(GameType.MINESWEEPER, json.encodeToString(engine.toSnapshot()))
        }
    }

    fun setDifficulty(d: Difficulty) {
        if (_uiState.value.demoMode) exitDemo()
        engine = MinesweeperEngine(d)
        startTime = System.currentTimeMillis()
        _uiState.value = MinesweeperUiState(difficulty = d)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.MINESWEEPER) }
        publish()
    }

    fun onRestart() {
        if (_uiState.value.demoMode) exitDemo()
        engine = MinesweeperEngine(engine.difficulty)
        startTime = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(flagMode = false, hintTarget = null, demoNotice = null)
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.MINESWEEPER) }
        publish()
    }

    fun toggleFlagMode() {
        _uiState.value = _uiState.value.copy(flagMode = !_uiState.value.flagMode)
    }

    fun onCellTap(r: Int, c: Int) {
        if (_uiState.value.demoMode) { exitDemo(); return }
        if (engine.status !is GameStatus.Playing) return
        if (_uiState.value.flagMode) engine.toggleFlag(r, c)
        else engine.tap(r, c)
        scheduleSave()
        publish()
        if (engine.status is GameStatus.Won) finishGame()
    }

    fun onCellLongPress(r: Int, c: Int) {
        if (_uiState.value.demoMode) { exitDemo(); return }
        if (engine.status !is GameStatus.Playing) return
        engine.toggleFlag(r, c)
        scheduleSave()
        publish()
    }

    private fun publish() {
        // 演示期间不把胜负暴露给界面（演示结束会恢复原局面，弹窗照旧）
        val status = if (_uiState.value.demoMode) GameStatus.Playing else engine.status
        _uiState.value = _uiState.value.copy(
            grid = engine.snapshot(),
            rows = engine.rows,
            cols = engine.cols,
            flagsRemaining = engine.flagsRemaining,
            difficulty = engine.difficulty,
            status = status,
            elapsedMs = System.currentTimeMillis() - startTime,
        )
    }

    private fun finishGame() {
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.MINESWEEPER,
                    score = null,
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = engine.difficulty.label,
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.MINESWEEPER)
        }
    }

    // ----- 提示 -----

    /**
     * 「提示」：按优先级帮老人走一步安全的棋（可推断的雷→插旗；可推断的安全格→翻开；
     * 都推不出来→兜底开一个真非雷格）。这就是真实的一步：照常计胜负、照常存档。
     */
    fun onHint() {
        if (_uiState.value.demoMode) return
        if (engine.status !is GameStatus.Playing) return
        val move = engine.findHint() ?: return
        val applied = when (move.kind) {
            HintKind.FLAG -> engine.toggleFlag(move.row, move.col)
            HintKind.REVEAL -> engine.tap(move.row, move.col)
        }
        if (!applied) return
        scheduleSave()
        publish()
        _uiState.value = _uiState.value.copy(
            hintTarget = move.row to move.col,
            hintSeq = _uiState.value.hintSeq + 1,
        )
        if (engine.status is GameStatus.Won) finishGame()
    }

    fun clearHint() {
        if (_uiState.value.hintTarget == null) return
        _uiState.value = _uiState.value.copy(hintTarget = null)
    }

    // ----- 演示模式 -----

    /** 进入演示：先快照当前局，电脑每约 1.3 秒走一步给老人看；不写战绩、不改存档。 */
    fun startDemo() {
        if (_uiState.value.demoMode || engine.status !is GameStatus.Playing) return
        demoSnapshot = engine.toSnapshot()
        _uiState.value = _uiState.value.copy(demoMode = true, demoNotice = null, hintTarget = null)
        publish()
        demoJob = viewModelScope.launch {
            while (isActive && _uiState.value.demoMode && engine.status is GameStatus.Playing) {
                delay(1300)
                if (!_uiState.value.demoMode) return@launch
                val stepped = demoStep()
                publish()
                if (!stepped) {
                    endDemoAuto()
                    return@launch
                }
            }
            if (_uiState.value.demoMode) endDemoAuto() // 全部安全格翻完，演示自然结束
        }
    }

    /** 退出演示（用户任何点击）：恢复进入演示前的局面。 */
    fun exitDemo() {
        if (!_uiState.value.demoMode) return
        demoJob?.cancel()
        restoreDemoSnapshot()
        _uiState.value = _uiState.value.copy(demoMode = false, demoNotice = null, hintTarget = null)
        publish()
    }

    /** 演示走一步：推断插旗 → 推断开安全格 → 兜底开非雷格；返回 false 表示无可走的一步。 */
    private fun demoStep(): Boolean {
        val move = engine.findHint(verifyTruth = true) ?: return false
        return when (move.kind) {
            HintKind.FLAG -> engine.toggleFlag(move.row, move.col)
            HintKind.REVEAL -> engine.tap(move.row, move.col)
        }
    }

    /** 演示自然结束：恢复原局面，提示老人自己上手（不判胜负、不写战绩）。 */
    private fun endDemoAuto() {
        demoJob?.cancel()
        restoreDemoSnapshot()
        _uiState.value = _uiState.value.copy(
            demoMode = false,
            demoNotice = "看完了吧？您来试试！",
            hintTarget = null,
        )
        publish()
        viewModelScope.launch {
            delay(4500)
            if (!_uiState.value.demoMode) {
                _uiState.value = _uiState.value.copy(demoNotice = null)
            }
        }
    }

    private fun restoreDemoSnapshot() {
        demoSnapshot?.let { engine.loadFromSnapshot(it) }
        demoSnapshot = null
    }
}
