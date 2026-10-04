package com.goodyaoshi.parentsgames.feature.games.sudoku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.sudoku.domain.Difficulty
import com.goodyaoshi.parentsgames.feature.games.sudoku.domain.GameStatus
import com.goodyaoshi.parentsgames.feature.games.sudoku.domain.SudokuCell
import com.goodyaoshi.parentsgames.feature.games.sudoku.domain.SudokuEngine
import com.goodyaoshi.parentsgames.feature.games.sudoku.domain.SudokuSnapshot
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

data class SudokuUiState(
    val board: List<List<SudokuCell>> = emptyList(),
    val rows: Int = Difficulty.EASY.rows,
    val cols: Int = Difficulty.EASY.cols,
    val digitMax: Int = Difficulty.EASY.digitMax,
    val selected: Pair<Int, Int>? = null,
    val difficulty: Difficulty = Difficulty.EASY,
    val status: GameStatus = GameStatus.Playing,
    val elapsedMs: Long = 0,
    /** 演示模式：电脑自动填数给老人看，任何点击退出并恢复原局面；不写战绩不改存档。 */
    val demoMode: Boolean = false,
    /** 演示提示条/结束提示文案（null = 不显示）。 */
    val demoNotice: String? = null,
    /** 演示刚填入的格（金色光圈高亮，随填格步进移动）。 */
    val demoLastCell: Pair<Int, Int>? = null,
)

@HiltViewModel
class SudokuViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
) : ViewModel() {

    private var engine = SudokuEngine(Difficulty.EASY)
    private var startTime = System.currentTimeMillis()
    private var baseElapsedMs = 0L
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null
    private var ticker: Job? = null
    private var demoJob: Job? = null
    private var demoSnapshot: SudokuSnapshot? = null

    private val _uiState = MutableStateFlow(SudokuUiState())
    val uiState: StateFlow<SudokuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = stateRepo.loadSnapshot(GameType.SUDOKU)
            if (raw != null) {
                runCatching {
                    val snap = json.decodeFromString<SudokuSnapshot>(raw)
                    val diff = runCatching { Difficulty.valueOf(snap.difficulty) }.getOrNull() ?: Difficulty.EASY
                    engine = SudokuEngine(diff)
                    if (engine.loadFromSnapshot(snap)) {
                        baseElapsedMs = snap.elapsedMs
                        startTime = System.currentTimeMillis()
                    } else {
                        engine = SudokuEngine(diff)
                        baseElapsedMs = 0
                        startTime = System.currentTimeMillis()
                    }
                }
            }
            publish()
            startTicker()
        }
    }

    private fun currentElapsed(): Long = baseElapsedMs + (System.currentTimeMillis() - startTime)

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (engine.status is GameStatus.Playing) {
                delay(1000)
                _uiState.value = _uiState.value.copy(elapsedMs = currentElapsed())
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            stateRepo.saveSnapshot(GameType.SUDOKU, json.encodeToString(engine.toSnapshot(currentElapsed())))
        }
    }

    private fun resetTimer() {
        baseElapsedMs = 0
        startTime = System.currentTimeMillis()
    }

    fun setDifficulty(d: Difficulty) {
        if (_uiState.value.demoMode) exitDemo()
        saveJob?.cancel() // 防止迟到的旧局保存盖掉刚清空的存档
        engine = SudokuEngine(d)
        resetTimer()
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.SUDOKU) }
        publish()
        startTicker()
    }

    fun onRestart() {
        if (_uiState.value.demoMode) exitDemo()
        saveJob?.cancel() // 防止迟到的旧局保存盖掉刚清空的存档
        engine = SudokuEngine(engine.difficulty)
        resetTimer()
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.SUDOKU) }
        publish()
        startTicker()
    }

    fun onCellTap(r: Int, c: Int) {
        if (_uiState.value.demoMode) { exitDemo(); return }
        if (engine.status !is GameStatus.Playing) return
        engine.select(r, c)
        publish()
    }

    fun onNumberTap(n: Int) {
        if (_uiState.value.demoMode) { exitDemo(); return }
        if (engine.status !is GameStatus.Playing) return
        val sel = engine.selected ?: return
        val changed = engine.setNumber(sel.first, sel.second, n)
        if (!changed) return
        scheduleSave()
        publish()
        if (engine.status is GameStatus.Won) finishGame()
    }

    fun onErase() {
        if (_uiState.value.demoMode) { exitDemo(); return }
        if (engine.status !is GameStatus.Playing) return
        val sel = engine.selected ?: return
        if (!engine.erase(sel.first, sel.second)) return
        scheduleSave()
        publish()
    }

    fun hint() {
        if (_uiState.value.demoMode) { exitDemo(); return }
        if (engine.status !is GameStatus.Playing) return
        if (engine.hint() == null) return
        scheduleSave()
        publish()
        if (engine.status is GameStatus.Won) finishGame()
    }

    private fun publish() {
        // 演示期间不把胜负暴露给界面（演示结束会恢复原局面，弹窗照旧）
        val status = if (_uiState.value.demoMode) GameStatus.Playing else engine.status
        _uiState.value = _uiState.value.copy(
            board = engine.snapshotBoard(),
            rows = engine.rows,
            cols = engine.cols,
            digitMax = engine.digitMax,
            selected = engine.selected,
            difficulty = engine.difficulty,
            status = status,
            elapsedMs = currentElapsed(),
        )
    }

    private fun finishGame() {
        val duration = currentElapsed()
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.SUDOKU,
                    score = null,
                    durationMs = duration,
                    difficulty = engine.difficulty.label,
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.SUDOKU)
        }
    }

    // ----- 演示模式 -----

    /**
     * 进入演示：先快照当前局，电脑每约 1.3 秒按求解顺序填一格给老人看。
     * 求解前先验证当前局面仍有解，无解则不进演示、提示后回到自己的棋局；不写战绩、不改存档。
     */
    fun startDemo() {
        if (_uiState.value.demoMode || engine.status !is GameStatus.Playing) return
        val steps = engine.solveStepsFromCurrent()
        if (steps == null) {
            // 局面解不通（多半有填错的数字）：不进演示，提示后照常自己玩
            showDemoNotice("现在的摆法解不出来啦，改一改数字再试试！")
            return
        }
        if (steps.isEmpty()) {
            showDemoNotice("这局已经填完啦，重新开始一局吧！")
            return
        }
        saveJob?.cancel() // 演示期间绝不写存档（连带取消未落盘的旧保存）
        demoSnapshot = engine.toSnapshot(currentElapsed())
        _uiState.value = _uiState.value.copy(demoMode = true, demoNotice = null, demoLastCell = null)
        publish()
        demoJob = viewModelScope.launch {
            for (step in steps) {
                delay(1300)
                if (!_uiState.value.demoMode) return@launch
                engine.demoFill(step.row, step.col, step.value)
                _uiState.value = _uiState.value.copy(demoLastCell = step.row to step.col)
                publish()
            }
            if (_uiState.value.demoMode) endDemoAuto() // 填满最后一格，演示自然结束
        }
    }

    /** 退出演示（用户任何点击）：恢复进入演示前的局面，时间照常走。 */
    fun exitDemo() {
        if (!_uiState.value.demoMode) return
        demoJob?.cancel()
        restoreDemoSnapshot()
        _uiState.value = _uiState.value.copy(demoMode = false, demoNotice = null, demoLastCell = null)
        publish()
        scheduleSave()
        startTicker()
    }

    /** 演示自然结束（填满最后一格）：恢复原局面，提示老人自己上手（不判胜负、不写战绩）。 */
    private fun endDemoAuto() {
        demoJob?.cancel()
        restoreDemoSnapshot()
        _uiState.value = _uiState.value.copy(demoMode = false, demoLastCell = null)
        publish()
        scheduleSave()
        startTicker()
        showDemoNotice("看完了吧？您来试试！")
    }

    /** 显示一条演示提示，4.5 秒后自动消失（期间若再进演示则不误清）。 */
    private fun showDemoNotice(text: String) {
        _uiState.value = _uiState.value.copy(demoNotice = text)
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
