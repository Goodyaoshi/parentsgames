package com.goodyaoshi.parentsgames.feature.games.match3

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.feature.games.match3.domain.CellContent
import com.goodyaoshi.parentsgames.feature.games.match3.ui.Gem

@Composable
fun Match3Screen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: Match3ViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val hasProgress = state.score > 0
    // FIX V307: alvo da fase
    val stageTarget = listOf(0L, 5_000L, 12_000L, 25_000L, 50_000L, 100_000L).getOrNull(state.stage) ?: 0L
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        GameTopBar(
            title = "宝石消除",
            subtitle = if (stageTarget > 0) "第 ${state.stage} 关 · ${state.score} / ${stageTarget}" else "第 ${state.stage} 关",
            onBackClick = onBack,
            rightContent = {
                Text(
                    "%,d".format(state.score),
                    color = theme.color.textPrimary,
                    style = typo.numericLg,
                    modifier = Modifier.padding(end = theme.spacing.md),
                )
            },
        )

        BoxWithConstraints(
            modifier = Modifier
                .padding(theme.spacing.xxs)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(theme.radii.lg))
                .background(theme.color.bgTubeWell)
                .padding(2.dp),
        ) {
            val boardSize = if (maxWidth < maxHeight) maxWidth else maxHeight
            val cellSize = boardSize / 6f
            Match3Board(
                state = state,
                cellSize = cellSize,
                onCellTap = viewModel::onCellTap,
            )
        }

        Spacer(Modifier.height(theme.spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "点一颗宝石，再点相邻的宝石。三颗以上相同即可得分。",
                color = theme.color.textSecondary,
                style = typo.labelMd,
                modifier = Modifier.weight(1f).padding(horizontal = theme.spacing.lg, vertical = theme.spacing.xs),
            )
            HelpButton(steps = TutorialContent.match3)
        }
    }
    TutorialFirstTime(name = "match3", steps = TutorialContent.match3)
    }
}

@Composable
private fun Match3Board(
    state: Match3UiState,
    cellSize: androidx.compose.ui.unit.Dp,
    onCellTap: (Int, Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Grid de células
        for (r in state.board.indices) {
            for (c in state.board[r].indices) {
                val cell = state.board[r][c]
                val swap = state.swapping
                val isSwappingThis = swap != null && (
                    (swap.r1 == r && swap.c1 == c) ||
                        (swap.r2 == r && swap.c2 == c)
                    )
                val targetOffset: Pair<Float, Float> = when {
                    !isSwappingThis -> 0f to 0f
                    swap.phase == SwapAnimation.Phase.OUTGOING -> {
                        // Vai para a posição da outra
                        if (swap.r1 == r && swap.c1 == c) {
                            (swap.c2 - swap.c1).toFloat() to (swap.r2 - swap.r1).toFloat()
                        } else {
                            (swap.c1 - swap.c2).toFloat() to (swap.r1 - swap.r2).toFloat()
                        }
                    }
                    swap.phase == SwapAnimation.Phase.RETURNING -> 0f to 0f
                    else -> 0f to 0f
                }
                val animatedDx by animateFloatAsState(
                    targetValue = targetOffset.first,
                    animationSpec = tween(durationMillis = 220),
                    label = "swap-dx",
                )
                val animatedDy by animateFloatAsState(
                    targetValue = targetOffset.second,
                    animationSpec = tween(durationMillis = 220),
                    label = "swap-dy",
                )
                val selected = state.selected == (r to c)
                GemCellAt(
                    cell = cell,
                    selected = selected,
                    cellSize = cellSize,
                    posR = r,
                    posC = c,
                    extraOffsetDx = animatedDx,
                    extraOffsetDy = animatedDy,
                    onClick = { onCellTap(r, c) },
                )
            }
        }

        // Floaters (pontuação flutuante) — FIX V303: key garante animação por floater único
        for (f in state.floaters) {
            key(f.id) {
                FloatingPoints(
                    text = f.text,
                    cellSize = cellSize,
                    r = f.r,
                    c = f.c,
                )
            }
        }
    }
}

@Composable
private fun GemCellAt(
    cell: CellContent,
    selected: Boolean,
    cellSize: androidx.compose.ui.unit.Dp,
    posR: Int,
    posC: Int,
    extraOffsetDx: Float,
    extraOffsetDy: Float,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val isVisible = cell !is CellContent.Empty

    val scale by animateFloatAsState(
        targetValue = when {
            !isVisible -> 0f
            selected -> 1.12f
            else -> 1f
        },
        animationSpec = if (selected) spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ) else tween(durationMillis = 160),
        label = "gem-scale",
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "gem-alpha",
    )

    val xDp = (posC * cellSize.value + extraOffsetDx * cellSize.value).dp
    val yDp = (posR * cellSize.value + extraOffsetDy * cellSize.value).dp

    Box(
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .size(cellSize)
            .padding(2.dp)
            .graphicsLayer {
                scaleX = scale; scaleY = scale; this.alpha = alpha
            }
            .clip(RoundedCornerShape(8.dp))
            .background(theme.color.bgSurface.copy(alpha = 0.20f))
            .then(
                if (selected) Modifier.border(2.5.dp, theme.color.accentGold, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (cell !is CellContent.Empty) {
            Gem(cell = cell, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FloatingPoints(
    text: String,
    cellSize: androidx.compose.ui.unit.Dp,
    r: Int,
    c: Int,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val rise by animateFloatAsState(
        targetValue = -40f,
        animationSpec = tween(durationMillis = 850),
        label = "floater-rise",
    )
    val fade by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 850),
        label = "floater-fade",
    )

    Box(
        modifier = Modifier
            .offset(x = (c * cellSize.value).dp, y = (r * cellSize.value).dp)
            .size(cellSize)
            .graphicsLayer {
                translationY = rise
                this.alpha = fade.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = theme.color.accentGold,
            style = typo.numericMd,
        )
    }
}
