package com.goodyaoshi.parentsgames.feature.games.xiangqi

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.PrimaryButton
import com.goodyaoshi.parentsgames.core.ui.TertiaryButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.core.ui.WinOverlay
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.ADVISOR
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.CANNON
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.ELEPHANT
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.HORSE
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.KING
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.PAWN
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.ROOK
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.XiangqiDifficulty
import com.goodyaoshi.parentsgames.feature.games.xiangqi.domain.XiangqiStatus
import kotlin.math.abs
import kotlin.math.roundToInt

// 棋盘内边距占棋盘宽/高的比例，保证边缘棋子不被裁掉
private const val PAD_X_FRACTION = 0.06f
private const val PAD_Y_FRACTION = 0.05f

// 提示着法高亮主色：品牌红。与选中/可吃圈（primary）、上一步圈（金色）明显区分，老人一眼可辨
private val HintRed = Color(0xFFC03A2B)

private fun pieceText(p: Int): String = when (abs(p)) {
    KING -> if (p > 0) "帅" else "将"
    ADVISOR -> if (p > 0) "仕" else "士"
    ELEPHANT -> if (p > 0) "相" else "象"
    HORSE -> "马"
    ROOK -> "车"
    CANNON -> "炮"
    PAWN -> if (p > 0) "兵" else "卒"
    else -> ""
}

private fun isPlayingStatus(status: XiangqiStatus): Boolean =
    status == XiangqiStatus.RED_TURN || status == XiangqiStatus.BLACK_TURN

private fun subtitleFor(state: XiangqiUiState): String = when {
    state.status == XiangqiStatus.RED_WON -> "红方胜利！"
    state.status == XiangqiStatus.BLACK_WON -> "黑方胜利！"
    state.isThinking -> "对方思考中…"
    state.inCheck -> "${state.mode.label} · 将军！"
    else -> state.mode.label
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}

@Composable
fun XiangqiScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: XiangqiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val hasProgress = state.lastMove != null && isPlayingStatus(state.status)

    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.color.bgCanvas)
                .systemBarsPadding(),
        ) {
            GameTopBar(
                title = "中国象棋",
                subtitle = subtitleFor(state),
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

            // 模式切换（切换即开新局）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
            ) {
                XiangqiMode.entries.forEach { m ->
                    val isSel = state.mode == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(theme.radii.md))
                            .background(if (isSel) theme.color.primary else theme.color.bgSurfaceMuted)
                            .clickable { viewModel.setMode(m) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            m.label,
                            color = if (isSel) theme.color.textInverse else theme.color.textPrimary,
                            style = typo.labelMd,
                        )
                    }
                }
            }

            // 难度选择（仅人机对战；切换即开新局，样式与扫雷一致）
            if (state.mode == XiangqiMode.AI_MODE) {
                Spacer(Modifier.height(theme.spacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = theme.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
                ) {
                    XiangqiDifficulty.entries.forEach { d ->
                        val isSel = state.difficulty == d
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(theme.radii.md))
                                .background(if (isSel) theme.color.primary else theme.color.bgSurfaceMuted)
                                .clickable { viewModel.setDifficulty(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                d.label,
                                color = if (isSel) theme.color.textInverse else theme.color.textPrimary,
                                style = typo.labelMd,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(theme.spacing.sm))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(9f / 10f)
                        .clip(RoundedCornerShape(theme.radii.lg))
                        .background(theme.color.bgTubeWell),
                ) {
                    XiangqiBoard(state = state, onTap = viewModel::onCellTap)
                }

                val gameOver = state.status == XiangqiStatus.RED_WON || state.status == XiangqiStatus.BLACK_WON
                if (gameOver) {
                    if (state.mode == XiangqiMode.AI_MODE && state.status == XiangqiStatus.RED_WON) {
                        WinOverlay(
                            timeLabel = formatTime(state.elapsedMs),
                            secondaryLabel = "模式",
                            secondaryValue = state.mode.label,
                            onPlayAgain = viewModel::onRestart,
                            onHome = onHome,
                        )
                    } else {
                        val message = if (state.mode == XiangqiMode.AI_MODE) {
                            "真遗憾，再试一次！"
                        } else if (state.status == XiangqiStatus.RED_WON) {
                            "红方胜利！"
                        } else {
                            "黑方胜利！"
                        }
                        GameOverOverlay(
                            message = message,
                            onPlayAgain = viewModel::onRestart,
                            onHome = onHome,
                        )
                    }
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.md, vertical = theme.spacing.sm),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    label = "提示",
                    icon = Icons.Outlined.Lightbulb,
                    onClick = viewModel::hint,
                )
                ActionButton(
                    label = "悔棋",
                    icon = Icons.Outlined.Undo,
                    onClick = viewModel::undo,
                )
                ActionButton(
                    label = "重新开始",
                    icon = Icons.Outlined.Refresh,
                    onClick = viewModel::onRestart,
                )
                HelpButton(steps = TutorialContent.xiangqi)
            }
        }
        TutorialFirstTime(name = "xiangqi", steps = TutorialContent.xiangqi)
    }
}

@Composable
private fun XiangqiBoard(
    state: XiangqiUiState,
    onTap: (Int, Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val riverStyle = remember(theme) {
        TextStyle(color = theme.color.borderStrong, fontWeight = FontWeight.W500)
    }

    // 提示呼吸动画（0.45..1 往复）：让提示圈明暗循环更醒目；
    // 「减弱动效」开启时不做动画，仅保留静态红圈
    val hintPulse: Float = if (state.hintMove != null && !theme.reduceMotion) {
        val breath = rememberInfiniteTransition(label = "hintBreath")
        val pulse by breath.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "hintBreathValue",
        )
        pulse
    } else {
        1f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val px = w * PAD_X_FRACTION
                    val py = h * PAD_Y_FRACTION
                    val cw = (w - 2 * px) / 8f
                    val ch = (h - 2 * py) / 9f
                    val c = ((pos.x - px) / cw).roundToInt()
                    val r = ((pos.y - py) / ch).roundToInt()
                    if (r in 0..9 && c in 0..8) onTap(r, c)
                }
            },
    ) {
        val padX = maxWidth * PAD_X_FRACTION
        val padY = maxHeight * PAD_Y_FRACTION
        val cellW = (maxWidth - padX * 2) / 8f
        val cellH = (maxHeight - padY * 2) / 9f
        val pieceSize = minOf(cellW, cellH) * 0.92f
        val pieceFont = with(density) { (pieceSize * 0.58f).toSp() }
        val pieceFontSize: TextUnit = if (pieceFont.value >= 22f) pieceFont else 22.sp
        val riverFont = with(density) { (cellH * 0.5f).toSp() }

        fun cx(c: Int): Dp = padX + cellW * c
        fun cy(r: Int): Dp = padY + cellH * r

        // 棋盘线
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val px = w * PAD_X_FRACTION
            val py = h * PAD_Y_FRACTION
            val cw = (w - 2 * px) / 8f
            val ch = (h - 2 * py) / 9f
            fun gx(c: Int) = px + c * cw
            fun gy(r: Int) = py + r * ch
            val lineColor = theme.color.borderStrong
            val stroke = 1.5.dp.toPx()

            // 横线
            for (r in 0..9) {
                drawLine(lineColor, Offset(gx(0), gy(r)), Offset(gx(8), gy(r)), stroke)
            }
            // 竖线：两侧贯通，中间在楚河汉界处断开
            for (c in 0..8) {
                if (c == 0 || c == 8) {
                    drawLine(lineColor, Offset(gx(c), gy(0)), Offset(gx(c), gy(9)), stroke)
                } else {
                    drawLine(lineColor, Offset(gx(c), gy(0)), Offset(gx(c), gy(4)), stroke)
                    drawLine(lineColor, Offset(gx(c), gy(5)), Offset(gx(c), gy(9)), stroke)
                }
            }
            // 外框加粗
            drawRect(
                lineColor,
                topLeft = Offset(gx(0), gy(0)),
                size = Size(gx(8) - gx(0), gy(9) - gy(0)),
                style = Stroke(width = 2.5.dp.toPx()),
            )
            // 九宫斜线
            drawLine(lineColor, Offset(gx(3), gy(0)), Offset(gx(5), gy(2)), stroke)
            drawLine(lineColor, Offset(gx(5), gy(0)), Offset(gx(3), gy(2)), stroke)
            drawLine(lineColor, Offset(gx(3), gy(7)), Offset(gx(5), gy(9)), stroke)
            drawLine(lineColor, Offset(gx(5), gy(7)), Offset(gx(3), gy(9)), stroke)
            // 楚河 汉界
            val riverText = textMeasurer.measure("楚河　　汉界", riverStyle.copy(fontSize = riverFont))
            drawText(
                riverText,
                topLeft = Offset(
                    (w - riverText.size.width) / 2f,
                    gy(4) + ch / 2f - riverText.size.height / 2f,
                ),
            )
        }

        // 上一步起点小圆点
        state.lastMove?.let { lm ->
            MarkerDot(
                cx = cx(lm.fromC),
                cy = cy(lm.fromR),
                size = pieceSize * 0.28f,
                color = theme.color.accentGold,
            )
        }
        // 可走位置小圆点（空点）
        state.legalTargets.forEach { m ->
            val occupied = state.board.getOrNull(m.toR)?.getOrNull(m.toC) ?: 0
            if (occupied == 0) {
                MarkerDot(
                    cx = cx(m.toC),
                    cy = cy(m.toR),
                    size = pieceSize * 0.28f,
                    color = theme.color.primary,
                )
            }
        }

        // 棋子
        for (r in 0..9) for (c in 0..8) {
            val p = state.board.getOrNull(r)?.getOrNull(c) ?: 0
            if (p == 0) continue
            val isSelected = state.selected == r to c
            val checked = state.inCheck && abs(p) == KING &&
                (p > 0) == (state.status == XiangqiStatus.RED_TURN)
            XiangqiPiece(
                piece = p,
                selected = isSelected,
                checked = checked,
                size = pieceSize,
                fontSize = pieceFontSize,
                modifier = Modifier.offset(
                    x = cx(c) - pieceSize / 2,
                    y = cy(r) - pieceSize / 2,
                ),
            )
        }

        // 可吃目标的圈
        state.legalTargets.forEach { m ->
            val occupied = state.board.getOrNull(m.toR)?.getOrNull(m.toC) ?: 0
            if (occupied != 0) {
                MarkerRing(
                    cx = cx(m.toC),
                    cy = cy(m.toR),
                    size = pieceSize + 6.dp,
                    color = theme.color.primary,
                    width = 3.dp,
                )
            }
        }
        // 上一步终点圈
        state.lastMove?.let { lm ->
            if (state.selected != lm.toR to lm.toC) {
                MarkerRing(
                    cx = cx(lm.toC),
                    cy = cy(lm.toR),
                    size = pieceSize + 4.dp,
                    color = theme.color.accentGold,
                    width = 2.5f.dp,
                )
            }
        }
        // 提示着法圈：品牌红粗描边 + 呼吸明暗，圈也比普通标记更大，
        // 与选中圈/可吃圈（primary 色）和上一步圈（金色）一眼区分
        state.hintMove?.let { h ->
            MarkerRing(
                cx = cx(h.fromC),
                cy = cy(h.fromR),
                size = pieceSize + 12.dp,
                color = HintRed,
                width = 4.dp,
                alpha = hintPulse,
            )
            MarkerRing(
                cx = cx(h.toC),
                cy = cy(h.toR),
                size = pieceSize + 12.dp,
                color = HintRed,
                width = 4.dp,
                alpha = hintPulse,
            )
        }
    }
}

@Composable
private fun XiangqiPiece(
    piece: Int,
    selected: Boolean,
    checked: Boolean,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val isRed = piece > 0
    val textColor = if (isRed) theme.color.feedbackError else theme.color.textPrimary
    val borderColor = when {
        selected -> theme.color.primary
        checked -> theme.color.feedbackError
        isRed -> theme.color.feedbackError
        else -> theme.color.textSecondary
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) theme.color.primary.copy(alpha = 0.15f) else theme.color.bgSurface)
            .border(if (selected || checked) 3.dp else 2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.84f)
                .clip(CircleShape)
                .border(1.dp, borderColor.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                pieceText(piece),
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.W800,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MarkerDot(cx: Dp, cy: Dp, size: Dp, color: Color) {
    Box(
        modifier = Modifier
            .offset(x = cx - size / 2, y = cy - size / 2)
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun MarkerRing(cx: Dp, cy: Dp, size: Dp, color: Color, width: Dp, alpha: Float = 1f) {
    Box(
        modifier = Modifier
            .offset(x = cx - size / 2, y = cy - size / 2)
            .size(size)
            .clip(CircleShape)
            // alpha 用于提示圈的呼吸明暗（默认 1f 不影响其他标记圈）
            .border(width, color.copy(alpha = alpha.coerceIn(0f, 1f)), CircleShape),
    )
}

@Composable
private fun GameOverOverlay(
    message: String,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(theme.radii.xl))
                .background(theme.color.bgSurface)
                .padding(theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, color = theme.color.textPrimary, style = typo.displayMd)
            Spacer(Modifier.height(theme.spacing.lg))
            PrimaryButton(text = "再玩一局", onClick = onPlayAgain, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(theme.spacing.sm))
            TertiaryButton(text = "返回首页", onClick = onHome, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .background(if (highlighted) theme.color.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.md, vertical = theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (highlighted) theme.color.primary else theme.color.primaryPressed,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = theme.color.textSecondary, style = typo.labelMd)
    }
}
