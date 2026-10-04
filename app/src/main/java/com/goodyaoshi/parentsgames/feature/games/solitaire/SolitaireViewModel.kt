package com.goodyaoshi.parentsgames.feature.games.solitaire

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Card
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Pile
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Rank
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Selection
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.SolitaireEngine
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.SolitaireSnapshot
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Suit
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class SolitaireUiState(
    val tableau: List<List<Card>> = emptyList(),
    val foundations: List<List<Card>> = emptyList(),
    val stockCount: Int = 0,
    val waste: List<Card> = emptyList(),
    val selection: Selection? = null,
    /** 当前选中牌的所有合法去处，用于淡色提示框（空 = 无选中或无去处）。 */
    val hintTargets: Set<Pile> = emptySet(),
    val moves: Int = 0,
    val elapsedMs: Long = 0,
    val completed: Boolean = false,
    /** 死局：翻库循环里也翻不出可打的牌、牌列与基础堆都无路可走，弹窗引导重洗或重开。 */
    val stuck: Boolean = false,
    /** 亮光提示开关（默认开）：亮着的牌 = 当前有合法去处，老人不用记规则。 */
    val glowEnabled: Boolean = true,
    /** 每一列里"当前有合法去处"的明牌下标集合，驱动金色微光。 */
    val tableauGlow: List<Set<Int>> = emptyList(),
    /** 弃牌堆顶是否有合法去处（有则弃牌堆顶发光）。 */
    val wasteGlow: Boolean = false,
    /** 演示模式：电脑自动走棋给老人看，任何点击退出并恢复原局面；不写战绩不改存档。 */
    val demoMode: Boolean = false,
    /** 演示提示条/结束提示文案（null = 不显示）。 */
    val demoNotice: String? = null,
)

/** 演示单步结果：走了一步 / 真死局（需换一局或收尾）。 */
private enum class DemoOutcome { MOVED, REDEAL }

/** 演示候选走法：来源、起始下标、去处、首张牌，以及产出标记（能翻背面新牌 / 搬空原列）。 */
private data class DemoCandidate(
    val from: Pile,
    val fromIndex: Int,
    val to: Pile,
    val leading: Card,
    val flips: Boolean,
    val empties: Boolean,
)

@HiltViewModel
class SolitaireViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
) : ViewModel() {

    private var engine = SolitaireEngine()
    private var startTime = System.currentTimeMillis()
    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null
    private var demoJob: Job? = null
    private var demoSnapshot: SolitaireSnapshot? = null
    private var demoDrawStreak = 0
    private var demoLastWasDraw = false
    private var demoRedeals = 0 // 演示中连续换局次数（最多 5，仍未赢就正常退出）
    // 演示防抖记忆：禁回头路 + 近 6 步禁重复 + 同牌 3 次无产出兜底（防"两个 Q 之间来回搬 J"）
    private var demoLastCardKey: Pair<Suit, Rank>? = null
    private var demoLastFrom: Pile? = null
    private val demoRecentMoves = ArrayDeque<Pair<Pair<Suit, Rank>, Pile>>() // 最近 6 步 (牌, 目标堆)
    private val demoMoveCounts = mutableMapOf<Pair<Suit, Rank>, Int>()       // 距上次产出各牌被移次数

    private val _uiState = MutableStateFlow(SolitaireUiState())
    val uiState: StateFlow<SolitaireUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = stateRepo.loadSnapshot(GameType.SOLITAIRE)
            if (raw != null) {
                runCatching {
                    val snap = json.decodeFromString<SolitaireSnapshot>(raw)
                    engine.loadFromSnapshot(snap)
                }
            }
            publish()
        }
        // 修复：计时此前只在操作时刷新，顶栏时钟一直是冻结的，界面看起来像没反应
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (!_uiState.value.completed) {
                    _uiState.value = _uiState.value.copy(elapsedMs = System.currentTimeMillis() - startTime)
                }
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            stateRepo.saveSnapshot(GameType.SOLITAIRE, json.encodeToString(engine.toSnapshot()))
        }
    }

    fun onDrawClick() {
        engine.drawFromStock()
        clearSelection()
        publish()
    }

    fun onTableauCardTap(col: Int, indexInPile: Int) {
        val pile = Pile.Tableau(col)
        handleTapOnSource(pile, indexInPile)
    }

    fun onWasteTap() {
        handleTapOnSource(Pile.Waste, 0)
    }

    fun onFoundationTap(idx: Int) {
        handleTapOnSource(Pile.Foundation(idx), 0)
    }

    fun onEmptyTableauTap(col: Int) {
        val sel = _uiState.value.selection ?: return
        if (engine.tryMove(sel.pile, sel.fromIndex, Pile.Tableau(col))) {
            clearSelectionAndPublish()
        }
    }

    fun onEmptyFoundationTap(idx: Int) {
        val sel = _uiState.value.selection ?: return
        if (engine.tryMove(sel.pile, sel.fromIndex, Pile.Foundation(idx))) {
            clearSelectionAndPublish()
        }
    }

    private fun handleTapOnSource(pile: Pile, indexInPile: Int) {
        val sel = _uiState.value.selection
        if (sel == null) {
            // 第一次点牌：若只有唯一合法去处，直接自动完成（省一次操作）；否则选中并提示所有去处
            val targets = engine.legalTargets(pile, indexInPile)
            if (targets.size == 1 && engine.tryMove(pile, indexInPile, targets.first())) {
                clearSelectionAndPublish()
                if (engine.isWin()) finishGame()
                return
            }
            _uiState.value = _uiState.value.copy(
                selection = Selection(pile, indexInPile),
                hintTargets = targets.toSet(),
            )
            return
        }
        if (sel.pile == pile && sel.fromIndex == indexInPile) {
            // 再点同一张：取消选中
            clearSelectionAndPublish()
            return
        }
        // 点到合法去处：移动
        if (engine.tryMove(sel.pile, sel.fromIndex, pile)) {
            clearSelectionAndPublish()
            if (engine.isWin()) finishGame()
            return
        }
        // 点到的不是去处：改选新点的牌，并刷新去处提示
        _uiState.value = _uiState.value.copy(
            selection = Selection(pile, indexInPile),
            hintTargets = engine.legalTargets(pile, indexInPile).toSet(),
        )
    }

    /** 拖拽落点：合法则移动并清除选中，非法则忽略（牌视觉上回到原位）。 */
    fun onDragMove(from: Pile, fromIndex: Int, to: Pile) {
        if (engine.tryMove(from, fromIndex, to)) {
            clearSelectionAndPublish()
            if (engine.isWin()) finishGame()
        }
    }

    /** 点空白处：取消当前选中。 */
    fun onBackgroundTap() {
        if (_uiState.value.selection == null) return
        clearSelectionAndPublish()
    }

    fun onAutoToFoundation(pile: Pile) {
        if (engine.autoToFoundation(pile)) {
            clearSelectionAndPublish()
            if (engine.isWin()) finishGame()
        }
    }

    fun onRestart() {
        if (_uiState.value.demoMode) exitDemo()
        engine = SolitaireEngine()
        startTime = System.currentTimeMillis()
        clearSelection()
        viewModelScope.launch { stateRepo.clearSnapshot(GameType.SOLITAIRE) }
        publish()
    }

    /** 死局恢复：收回牌列与弃牌堆重洗入库存堆（基础堆保留），记一步，照常存档。 */
    fun onReshuffle() {
        if (_uiState.value.demoMode) exitDemo()
        engine.reshuffleBoard()
        clearSelection()
        publish()
        scheduleSave()
    }

    // ----- 亮光提示 -----

    /** 亮光 开/关（只影响界面提示，不持久化）。 */
    fun toggleGlow() {
        _uiState.value = _uiState.value.copy(glowEnabled = !_uiState.value.glowEnabled)
    }

    // ----- 演示模式 -----

    /**
     * 进入演示：先快照用户当前局；再在内存里用同一 demoStep 策略无动画模拟**当前这副牌**——
     * 模拟能赢（构造局 + 正常进行中理论必过）就直接从当前局面开始动画演示，轨迹=模拟轨迹必赢；
     * 模拟没赢（极端兜底，比如用户把牌堆得很乱）才走"重新构造一副必赢牌再演"（提示条说明换局）。
     * 不写战绩、不改存档，任何点击退出即恢复原局面。
     */
    fun startDemo() {
        if (_uiState.value.demoMode || _uiState.value.completed) return
        demoSnapshot = engine.toSnapshot()
        clearSelection()
        _uiState.value = _uiState.value.copy(demoMode = true, demoNotice = null)
        publish()
        // 模拟为主线程纯数据操作（构造局常规几十步、几毫秒，带墙钟保护），不碰存档/战绩
        demoResetMemory()
        val currentWins = simulateDeal().first
        if (currentWins) {
            // 模拟会打到终局，先把引擎卷回模拟起点，动画再照同一条必赢轨迹慢慢重演
            demoSnapshot?.let { engine.loadFromSnapshot(it) }
        } else {
            // 兜底：当前局模拟赢不了（如牌被堆得很乱）→ 重新构造一副必赢牌再演
            val chosen = pickVerifiedDeal()
            demoResetMemory()
            if (chosen != null) {
                engine.loadFromSnapshot(chosen)
                _uiState.value = _uiState.value.copy(
                    demoNotice = "这一局有点乱了，电脑换一局给您演示",
                )
            } else {
                // 连兜底都没拿到新牌：恢复原局面照常演示（到收尾自动结束）
                demoSnapshot?.let { engine.loadFromSnapshot(it) }
            }
        }
        demoRedeals = 0
        publish()
        demoJob = viewModelScope.launch {
            while (isActive && _uiState.value.demoMode && !engine.isWin()) {
                delay(1300)
                if (!_uiState.value.demoMode) return@launch
                val outcome = demoStep()
                publish()
                // 真死局（翻库也翻不出、回移也解锁不了）→ 换一局继续演示，最多换 5 局；
                // 纯翻牌周期很长是正常玩法，阈值 150 步且任何产出步都会清零计数
                val needRestart = when (outcome) {
                    DemoOutcome.REDEAL -> true
                    DemoOutcome.MOVED -> {
                        if (demoLastWasDraw) {
                            demoDrawStreak++
                            demoDrawStreak > 150
                        } else {
                            demoDrawStreak = 0
                            false
                        }
                    }
                }
                if (!needRestart) continue
                demoRedeals++
                if (demoRedeals > 5) {
                    endDemoAuto()
                    return@launch
                }
                demoNewDeal()
            }
            if (_uiState.value.demoMode) endDemoAuto() // 走完 / 胜利
        }
    }

    /** 退出演示（用户任何点击）：恢复进入演示前的局面，恢复可玩。 */
    fun exitDemo() {
        if (!_uiState.value.demoMode) return
        demoJob?.cancel()
        restoreDemoSnapshot()
        _uiState.value = _uiState.value.copy(
            demoMode = false,
            selection = null,
            hintTargets = emptySet(),
            demoNotice = null,
        )
        publish()
    }

    /**
     * 演示走一步。返回 MOVED（走了）或 REDEAL（真死局，需换局/收尾）。
     * 选步优先级：①收基础堆（豁免过滤）②能翻背面新牌 ③搬空原列/弃牌顶回牌列 ④普通移动
     * ⑤翻库 → ⑥基础堆回移解锁（最后手段，同样受防抖过滤）→ 都不行才是真死局。
     * ②③④⑥经过防抖过滤（waste 来源整体豁免；其余禁回头路 + 近 6 步同牌同堆禁重复），
     * 既保证弃牌顶能落牌绝不误拦，又杜绝"一张 J 在两个 Q 之间来回抖"。
     */
    private fun demoStep(): DemoOutcome {
        demoLastWasDraw = false
        // ① 收基础堆：永远优先且不受防抖限制（弃牌堆顶、各列顶）
        if (engine.autoToFoundation(Pile.Waste)) {
            demoMoveCounts.clear()
            return DemoOutcome.MOVED
        }
        for (i in 0 until 7) {
            if (engine.autoToFoundation(Pile.Tableau(i))) {
                demoMoveCounts.clear()
                return DemoOutcome.MOVED
            }
        }
        // 收集牌列候选（跳过"整摞从列底搬去空列"的无意义步）
        val candidates = mutableListOf<DemoCandidate>()
        for (i in 0 until 7) {
            val list = engine.tableau[i]
            val firstFaceUp = list.indexOfFirst { it.faceUp }
            if (firstFaceUp == -1) continue
            for (j in firstFaceUp until list.size) {
                for (t in engine.legalTargets(Pile.Tableau(i), j)) {
                    if (j == 0 && t is Pile.Tableau && engine.tableau[t.index].isEmpty()) continue
                    candidates.add(
                        DemoCandidate(
                            from = Pile.Tableau(i),
                            fromIndex = j,
                            to = t,
                            leading = list[j],
                            flips = j > 0 && !list[j - 1].faceUp,
                            empties = j == 0,
                        ),
                    )
                }
            }
        }
        val wasteTop = engine.waste.lastOrNull()
        val wasteCandidates = if (wasteTop == null) {
            emptyList()
        } else {
            engine.legalTargets(Pile.Waste, 0).map {
                DemoCandidate(Pile.Waste, 0, it, wasteTop, flips = false, empties = false)
            }
        }
        // 基础堆回移候选（最后手段）：各基础堆顶牌回牌列
        val pullbacks = mutableListOf<DemoCandidate>()
        for (f in 0 until 4) {
            val top = engine.foundations[f].lastOrNull() ?: continue
            for (t in engine.legalTargets(Pile.Foundation(f), 0)) {
                if (t is Pile.Tableau) {
                    pullbacks.add(DemoCandidate(Pile.Foundation(f), 0, t, top, flips = false, empties = false))
                }
            }
        }
        fun ok(c: DemoCandidate) = demoPassesFilters(c)
        // 依次尝试一层候选；demoExec 因防振荡兜底拒绝时局面未变，可继续试同层下一候选
        fun tryFirst(list: List<DemoCandidate>, pred: (DemoCandidate) -> Boolean): DemoOutcome? {
            for (c in list) {
                if (pred(c) && demoExec(c)) return DemoOutcome.MOVED
            }
            return null
        }
        // ② 能翻出背面新牌的移动（真产出，最优先展示）
        tryFirst(candidates) { it.flips && ok(it) }?.let { return it }
        // ③ 搬完后原列变空 / 弃牌堆顶回牌列
        tryFirst(candidates) { it.empties && ok(it) }?.let { return it }
        tryFirst(wasteCandidates) { ok(it) }?.let { return it }
        // ④ 其他普通移动（两个 Q 二选一属于这层，受回头路/近 6 步限制不会抖）
        tryFirst(candidates) { ok(it) }?.let { return it }
        // ⑤ 翻库兜底（正常的长翻牌周期由外层 streak 上限 150 保护）
        demoLastWasDraw = true
        if (!engine.drawFromStock()) {
            // ⑥ 库存与弃牌都空：基础堆回移解锁（最后手段）
            demoLastWasDraw = false
            tryFirst(pullbacks) { ok(it) }?.let { return it }
            return DemoOutcome.REDEAL
        }
        // 库存还在但整圈翻不出能打的牌（引擎判定死局，含回移模拟）：别让老人干等，直接试回移
        if (engine.isDeadlocked()) {
            demoLastWasDraw = false
            tryFirst(pullbacks) { ok(it) }?.let { return it }
            return DemoOutcome.REDEAL
        }
        return DemoOutcome.MOVED
    }

    /**
     * 防抖过滤。弃牌堆来源的移动整体豁免：waste→牌列/基础堆把牌永久带离翻库循环，
     * 结构上不可能振荡，而这正是演示最该展示的走法，绝不能被记忆规则误拦。
     * 对牌列/基础堆来源仍保留两道防抖：禁回头路（同牌移回上一步的来源堆）+
     * 禁近 6 步"同牌同目标堆"重复（只拦同堆重复，不再按"动过这张牌"全局拦截，
     * 翻库周期让牌面反复出现属正常，换不同目标堆的重排不算抖）。
     */
    private fun demoPassesFilters(c: DemoCandidate): Boolean {
        if (c.from is Pile.Waste) return true
        val key = c.leading.suit to c.leading.rank
        if (demoLastCardKey == key && demoLastFrom == c.to) return false
        if (demoRecentMoves.any { it.first == key && it.second == c.to }) return false
        return true
    }

    /** 执行演示移动并记账。返回 false 表示本候选被拒（局面已复原，调用方可继续试下一候选）。 */
    private fun demoExec(c: DemoCandidate): Boolean {
        val undo = engine.toSnapshot()
        val f0 = engine.foundations.sumOf { it.size }
        val u0 = engine.tableau.sumOf { col -> col.count { it.faceUp } }
        if (!engine.tryMove(c.from, c.fromIndex, c.to)) return false
        val productive = engine.foundations.sumOf { it.size } > f0 ||
            engine.tableau.sumOf { col -> col.count { it.faceUp } } > u0
        val key = c.leading.suit to c.leading.rank
        if (c.from !is Pile.Waste && !productive) {
            val n = (demoMoveCounts[key] ?: 0) + 1
            if (n >= 3) {
                // 振荡兜底（仅对牌列/基础堆来源）：这张牌移了 3 次仍无翻牌/进基础堆产出 → 撤销并拒绝
                engine.loadFromSnapshot(undo)
                return false
            }
            demoMoveCounts[key] = n
        } else if (productive) {
            demoMoveCounts.clear()
        }
        demoLastCardKey = key
        demoLastFrom = c.from
        demoRecentMoves.addLast(key to c.to)
        while (demoRecentMoves.size > 6) demoRecentMoves.removeFirst()
        return true
    }

    /**
     * 中途换局：同样换一副"验证过必赢"的牌局继续演示（模拟筛选不碰存档/战绩），
     * 退出演示时仍恢复进入演示前的用户快照。提示条短暂告知老人"换一局再演示"。
     */
    private fun demoNewDeal() {
        val chosen = pickVerifiedDeal()
        demoResetMemory()
        if (chosen != null) engine.loadFromSnapshot(chosen) else engine = SolitaireEngine()
        val redealMsg = "这一局走不通了，电脑换一局再演示"
        _uiState.value = _uiState.value.copy(demoNotice = redealMsg)
        publish()
        viewModelScope.launch {
            delay(3200)
            if (_uiState.value.demoMode && _uiState.value.demoNotice == redealMsg) {
                _uiState.value = _uiState.value.copy(demoNotice = null)
            }
        }
    }

    /**
     * 兜底选牌（仅当"模拟当前局面"赢不了时调用）：构造式发牌 + 模拟校验，失败再随机筛选，
     * 全败取基础堆收牌最多的一副。全程不碰存档/战绩。
     * 模拟通过临时替换 engine 引用实现隔离：用户真实对局对象全程不动（调用方负责随后加载选定快照）；
     * 1.5 秒墙钟保护防主线程 ANR，超时取当前最优。
     */
    private fun pickVerifiedDeal(): SolitaireSnapshot? {
        val userEngine = engine
        val t0 = System.currentTimeMillis()
        var best: Pair<SolitaireSnapshot, Int>? = null
        try {
            // 主路线：构造式必赢牌局，最多校验两副
            repeat(2) {
                val cand = SolitaireEngine(Random.nextLong())
                val initial = cand.toSnapshot()
                engine = cand
                demoResetMemory()
                val (won, score) = simulateDeal()
                if (won) return initial
                if (score > (best?.second ?: -1)) best = initial to score
                if (System.currentTimeMillis() - t0 > 1500) return best?.first
            }
            // 兜底：退回真随机发牌 + 模拟筛选（最多 200 副）
            repeat(200) {
                val cand = SolitaireEngine(Random.nextLong(), constructed = false)
                val initial = cand.toSnapshot()
                engine = cand
                demoResetMemory()
                val (won, score) = simulateDeal()
                if (won) return initial
                if (score > (best?.second ?: -1)) best = initial to score
                if (System.currentTimeMillis() - t0 > 1500) return best?.first
            }
        } finally {
            engine = userEngine
        }
        return best?.first
    }

    /** 在内存里快速模拟当前 engine 上的一局（与真实动画演示同一策略、同一防抖记忆重置），返回 是否赢 to 最终基础堆牌数。 */
    private fun simulateDeal(): Pair<Boolean, Int> {
        var steps = 0
        var drawStreak = 0
        while (steps < 3000) {
            if (engine.isWin()) return true to engine.foundations.sumOf { it.size }
            when (demoStep()) {
                DemoOutcome.REDEAL -> return false to engine.foundations.sumOf { it.size }
                DemoOutcome.MOVED -> {
                    if (demoLastWasDraw) {
                        drawStreak++
                        if (drawStreak > 150) return false to engine.foundations.sumOf { it.size }
                    } else {
                        drawStreak = 0
                    }
                }
            }
            steps++
        }
        return false to engine.foundations.sumOf { it.size }
    }

    /** 清空演示防抖记忆：模拟换副/中途换局/开始演示/退出恢复都从零开始，保证策略轨迹确定、模拟可复现到动画。 */
    private fun demoResetMemory() {
        demoDrawStreak = 0
        demoLastCardKey = null
        demoLastFrom = null
        demoRecentMoves.clear()
        demoMoveCounts.clear()
    }

    /** 演示结束（胜利 / 连换 5 局仍未赢）：恢复进入演示前的原局面，提示老人自己上手。 */
    private fun endDemoAuto() {
        demoJob?.cancel()
        restoreDemoSnapshot()
        _uiState.value = _uiState.value.copy(
            demoMode = false,
            selection = null,
            hintTargets = emptySet(),
            demoNotice = "看完了吧？您来试试！",
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
        demoRedeals = 0
        demoResetMemory()
    }

    private fun clearSelection() {
        _uiState.value = _uiState.value.copy(selection = null, hintTargets = emptySet())
    }
    private fun clearSelectionAndPublish() {
        _uiState.value = _uiState.value.copy(selection = null, hintTargets = emptySet())
        publish()
        scheduleSave()
    }

    private fun publish() {
        val demo = _uiState.value.demoMode
        // 亮光：所有"当前有合法去处"的明牌（牌列明牌 + 弃牌堆顶），供金色微光提示
        val tableauGlow = MutableList(7) { mutableSetOf<Int>() }
        for (col in 0 until 7) {
            val list = engine.tableau[col]
            val firstFaceUp = list.indexOfFirst { it.faceUp }
            if (firstFaceUp == -1) continue
            for (j in firstFaceUp until list.size) {
                if (engine.legalTargets(Pile.Tableau(col), j).isNotEmpty()) tableauGlow[col].add(j)
            }
        }
        _uiState.value = _uiState.value.copy(
            tableau = engine.tableau.map { it.toList() },
            foundations = engine.foundations.map { it.toList() },
            stockCount = engine.stock.size,
            waste = engine.waste.toList(),
            moves = engine.moves,
            elapsedMs = System.currentTimeMillis() - startTime,
            // 演示期间不弹胜利/死局（演示结束时恢复原局面，一切照旧）
            completed = !demo && engine.isWin(),
            stuck = !demo && !engine.isWin() && engine.isDeadlocked(),
            tableauGlow = tableauGlow,
            wasteGlow = engine.waste.isNotEmpty() && engine.legalTargets(Pile.Waste, 0).isNotEmpty(),
        )
    }

    private fun finishGame() {
        viewModelScope.launch {
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.SOLITAIRE,
                    score = null,
                    durationMs = System.currentTimeMillis() - startTime,
                    difficulty = "KLONDIKE",
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.SOLITAIRE)
        }
    }
}
