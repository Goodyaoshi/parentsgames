package com.goodyaoshi.parentsgames.feature.games.solitaire

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.PrimaryButton
import com.goodyaoshi.parentsgames.core.ui.SecondaryButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.core.ui.WinOverlay
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Card
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Pile
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Rank
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Selection
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Suit
import com.goodyaoshi.parentsgames.feature.games.solitaire.ui.PlayingCard
import kotlin.math.roundToInt

/** 拖拽中的一次抓取：来源、牌组、尺寸与指针的窗口坐标。 */
private data class DragState(
    val from: Pile,
    val fromIndex: Int,
    val cards: List<Card>,
    val cardW: Dp,
    val cardH: Dp,
    val gap: Dp,
    val grab: Offset,    // 按下点相对被抓牌左上角的偏移
    val current: Offset, // 指针当前的窗口坐标
)

/** 一个牌堆的可落点区域（窗口坐标）。 */
private class Zone(val pile: Pile, val offset: Offset, val size: Size)

/**
 * 登记各牌堆窗口坐标，供拖拽落点判定；持有当前拖拽状态并渲染浮层。
 */
private class DragController {
    var state by mutableStateOf<DragState?>(null)
    var onDrop: ((from: Pile, fromIndex: Int, to: Pile) -> Unit)? = null
    val zones = mutableMapOf<Pile, Zone>()

    fun register(pile: Pile, coords: LayoutCoordinates) {
        zones[pile] = Zone(pile, coords.positionInRoot(), coords.size.toSize())
    }

    fun dropTarget(): Pile? {
        val d = state ?: return null
        return zones.values.firstOrNull { z ->
            z.pile != d.from &&
                d.current.x >= z.offset.x && d.current.x <= z.offset.x + z.size.width &&
                d.current.y >= z.offset.y && d.current.y <= z.offset.y + z.size.height
        }?.pile
    }

    fun finishDrag() {
        val d = state
        val target = dropTarget()
        if (d != null && target != null) onDrop?.invoke(d.from, d.fromIndex, target)
        state = null
    }
}

@Composable
fun SolitaireScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: SolitaireViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    val dragController = remember { DragController() }
    dragController.onDrop = { from, fromIndex, to -> viewModel.onDragMove(from, fromIndex, to) }

    val hasProgress = state.moves > 0 && !state.completed
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
                GameTopBar(
                    title = "纸牌",
                    subtitle = "${state.moves} 步",
                    onBackClick = onBack,
                    rightContent = {
                        Text(
                            formatTime(state.elapsedMs),
                            color = theme.color.textPrimary,
                            style = typo.numericMd,
                            modifier = Modifier.padding(end = theme.spacing.md),
                        )
                    },
                )

                // 演示提示条：一条窄横幅，不挡牌面；换局/结束提示优先于默认演示文案
                if (state.demoMode || state.demoNotice != null) {
                    val notice = state.demoNotice
                        ?: if (state.demoMode) "电脑在玩给您看，点一下屏幕就能自己来" else ""
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.color.accentGold.copy(alpha = 0.20f))
                            .padding(horizontal = theme.spacing.md, vertical = theme.spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(notice, color = theme.color.textPrimary, style = typo.bodyMd)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(theme.spacing.xxs)
                        .clip(RoundedCornerShape(theme.radii.lg))
                        .background(theme.color.bgTubeWell)
                        .padding(theme.spacing.xxs)
                        // 点空白处取消选中（牌面自身会消费点击，不会落到这里）
                        .clickable { viewModel.onBackgroundTap() },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        TopRow(state = state, vm = viewModel, drag = dragController)
                        Spacer(Modifier.height(theme.spacing.md))
                        TableauRow(state = state, vm = viewModel, drag = dragController)
                    }
                    if (state.completed) {
                        WinOverlay(
                            timeLabel = formatTime(state.elapsedMs),
                            secondaryLabel = "步数",
                            secondaryValue = state.moves.toString(),
                            onPlayAgain = viewModel::onRestart,
                            onHome = onHome,
                        )
                    }
                    if (state.demoMode) {
                        // 演示遮罩：全透明不挡牌面，任何点击立即退出演示、恢复原局面
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .zIndex(10f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { viewModel.exitDemo() },
                        )
                    }
                }

                // 底部按钮行：看演示 + 亮光开关 + 玩法
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = theme.spacing.xs),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryButton(
                        text = if (state.demoMode) "停止演示" else "看演示",
                        onClick = { if (state.demoMode) viewModel.exitDemo() else viewModel.startDemo() },
                    )
                    Spacer(Modifier.width(theme.spacing.md))
                    SecondaryButton(
                        text = if (state.glowEnabled) "亮光：开" else "亮光：关",
                        onClick = viewModel::toggleGlow,
                    )
                    Spacer(Modifier.width(theme.spacing.md))
                    HelpButton(steps = TutorialContent.solitaire)
                }
            }
            // 拖拽浮层：跟随手指的牌组，画在最上层
            FloatingDragRun(drag = dragController)
            // 死局弹窗：无任何可走的牌时引导老人重洗或重开（静态展示，尊重 reduceMotion）
            if (state.stuck) {
                StuckDialog(
                    onReshuffle = viewModel::onReshuffle,
                    onRestart = viewModel::onRestart,
                )
            }
        }
        TutorialFirstTime(name = "solitaire", steps = TutorialContent.solitaire)
    }
}

/** 死局弹窗：两个按钮都能解除死局，故不提供"关闭"；遮罩吃掉点击防止误触牌面；无动效，尊重 reduceMotion。 */
@Composable
private fun StuckDialog(onReshuffle: () -> Unit, onRestart: () -> Unit) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(theme.radii.xl))
                .background(theme.color.bgSurface)
                .clickable(enabled = false) {}
                .padding(theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("没有能走的牌了", color = theme.color.textPrimary, style = typo.titleLg)
            Spacer(Modifier.height(theme.spacing.sm))
            Text(
                "帮你重新洗一洗好不好？已经摆好的花色牌不会乱。",
                color = theme.color.textSecondary,
                style = typo.bodyMd,
            )
            Spacer(Modifier.height(theme.spacing.lg))
            PrimaryButton(text = "重新洗牌", onClick = onReshuffle, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(theme.spacing.sm))
            SecondaryButton(text = "重新开始", onClick = onRestart, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TopRow(state: SolitaireUiState, vm: SolitaireViewModel, drag: DragController) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 修复：顶行共 6 张牌（翻牌堆+弃牌堆+4 基础堆）+ 5 处 4dp 间隙，
        // 按屏宽自适应，避免固定 62dp 时小屏溢出、基础堆被裁掉
        val topW = ((maxWidth.value - 5 * 4f) / 6f).coerceIn(36f, 62f).dp
        val topH = topW * 1.42f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stock + Waste
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.clickable { vm.onDrawClick() }) {
                    if (state.stockCount > 0) {
                        PlayingCard(
                            card = Card(Suit.CLUBS, Rank.ACE, faceUp = false),
                            width = topW,
                            height = topH,
                        )
                    } else {
                        PlayingCard(card = null, width = topW, height = topH, placeholder = true)
                        // 库存堆空但弃牌堆还有牌：提示老人点这里能把弃牌翻回来接着翻
                        if (state.waste.isNotEmpty()) {
                            Text(
                                "↻",
                                color = theme.color.textSecondary,
                                style = typo.titleLg,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { drag.register(Pile.Waste, it) }
                        .clickable(enabled = state.waste.isNotEmpty()) { vm.onWasteTap() }
                        .pointerInput(state.waste, topW) {
                            detectDragGestures(
                                onDragStart = { grab ->
                                    val top = state.waste.lastOrNull()
                                    if (top != null) {
                                        drag.state = DragState(
                                            from = Pile.Waste,
                                            fromIndex = 0,
                                            cards = listOf(top),
                                            cardW = topW,
                                            cardH = topH,
                                            gap = 0.dp,
                                            grab = grab,
                                            current = (drag.zones[Pile.Waste]?.offset ?: Offset.Zero) + grab,
                                        )
                                    }
                                },
                                onDrag = { _, amount ->
                                    val d = drag.state
                                    if (d != null) drag.state = d.copy(current = d.current + amount)
                                },
                                onDragEnd = { drag.finishDrag() },
                                onDragCancel = { drag.state = null },
                            )
                        },
                ) {
                    val top = state.waste.lastOrNull()
                    if (top != null) {
                        val wasteSelected = state.selection?.pile is Pile.Waste
                        val wasteDragging = drag.state?.from is Pile.Waste
                        PlayingCard(
                            card = top,
                            width = topW,
                            height = topH,
                            selected = wasteSelected,
                            // 弃牌堆顶有合法去处时发光（选中/拖拽时不重复提示）
                            glow = state.glowEnabled && state.wasteGlow && !wasteSelected && !wasteDragging,
                        )
                    } else {
                        PlayingCard(card = null, width = topW, height = topH, placeholder = true)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            // Foundations
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.foundations.forEachIndexed { idx, pile ->
                    val foundation = Pile.Foundation(idx)
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { drag.register(foundation, it) }
                            .clickable {
                                if (pile.isEmpty()) vm.onEmptyFoundationTap(idx) else vm.onFoundationTap(idx)
                            }
                            .pointerInput(idx, pile, topW) {
                                detectDragGestures(
                                    onDragStart = { grab ->
                                        val top = pile.lastOrNull()
                                        if (top != null) {
                                            drag.state = DragState(
                                                from = foundation,
                                                fromIndex = 0,
                                                cards = listOf(top),
                                                cardW = topW,
                                                cardH = topH,
                                                gap = 0.dp,
                                                grab = grab,
                                                current = (drag.zones[foundation]?.offset ?: Offset.Zero) + grab,
                                            )
                                        }
                                    },
                                    onDrag = { _, amount ->
                                        val d = drag.state
                                        if (d != null) drag.state = d.copy(current = d.current + amount)
                                    },
                                    onDragEnd = { drag.finishDrag() },
                                    onDragCancel = { drag.state = null },
                                )
                            },
                    ) {
                        val top = pile.lastOrNull()
                        val selected = (state.selection?.pile as? Pile.Foundation)?.index == idx
                        val hinted = state.hintTargets.contains(foundation)
                        if (top != null) {
                            PlayingCard(card = top, width = topW, height = topH, selected = selected, hintTarget = hinted)
                        } else {
                            PlayingCard(card = null, width = topW, height = topH, placeholder = true, hintTarget = hinted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableauRow(state: SolitaireUiState, vm: SolitaireViewModel, drag: DragController) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // cardW responsivo (7 colunas + 6 gaps de 2dp)
        val totalGap = 6 * 2
        val cardW = ((maxWidth.value - totalGap) / 7f).coerceIn(36f, 72f).dp
        val cardH = cardW * 1.42f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            state.tableau.forEachIndexed { col, pile ->
                TableauColumn(
                    col = col,
                    cards = pile,
                    selection = state.selection,
                    hintTargets = state.hintTargets,
                    glowEnabled = state.glowEnabled,
                    glowIndices = state.tableauGlow.getOrElse(col) { emptySet() },
                    drag = drag,
                    cardW = cardW,
                    cardH = cardH,
                    onEmpty = { vm.onEmptyTableauTap(col) },
                    onCard = { idx -> vm.onTableauCardTap(col, idx) },
                )
            }
        }
    }
}

@Composable
private fun TableauColumn(
    col: Int,
    cards: List<Card>,
    selection: Selection?,
    hintTargets: Set<Pile>,
    glowEnabled: Boolean,
    glowIndices: Set<Int>,
    drag: DragController,
    cardW: Dp = 46.dp,
    cardH: Dp = 66.dp,
    onEmpty: () -> Unit,
    onCard: (Int) -> Unit,
) {
    val verticalGap = (cardH.value * 0.30f).dp
    val selectedFromIndex = (selection?.pile as? Pile.Tableau)
        ?.takeIf { it.index == col }
        ?.let { selection.fromIndex }
    val isHinted = hintTargets.contains(Pile.Tableau(col))
    val dragFrom = drag.state?.from as? Pile.Tableau
    val dragFromIndex = if (dragFrom?.index == col) drag.state?.fromIndex else null

    Box(
        modifier = Modifier
            .width(cardW)
            .height(cardH + verticalGap * (cards.size.coerceAtLeast(1) - 1).coerceAtLeast(0))
            .onGloballyPositioned { drag.register(Pile.Tableau(col), it) }
            .clickable(enabled = cards.isEmpty()) { onEmpty() },
    ) {
        if (cards.isEmpty()) {
            PlayingCard(card = null, width = cardW, height = cardH, placeholder = true, hintTarget = isHinted)
        } else {
            cards.forEachIndexed { idx, card ->
                val isSelected = selectedFromIndex != null && idx >= selectedFromIndex
                val isDragging = dragFromIndex != null && idx >= dragFromIndex
                // 牌到位/翻开时轻微弹入，给老人"牌落下去了"的确认
                val pop = remember(card) { Animatable(1.12f) }
                LaunchedEffect(pop) { pop.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
                Box(
                    modifier = Modifier
                        .offset(y = verticalGap * idx)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        .clickable { onCard(idx) }
                        .pointerInput(col, idx, cards, cardW) {
                            detectDragGestures(
                                onDragStart = { grab ->
                                    val zone = drag.zones[Pile.Tableau(col)]
                                    if (zone != null) {
                                        drag.state = DragState(
                                            from = Pile.Tableau(col),
                                            fromIndex = idx,
                                            cards = cards.subList(idx, cards.size).toList(),
                                            cardW = cardW,
                                            cardH = cardH,
                                            gap = verticalGap,
                                            grab = grab,
                                            current = zone.offset + Offset(0f, verticalGap.toPx()) + grab,
                                        )
                                    }
                                },
                                onDrag = { _, amount ->
                                    val d = drag.state
                                    if (d != null) drag.state = d.copy(current = d.current + amount)
                                },
                                onDragEnd = { drag.finishDrag() },
                                onDragCancel = { drag.state = null },
                            )
                        },
                ) {
                    PlayingCard(
                        card = card,
                        width = cardW,
                        height = cardH,
                        selected = isSelected,
                        hintTarget = isHinted && idx == cards.lastIndex,
                        // 亮光 = 这张明牌当前有合法去处（选中/拖拽中的牌不重复提示）
                        glow = glowEnabled && !isSelected && !isDragging && glowIndices.contains(idx),
                        dimmed = isDragging,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingDragRun(drag: DragController) {
    val d = drag.state ?: return
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (d.current.x - d.grab.x).roundToInt(),
                    (d.current.y - d.grab.y).roundToInt(),
                )
            }
            .zIndex(30f),
    ) {
        d.cards.forEachIndexed { i, card ->
            Box(modifier = Modifier.offset(y = d.gap * i)) {
                PlayingCard(card = card, width = d.cardW, height = d.cardH)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
