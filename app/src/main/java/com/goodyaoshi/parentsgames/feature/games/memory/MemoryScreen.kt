package com.goodyaoshi.parentsgames.feature.games.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.core.ui.WinOverlay
import com.goodyaoshi.parentsgames.feature.games.memory.domain.Difficulty
import com.goodyaoshi.parentsgames.feature.games.memory.domain.GameStatus
import com.goodyaoshi.parentsgames.feature.games.memory.domain.MemoryCard
import kotlin.math.abs

@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val hasProgress = state.matchedPairs > 0 && state.status is GameStatus.Playing
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
        Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
            GameTopBar(
                title = "翻牌配对",
                subtitle = "${state.difficulty.label} · 已配对 ${state.matchedPairs}/${state.totalPairs}",
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
                MemoryGrid(state = state, viewModel = viewModel)
                if (state.status is GameStatus.Won) {
                    WinOverlay(
                        timeLabel = formatTime(state.elapsedMs),
                        secondaryLabel = "步数",
                        secondaryValue = "${state.moves}",
                        onPlayAgain = viewModel::onRestart,
                        onHome = onHome,
                    )
                }
            }

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = theme.spacing.md, vertical = theme.spacing.sm),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    label = "重新开始",
                    icon = Icons.Outlined.Refresh,
                    onClick = viewModel::onRestart,
                )
                HelpButton(steps = TutorialContent.memory)
            }
        }
        TutorialFirstTime(name = "memory", steps = TutorialContent.memory)
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .background(Color.Transparent)
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

@Composable
private fun MemoryGrid(state: MemoryUiState, viewModel: MemoryViewModel) {
    // 三档难度固定 4 列：牌宽始终按可用宽度均分（与简单档一致，不会变小）；
    // 困难 4×6 在矮屏上略压扁行高，保证整盘可见、无需滚动。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gap = 6.dp
        val cols = state.cols
        val rows = state.rows
        val cellW = (maxWidth - gap * (cols - 1)) / cols
        val cellH = minOf(cellW, (maxHeight - gap * (rows - 1)) / rows)
        Column(
            modifier = Modifier
                .size(
                    width = cellW * cols + gap * (cols - 1),
                    height = cellH * rows + gap * (rows - 1),
                )
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    for (c in 0 until cols) {
                        val idx = r * cols + c
                        val card = state.cards.getOrNull(idx) ?: MemoryCard("")
                        MemoryCardView(
                            card = card,
                            onTap = { viewModel.onCardTap(r, c) },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCardView(
    card: MemoryCard,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(theme.radii.md)

    // 翻开/盖回：轻微翻转动画（中点切换正反面）
    val progress by animateFloatAsState(
        targetValue = if (card.isFaceUp) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "memory-flip",
    )
    // 已配对的牌变淡
    val cardAlpha by animateFloatAsState(
        targetValue = if (card.isMatched) 0.35f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "memory-matched-alpha",
    )
    val faceScale = (abs(progress - 0.5f) * 2f).coerceIn(0.05f, 1f)

    BoxWithConstraints(modifier = modifier) {
        // 牌面字号约占卡面 55%，保证大字清晰
        val emojiSize = (minOf(maxWidth.value, maxHeight.value) * 0.55f).sp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = faceScale
                    alpha = cardAlpha
                }
                .clip(shape)
                .clickable { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            if (progress < 0.5f) {
                // 背面：品牌红 + 中央白色小圆点
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.color.primary)
                        .border(2.dp, theme.color.primaryPressed, shape),
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            } else {
                // 正面：白底描边 + 大号 emoji
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.color.bgSurface)
                        .border(2.dp, theme.color.borderSubtle, shape),
                )
                Text(
                    card.emoji,
                    fontSize = emojiSize,
                    textAlign = TextAlign.Center,
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
