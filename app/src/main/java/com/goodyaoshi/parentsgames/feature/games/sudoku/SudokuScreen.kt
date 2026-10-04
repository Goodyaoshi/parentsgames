package com.goodyaoshi.parentsgames.feature.games.sudoku

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.goodyaoshi.parentsgames.feature.games.sudoku.domain.Difficulty
import com.goodyaoshi.parentsgames.feature.games.sudoku.domain.GameStatus
import com.goodyaoshi.parentsgames.feature.games.sudoku.domain.SudokuCell

@Composable
fun SudokuScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: SudokuViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val hasProgress = state.status is GameStatus.Playing &&
        state.board.any { row -> row.any { !it.given && it.value != 0 } }
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
        Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
            GameTopBar(
                title = "数独",
                subtitle = state.difficulty.label,
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

            // 难度选择
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
                SudokuBoard(
                    state = state,
                    onCellTap = viewModel::onCellTap,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.status is GameStatus.Won) {
                    WinOverlay(
                        timeLabel = formatTime(state.elapsedMs),
                        secondaryLabel = "难度",
                        secondaryValue = state.difficulty.label,
                        onPlayAgain = viewModel::onRestart,
                        onHome = onHome,
                    )
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

            // 数字键盘 + 擦除
            NumberKeypad(
                digitMax = state.digitMax,
                onNumber = viewModel::onNumberTap,
                onErase = viewModel::onErase,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.md, vertical = theme.spacing.xs),
            )

            // 提示 / 看演示 / 重新开始 / 玩法（均分宽度，防挤坏）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.xs, vertical = theme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    label = "提示",
                    icon = Icons.Outlined.Lightbulb,
                    onClick = viewModel::hint,
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
                HelpButton(steps = TutorialContent.sudoku)
            }
        }
        TutorialFirstTime(name = "sudoku", steps = TutorialContent.sudoku)
    }
}

@Composable
private fun SudokuBoard(
    state: SudokuUiState,
    onCellTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val d = state.difficulty

    Box(modifier = modifier.aspectRatio(1f)) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until state.rows) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until state.cols) {
                        val cell = state.board.getOrNull(r)?.getOrNull(c) ?: SudokuCell()
                        val isPeer = state.selected?.let { (sr, sc) ->
                            r == sr || c == sc ||
                                (r / d.boxRows == sr / d.boxRows && c / d.boxCols == sc / d.boxCols)
                        } ?: false
                        SudokuBoardCell(
                            cell = cell,
                            isSelected = state.selected == (r to c),
                            isPeer = isPeer,
                            bigFont = state.digitMax <= 6,
                            demoTarget = state.demoMode && state.demoLastCell == (r to c),
                            onTap = { onCellTap(r, c) },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }
        // 粗宫线（画在格子上面）
        Canvas(modifier = Modifier.matchParentSize()) {
            val cw = size.width / state.cols
            val ch = size.height / state.rows
            val line = 2.dp.toPx()
            for (i in 1 until state.cols / d.boxCols) {
                drawLine(
                    color = theme.color.borderStrong,
                    start = Offset(cw * i * d.boxCols, 0f),
                    end = Offset(cw * i * d.boxCols, size.height),
                    strokeWidth = line,
                )
            }
            for (j in 1 until state.rows / d.boxRows) {
                drawLine(
                    color = theme.color.borderStrong,
                    start = Offset(0f, ch * j * d.boxRows),
                    end = Offset(size.width, ch * j * d.boxRows),
                    strokeWidth = line,
                )
            }
            drawRect(
                color = theme.color.borderStrong,
                style = Stroke(width = line),
            )
        }
    }
}

@Composable
private fun SudokuBoardCell(
    cell: SudokuCell,
    isSelected: Boolean,
    isPeer: Boolean,
    bigFont: Boolean,
    demoTarget: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    // 演示刚填入的格：金色光圈呼吸；系统开了"减弱动态效果"就给静态亮圈
    val ringAlpha = if (!demoTarget) 0f
    else if (theme.reduceMotion) 0.9f
    else rememberInfiniteTransition(label = "sudokuDemoRing").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "sudokuDemoRingAlpha",
    ).value
    val bg = when {
        cell.error -> theme.color.feedbackError.copy(alpha = 0.20f)
        isSelected -> theme.color.primary.copy(alpha = 0.24f)
        isPeer -> theme.color.primary.copy(alpha = 0.08f)
        else -> theme.color.bgSurface
    }
    Box(
        modifier = modifier
            .background(bg)
            .then(if (demoTarget) Modifier.background(theme.color.accentGold.copy(alpha = 0.16f)) else Modifier)
            .border(0.5.dp, theme.color.borderSubtle)
            .then(
                if (demoTarget) {
                    Modifier.border(2.5.dp, theme.color.accentGold.copy(alpha = ringAlpha))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (cell.value != 0) {
            val color = when {
                cell.error -> theme.color.feedbackError
                cell.given -> theme.color.textPrimary
                else -> theme.color.primary
            }
            val weight = if (cell.given) FontWeight.W700 else FontWeight.W600
            val style = typo.numericLg.copy(fontWeight = weight, fontSize = if (bigFont) 30.sp else 24.sp)
            Text(cell.value.toString(), color = color, style = style)
        }
    }
}

@Composable
private fun NumberKeypad(
    digitMax: Int,
    onNumber: (Int) -> Unit,
    onErase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    @Composable
    fun Key(label: String, muted: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Box(
            modifier = modifier
                .height(56.dp)
                .clip(RoundedCornerShape(theme.radii.md))
                .background(if (muted) theme.color.bgSurfaceMuted else theme.color.bgSurface)
                .border(0.5.dp, theme.color.borderSubtle, RoundedCornerShape(theme.radii.md))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = theme.color.textPrimary,
                style = if (muted) TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W600) else typo.numericLg,
            )
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
        if (digitMax <= 6) {
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                for (n in 1..digitMax) Key(n.toString(), modifier = Modifier.weight(1f)) { onNumber(n) }
                Key("擦除", muted = true, modifier = Modifier.weight(1f)) { onErase() }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                for (n in 1..5) Key(n.toString(), modifier = Modifier.weight(1f)) { onNumber(n) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                for (n in 6..9) Key(n.toString(), modifier = Modifier.weight(1f)) { onNumber(n) }
                Key("擦除", muted = true, modifier = Modifier.weight(1f)) { onErase() }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = theme.color.primaryPressed,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = theme.color.textSecondary, style = typo.labelMd, maxLines = 1)
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
