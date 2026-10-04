package com.goodyaoshi.parentsgames.feature.games.gomoku

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuEngine
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuDifficulty
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuMode
import com.goodyaoshi.parentsgames.feature.games.gomoku.domain.GomokuStatus

private val StoneBlack = Color(0xFF232323)
private val LastMoveMark = Color(0xFFE24B4A)
// 提示落点主色：品牌红。原来为细绿圈，老人不易注意到，改为粗红圈 + 呼吸光晕
private val HintRed = Color(0xFFC03A2B)

@Composable
fun GomokuScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: GomokuViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val hasProgress = state.board.any { it != GomokuEngine.EMPTY } && state.status.isPlaying
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
        Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
            GameTopBar(
                title = "五子棋",
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

            // 模式切换：切换即开新局
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = theme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
            ) {
                GomokuMode.entries.forEach { m ->
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
                            modeLabel(m),
                            color = if (isSel) theme.color.textInverse else theme.color.textPrimary,
                            style = typo.labelMd,
                        )
                    }
                }
            }

            // 难度选择（仅人机对战；切换即开新局，样式与扫雷一致）
            if (state.mode == GomokuMode.AI_MODE) {
                Spacer(Modifier.height(theme.spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = theme.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
                ) {
                    GomokuDifficulty.entries.forEach { d ->
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
                    .padding(theme.spacing.xxs)
                    .clip(RoundedCornerShape(theme.radii.lg))
                    .background(theme.color.bgTubeWell)
                    .padding(2.dp),
            ) {
                GomokuBoard(
                    board = state.board,
                    lastMove = state.lastMove,
                    hintMove = state.hintMove,
                    onTap = viewModel::onCellTap,
                )
                if (!state.status.isPlaying) {
                    if (state.mode == GomokuMode.AI_MODE && state.status == GomokuStatus.BlackWon) {
                        WinOverlay(
                            timeLabel = formatTime(state.elapsedMs),
                            secondaryLabel = "模式",
                            secondaryValue = "人机对战",
                            onPlayAgain = viewModel::onRestart,
                            onHome = onHome,
                        )
                    } else {
                        val (title, subtitle) = endTexts(state)
                        EndOverlay(
                            title = title,
                            subtitle = subtitle,
                            onPlayAgain = viewModel::onRestart,
                            onHome = onHome,
                        )
                    }
                }
            }

            Text(
                text = statusLine(state),
                color = if (state.isThinking) theme.color.textSecondary else theme.color.textPrimary,
                style = typo.bodyLg,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.md, vertical = theme.spacing.xs),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.md, vertical = theme.spacing.sm),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(label = "提示", icon = Icons.Outlined.Lightbulb, onClick = viewModel::hint)
                ActionButton(label = "悔棋", icon = Icons.AutoMirrored.Outlined.Undo, onClick = viewModel::undo)
                ActionButton(label = "重新开始", icon = Icons.Outlined.Refresh, onClick = viewModel::onRestart)
                HelpButton(steps = TutorialContent.gomoku)
            }
        }
        TutorialFirstTime(name = "gomoku", steps = TutorialContent.gomoku)
    }
}

@Composable
private fun GomokuBoard(
    board: List<Int>,
    lastMove: Int?,
    hintMove: Int?,
    onTap: (Int, Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    val lineColor = theme.color.borderStrong

    // 提示呼吸动画（0.4..1 往复）：驱动提示落点红色光晕的明暗循环；
    // 「减弱动效」开启时不做动画，仅保留静态粗红圈
    val hintPulse: Float = if (hintMove != null && !theme.reduceMotion) {
        val breath = rememberInfiniteTransition(label = "hintBreath")
        val pulse by breath.animateFloat(
            initialValue = 0.4f,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(theme.color.bgSurface)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val n = GomokuEngine.SIZE
                    // 交点位于每格中心，直接吸附到最近的交点
                    val cellW = size.width.toFloat() / n
                    val cellH = size.height.toFloat() / n
                    val col = (offset.x / cellW).toInt().coerceIn(0, n - 1)
                    val row = (offset.y / cellH).toInt().coerceIn(0, n - 1)
                    onTap(row, col)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = GomokuEngine.SIZE
            val cellW = size.width / n
            val cellH = size.height / n
            val lineStroke = cellW * 0.04f
            val stoneRadius = minOf(cellW, cellH) * 0.425f
            // 网格线
            for (i in 0 until n) {
                val x = cellW * (i + 0.5f)
                val y = cellH * (i + 0.5f)
                drawLine(lineColor, Offset(x, cellH * 0.5f), Offset(x, size.height - cellH * 0.5f), lineStroke)
                drawLine(lineColor, Offset(cellW * 0.5f, y), Offset(size.width - cellW * 0.5f, y), lineStroke)
            }
            // 棋子
            for (idx in board.indices) {
                val v = board[idx]
                if (v == GomokuEngine.EMPTY) continue
                val center = Offset(cellW * (idx % n + 0.5f), cellH * (idx / n + 0.5f))
                if (v == GomokuEngine.BLACK) {
                    drawCircle(StoneBlack, stoneRadius, center)
                } else {
                    drawCircle(Color.White, stoneRadius, center)
                    drawCircle(StoneBlack, stoneRadius, center, style = Stroke(width = lineStroke * 1.4f))
                }
                if (idx == lastMove) {
                    drawCircle(LastMoveMark, stoneRadius * 0.22f, center)
                }
            }
            // 提示落点：品牌红粗描边圈 + 呼吸光晕（替代原来细而不显眼的绿圈）
            if (hintMove != null && hintMove in board.indices && board[hintMove] == GomokuEngine.EMPTY) {
                val center = Offset(cellW * (hintMove % n + 0.5f), cellH * (hintMove / n + 0.5f))
                // 外层光晕：透明度随呼吸脉动，形成「发光」循环
                drawCircle(
                    HintRed.copy(alpha = 0.15f + 0.35f * hintPulse),
                    stoneRadius * 1.35f,
                    center,
                    style = Stroke(width = cellW * 0.06f),
                )
                // 主圈：粗红描边，宽度约为原来两倍，任何瞬间都清晰可见
                drawCircle(HintRed, stoneRadius * 1.18f, center, style = Stroke(width = cellW * 0.13f))
            }
        }
    }
}

@Composable
private fun EndOverlay(
    title: String,
    subtitle: String,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false) {},
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
            Text(title, color = theme.color.textPrimary, style = typo.displayMd)
            Spacer(Modifier.height(theme.spacing.sm))
            Text(subtitle, color = theme.color.textSecondary, style = typo.bodyLg)
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
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.md, vertical = theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = theme.color.primaryPressed,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = theme.color.textSecondary, style = typo.labelMd)
    }
}

private fun modeLabel(mode: GomokuMode): String =
    if (mode == GomokuMode.AI_MODE) "人机对战" else "双人对战"

private fun subtitleFor(state: GomokuUiState): String {
    val modeName = modeLabel(state.mode)
    return when {
        state.isThinking -> "$modeName · 对方思考中…"
        state.status.isPlaying -> "$modeName · ${if (state.status == GomokuStatus.BlackTurn) "黑方行动" else "白方行动"}"
        else -> modeName
    }
}

private fun statusLine(state: GomokuUiState): String = when {
    state.isThinking -> "对方思考中…"
    state.status == GomokuStatus.BlackTurn && state.mode == GomokuMode.AI_MODE -> "轮到您了，请落子（黑棋）"
    state.status == GomokuStatus.BlackTurn -> "轮到黑方落子"
    state.status == GomokuStatus.WhiteTurn -> "轮到白方落子"
    else -> "对局结束"
}

private fun endTexts(state: GomokuUiState): Pair<String, String> = when {
    state.status == GomokuStatus.Draw -> "平局" to "棋盘已下满，势均力敌！"
    state.mode == GomokuMode.AI_MODE -> "真遗憾，再试一次！" to "对方先连成了五子，再来一局吧。"
    state.status == GomokuStatus.BlackWon -> "黑方胜利！" to "黑棋先连成了五子。"
    else -> "白方胜利！" to "白棋先连成了五子。"
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
