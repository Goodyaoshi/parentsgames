package com.goodyaoshi.parentsgames.feature.games.minesweeper

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.core.ui.WinOverlay
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.Cell
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.Difficulty
import com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.GameStatus
import kotlinx.coroutines.delay

private val NumberColors = mapOf(
    1 to Color(0xFF1D5FA5),
    2 to Color(0xFF1E6B3C),
    3 to Color(0xFFB8351F),
    4 to Color(0xFF3C3489),
    5 to Color(0xFF712B13),
    6 to Color(0xFF0F6E56),
    7 to Color(0xFF2C2A26),
    8 to Color(0xFF6B665E),
)

@Composable
fun MinesweeperScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: MinesweeperViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    // 提示高亮约 1 秒后自动熄灭
    LaunchedEffect(state.hintSeq) {
        if (state.hintTarget != null) {
            delay(1000)
            viewModel.clearHint()
        }
    }

    val hasProgress = state.grid.any { row -> row.any { it.isRevealed || it.isFlagged } } &&
        state.status is com.goodyaoshi.parentsgames.feature.games.minesweeper.domain.GameStatus.Playing
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        GameTopBar(
            title = "扫雷",
            subtitle = "${state.difficulty.label} · ${state.flagsRemaining}🚩",
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

        // 演示提示条：一条窄横幅，不挡棋盘；结束后短暂显示"您来试试"
        if (state.demoMode || state.demoNotice != null) {
            val notice = if (state.demoMode) "电脑在玩给您看，点一下屏幕就能自己来" else state.demoNotice.orEmpty()
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

        // Seletor de dificuldade
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = theme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
        ) {
            Difficulty.entries.forEach { d ->
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
            MinesweeperGrid(state = state, viewModel = viewModel)
            if (state.status is GameStatus.Won) {
                WinOverlay(
                    timeLabel = formatTime(state.elapsedMs),
                    secondaryLabel = "难度",
                    secondaryValue = state.difficulty.label,
                    onPlayAgain = viewModel::onRestart,
                    onHome = onHome,
                )
            }
            if (state.status is GameStatus.Lost) {
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
                        Text("真遗憾！", color = theme.color.textPrimary, style = typo.displayMd)
                        Spacer(Modifier.height(theme.spacing.sm))
                        Text(
                            "再试一次，你可以的！",
                            color = theme.color.textSecondary,
                            style = typo.bodyLg,
                        )
                        Spacer(Modifier.height(theme.spacing.lg))
                        com.goodyaoshi.parentsgames.core.ui.PrimaryButton(
                            text = "再玩一局",
                            onClick = viewModel::onRestart,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(theme.spacing.sm))
                        com.goodyaoshi.parentsgames.core.ui.TertiaryButton(
                            text = "返回首页",
                            onClick = onHome,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (state.demoMode) {
                // 演示遮罩：全透明不挡棋盘，任何点击立即退出演示、恢复原局面
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

        // 底部按钮行：模式 / 提示 / 看演示 / 重新开始 / 玩法（均分宽度，防挤坏）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = theme.spacing.xs, vertical = theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                label = if (state.flagMode) "插旗模式" else "点击模式",
                icon = Icons.Outlined.Flag,
                highlighted = state.flagMode,
                onClick = viewModel::toggleFlagMode,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "提示",
                icon = Icons.Outlined.Lightbulb,
                onClick = viewModel::onHint,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = if (state.demoMode) "停止演示" else "看演示",
                icon = Icons.Outlined.PlayArrow,
                onClick = { if (state.demoMode) viewModel.exitDemo() else viewModel.startDemo() },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "重新开始",
                icon = Icons.Outlined.Refresh,
                onClick = viewModel::onRestart,
                modifier = Modifier.weight(1f),
            )
            HelpButton(steps = TutorialContent.minesweeper)
        }
    }
    TutorialFirstTime(name = "minesweeper", steps = TutorialContent.minesweeper)
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .background(if (highlighted) theme.color.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (highlighted) theme.color.primary else theme.color.primaryPressed,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = theme.color.textSecondary, style = typo.labelMd, maxLines = 1)
    }
}

@Composable
private fun MinesweeperGrid(state: MinesweeperUiState, viewModel: MinesweeperViewModel) {
    val aspectRatio = state.cols.toFloat() / state.rows.toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            for (r in 0 until state.rows) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    for (c in 0 until state.cols) {
                        val cell = state.grid.getOrNull(r)?.getOrNull(c) ?: Cell()
                        val hinted = state.hintTarget?.let { it.first == r && it.second == c } == true
                        MinesweeperCell(
                            cell = cell,
                            hinted = hinted,
                            onTap = { viewModel.onCellTap(r, c) },
                            onLongPress = { viewModel.onCellLongPress(r, c) },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinesweeperCell(
    cell: Cell,
    hinted: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    // 提示高亮：金色光圈呼吸；系统开了"减弱动态效果"就给静态亮圈
    val ringAlpha = if (!hinted) 0f
    else if (theme.reduceMotion) 0.9f
    else rememberInfiniteTransition(label = "msHintRing").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "msHintRingAlpha",
    ).value
    val bg = when {
        cell.isRevealed && cell.isMine -> Color(0xFFE24B4A)
        cell.isRevealed -> theme.color.bgSurface
        else -> theme.color.bgSurfaceMuted
    }
    val border = when {
        cell.isRevealed -> theme.color.borderSubtle
        else -> theme.color.borderStrong
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .then(if (hinted) Modifier.background(theme.color.accentGold.copy(alpha = 0.16f)) else Modifier)
            .border(0.5.dp, border, RoundedCornerShape(3.dp))
            .then(
                if (hinted) {
                    Modifier.border(2.5.dp, theme.color.accentGold.copy(alpha = ringAlpha), RoundedCornerShape(3.dp))
                } else {
                    Modifier
                }
            )
            .pointerInput(cell) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            cell.isFlagged && !cell.isRevealed -> Text("🚩", fontSize = 16.sp)
            cell.isRevealed && cell.isMine -> Text("💣", fontSize = 16.sp)
            cell.isRevealed && cell.adjacentMines > 0 -> {
                Text(
                    cell.adjacentMines.toString(),
                    color = NumberColors[cell.adjacentMines] ?: Color.Black,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700),
                )
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
