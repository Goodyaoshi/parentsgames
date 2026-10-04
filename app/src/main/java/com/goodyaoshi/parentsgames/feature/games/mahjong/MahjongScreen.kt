package com.goodyaoshi.parentsgames.feature.games.mahjong

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.automirrored.outlined.Undo
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.R
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
import com.goodyaoshi.parentsgames.feature.games.mahjong.domain.MahjongBoardLayout
import com.goodyaoshi.parentsgames.feature.games.mahjong.domain.MahjongTile
import com.goodyaoshi.parentsgames.feature.games.mahjong.ui.MahjongTile as MahjongTileVisual
import com.goodyaoshi.parentsgames.feature.games.mahjong.ui.TileVisualState

@Composable
fun MahjongScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: MahjongViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val hasProgress = state.tiles.any { it.removed } && !state.completed

    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
            GameTopBar(
                title = "麻将",
                subtitle = "剩余 ${state.remaining} 张",
                onBackClick = { if (hasProgress) onBack() else onBack() },
            rightContent = {
                Text(
                    formatTime(state.elapsedMs),
                    color = theme.color.textPrimary,
                    style = typo.numericMd,
                    modifier = Modifier.padding(end = theme.spacing.md),
                )
            },
        )

        // 盘面规格切换（风格与扫雷难度选择器一致，选中高亮 primary）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = theme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
        ) {
            MahjongBoardLayout.entries.forEach { l ->
                val isSel = state.layout == l
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(theme.radii.md))
                        .background(if (isSel) theme.color.primary else theme.color.bgSurfaceMuted)
                        .clickable { viewModel.onLayoutSelected(l) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        boardLabel(l),
                        color = if (isSel) theme.color.textInverse else theme.color.textPrimary,
                        style = typo.labelMd,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(theme.spacing.xxs)
                .clip(RoundedCornerShape(theme.radii.lg))
                .background(theme.color.bgTubeWell),
        ) {
            MahjongBoard(state = state, onTileTap = viewModel::onTileTap)
            if (state.completed) {
                WinOverlay(
                    timeLabel = formatTime(state.elapsedMs),
                    secondaryLabel = "牌数",
                    secondaryValue = state.tiles.size.toString(),
                    onPlayAgain = viewModel::onRestart,
                    onHome = onHome,
                )
            }
        }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.md, vertical = theme.spacing.sm),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(label = "撤销", icon = Icons.AutoMirrored.Outlined.Undo, onClick = viewModel::onUndo)
                ActionButton(label = "提示", icon = Icons.Outlined.Lightbulb, onClick = viewModel::onHint)
                ActionButton(label = "洗牌", icon = Icons.Outlined.Shuffle, onClick = viewModel::onReshuffle)
                HelpButton(steps = TutorialContent.mahjong)
            }
        }
        // 死局洗牌确认弹窗：无解自动检测或点「提示」无可配对时触发
        if (state.showReshuffleDialog) {
            NoMovesDialog(
                onConfirm = viewModel::onReshuffleConfirmed,
                onDismiss = viewModel::onReshuffleDialogDismissed,
            )
        }
      }
      TutorialFirstTime(name = "mahjong", steps = TutorialContent.mahjong)
    }
}

/** 死局弹窗：文案复用 strings 里的 mahjong_no_moves；静态展示，不依赖动效（尊重 reduceMotion）。 */
@Composable
private fun NoMovesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
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
            Text("无牌可配", color = theme.color.textPrimary, style = typo.titleLg)
            Spacer(Modifier.height(theme.spacing.sm))
            Text(
                stringResource(R.string.mahjong_no_moves),
                color = theme.color.textSecondary,
                style = typo.bodyMd,
            )
            Spacer(Modifier.height(theme.spacing.lg))
            PrimaryButton(text = "洗个牌", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(theme.spacing.sm))
            TertiaryButton(text = "先不洗", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun boardLabel(layout: MahjongBoardLayout): String = when (layout) {
    MahjongBoardLayout.SMALL -> "小盘 ${layout.tileCount} 张"
    MahjongBoardLayout.LARGE -> "大盘 ${layout.tileCount} 张"
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
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.sm, vertical = theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = theme.color.primaryPressed, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = theme.color.textSecondary, style = typo.labelMd)
    }
}

/**
 * Mahjong board com sizing dinâmico (FIX M01/M02):
 * - Calcula bounding box em "unidades de half-cell"
 * - Determina tileW que faz tudo caber em maxWidth e maxHeight
 * - Centraliza o conjunto
 */
@Composable
private fun MahjongBoard(state: MahjongUiState, onTileTap: (Int) -> Unit) {
    val theme = LocalAppTheme.current
    val active = state.tiles.filter { !it.removed }
    if (active.isEmpty()) return

    // 提示呼吸动画（0..1 往复）：驱动被提示两张牌的红色光晕明暗循环；
    // 「减弱动效」开启时不做动画，仅保留静态 3dp 红描边
    val hintPulse: Float = if (state.hintPair != null && !theme.reduceMotion) {
        val breath = rememberInfiniteTransition(label = "hintBreath")
        val pulse by breath.animateFloat(
            initialValue = 0f,
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

    val minCol = active.minOf { it.col }
    val maxCol = active.maxOf { it.col }
    val minRow = active.minOf { it.row }
    val maxRow = active.maxOf { it.row }
    val maxLayer = active.maxOf { it.layer }
    // Cada tile ocupa 2 half-cells de largura
    val halfCellsWide = (maxCol - minCol) + 2  // +2 porque tile precisa de 2 half-cells
    // Cada linha = 1 tile altura
    val tilesHigh = (maxRow - minRow) + 1
    // Layers superiores se deslocam para a esquerda/cima, logo não ampliam os limites

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        val maxW = maxWidth
        val maxH = maxHeight

        // Resolve tileW para caber em ambas as dimensões
        // Largura usada: (halfCellsWide / 2) * tileW + depthRatio * tileW
        // Altura usada: tilesHigh * tileH + depthRatio * tileH
        // Com tileH = tileW * 1.32 (proporção tradicional)
        val tileAspect = 1.32f
        val depthRatio = 0.1f

        val tilesWideEffective = (halfCellsWide / 2f) + depthRatio
        val tilesHighEffective = tilesHigh + depthRatio

        val tileWByWidth = maxW / tilesWideEffective
        val tileWByHeight = (maxH / tilesHighEffective) / tileAspect
        val tileW = listOf(tileWByWidth, tileWByHeight, 74.dp).minOrNull() ?: 32.dp
        val tileH = tileW * tileAspect
        val tileDepth = tileW * depthRatio
        val layerOffsetX = tileW * 0.08f
        val layerOffsetY = tileH * 0.08f

        // Largura/altura efetivas para centralização
        val boardW = tileW * (halfCellsWide / 2f) + tileDepth
        val boardH = tileH * tilesHigh + tileDepth

        Box(
            modifier = Modifier.size(width = boardW, height = boardH),
        ) {
            active.sortedWith(compareBy({ it.layer }, { it.row }, { it.col })).forEach { tile ->
                val isFree = computeIsFree(tile, active)
                val selected = state.selectedId == tile.id
                val hinted = state.hintPair?.let { it.first == tile.id || it.second == tile.id } == true

                val visualState = when {
                    selected -> TileVisualState.SELECTED
                    hinted -> TileVisualState.HINTED
                    !isFree -> TileVisualState.BLOCKED
                    else -> TileVisualState.FREE
                }

                val scale by animateFloatAsState(
                    targetValue = when (visualState) {
                        TileVisualState.SELECTED -> 1.08f
                        // 提示牌放大到与选中一致，配合红色描边足够醒目
                        TileVisualState.HINTED -> 1.08f
                        else -> 1f
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "tile-scale",
                )

                val x = ((tile.col - minCol).toFloat() / 2f) * tileW.value - tile.layer * layerOffsetX.value
                val y = (tile.row - minRow).toFloat() * tileH.value - tile.layer * layerOffsetY.value

                Box(
                    modifier = Modifier
                        .offset(x.dp, y.dp)
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            // FIX V206: scale a partir do centro, não top-left
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                        }
                        .clickable(enabled = isFree) { onTileTap(tile.id) },
                ) {
                    MahjongTileVisual(
                        face = tile.face,
                        state = visualState,
                        width = tileW,
                        height = tileH,
                        depth = tileDepth,
                        // 呼吸脉动只对被提示的牌生效（非提示牌内部也不会用到）
                        hintPulse = hintPulse,
                    )
                }
            }
        }
    }
}

private fun computeIsFree(tile: MahjongTile, all: List<MahjongTile>): Boolean {
    val above = all.any { t ->
        t.layer == tile.layer + 1 &&
            kotlin.math.abs(t.row - tile.row) <= 1 &&
            kotlin.math.abs(t.col - tile.col) <= 1
    }
    if (above) return false
    val same = all.filter { it.layer == tile.layer && it.id != tile.id }
    val left = same.any { it.row == tile.row && it.col == tile.col - 2 }
    val right = same.any { it.row == tile.row && it.col == tile.col + 2 }
    return !(left && right)
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
