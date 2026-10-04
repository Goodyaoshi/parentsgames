package com.goodyaoshi.parentsgames.feature.games.klotski

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.core.ui.WinOverlay
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiBoard
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiLayout
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiPiece
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiPieceType
import com.goodyaoshi.parentsgames.feature.games.klotski.domain.KlotskiSolver

// 木块配色（仿实体木块，不随主题变色）
private val CaoColor = Color(0xFFC03A2B)
private val CaoBorder = Color(0xFF8E271C)
private val GeneralColor = Color(0xFFD4A24A)
private val GeneralBorder = Color(0xFF9A7226)
private val SoldierColor = Color(0xFFE2C285)
private val SoldierBorder = Color(0xFFA98447)
private val SelectedBorder = Color(0xFF3A2A18)
private val BlockTextColor = Color(0xFF5B3A12)
// 提示高亮：红描边 + 目标格淡红填充（与木块配色区分明显）
private val HintColor = Color(0xFFE53935)
private val HintTargetColor = Color(0xFFE53935).copy(alpha = 0.28f)

@Composable
fun KlotskiScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: KlotskiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    val context = LocalContext.current

    // 无解时文字提示（经典布局理论上不会出现）
    LaunchedEffect(state.hintFailed) {
        if (state.hintFailed) {
            Toast.makeText(context, "暂时没有找到解法", Toast.LENGTH_SHORT).show()
            viewModel.onHintFailureShown()
        }
    }

    val hasProgress = state.moves > 0 && !state.finished
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
        Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
            GameTopBar(
                title = "华容道",
                subtitle = "${state.layout.label} · ${state.moves}步",
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

            // 开局选择：9 个布局一行放不下，用可横向滚动的 LazyRow，按钮保持大号
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = theme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
            ) {
                items(KlotskiLayout.entries, key = { it.id }) { layout ->
                    val isSel = state.layout == layout
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(theme.radii.md))
                            .background(if (isSel) theme.color.primary else theme.color.bgSurfaceMuted)
                            .clickable { viewModel.onLayoutSelected(layout) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            layout.label,
                            color = if (isSel) theme.color.textInverse else theme.color.textPrimary,
                            style = typo.labelMd,
                            maxLines = 1,
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
                Column(modifier = Modifier.fillMaxSize()) {
                    KlotskiBoardView(
                        pieces = state.pieces,
                        selectedPieceId = state.selectedPieceId,
                        hint = state.hint,
                        reduceMotion = theme.reduceMotion,
                        onPieceTap = viewModel::onPieceTap,
                        onCellTap = viewModel::onCellTap,
                    )
                    ExitMarker()
                }
                if (state.finished) {
                    WinOverlay(
                        timeLabel = formatTime(state.elapsedMs),
                        secondaryLabel = "步数",
                        secondaryValue = "${state.moves}",
                        onPlayAgain = viewModel::onRestart,
                        onHome = onHome,
                    )
                }
            }

            // 按钮行：提示 / 悔棋 / 重来 / 玩法
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = theme.spacing.md, vertical = theme.spacing.sm),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    label = if (state.hintLoading) "…" else "提示",
                    icon = Icons.Outlined.Lightbulb,
                    enabled = !state.hintLoading && !state.finished,
                    onClick = viewModel::onHint,
                )
                ActionButton(label = "悔棋", icon = Icons.AutoMirrored.Outlined.Undo, onClick = viewModel::onUndo)
                ActionButton(label = "重来", icon = Icons.Outlined.Refresh, onClick = viewModel::onRestart)
                HelpButton(steps = TutorialContent.klotski)
            }
        }
        TutorialFirstTime(name = "klotski", steps = TutorialContent.klotski)
    }
}

@Composable
private fun KlotskiBoardView(
    pieces: List<KlotskiPiece>,
    selectedPieceId: Int?,
    hint: KlotskiSolver.Hint?,
    reduceMotion: Boolean,
    onPieceTap: (Int) -> Unit,
    onCellTap: (Int, Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(KlotskiBoard.COLS.toFloat() / KlotskiBoard.ROWS.toFloat()),
    ) {
        val cellW = maxWidth / KlotskiBoard.COLS
        val cellH = maxHeight / KlotskiBoard.ROWS
        // 提示目标区：要滑到的落点（整块区域，淡红填充）
        val hintTarget = hint?.let { h -> pieces.firstOrNull { it.id == h.pieceId }?.let { h to it } }

        // 底格：空格可点，底行中间两格是出口；提示目标格淡红填充
        for (row in 0 until KlotskiBoard.ROWS) {
            for (col in 0 until KlotskiBoard.COLS) {
                val isExitCell = row == KlotskiBoard.ROWS - 1 &&
                    col >= KlotskiBoard.EXIT_X && col < KlotskiBoard.EXIT_X + 2
                val isHintTarget = hintTarget?.let { (h, p) ->
                    col >= h.toX && col < h.toX + p.type.w && row >= h.toY && row < h.toY + p.type.h
                } == true
                Box(
                    modifier = Modifier
                        .offset(x = cellW * col, y = cellH * row)
                        .size(width = cellW, height = cellH)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isHintTarget -> HintTargetColor
                                isExitCell -> theme.color.accentGold.copy(alpha = 0.30f)
                                else -> theme.color.bgSurfaceMuted
                            }
                        )
                        .clickable { onCellTap(col, row) },
                )
            }
        }

        // 木块盖在底格上面
        pieces.forEach { piece ->
            KlotskiBlock(
                piece = piece,
                selected = piece.id == selectedPieceId,
                hinted = piece.id == hint?.pieceId,
                reduceMotion = reduceMotion,
                modifier = Modifier
                    .offset(
                        x = cellW * piece.x + 2.dp,
                        y = cellH * piece.y + 2.dp,
                    )
                    .size(
                        width = cellW * piece.type.w - 4.dp,
                        height = cellH * piece.type.h - 4.dp,
                    )
                    .clickable { onPieceTap(piece.id) },
            )
        }
    }
}

@Composable
private fun KlotskiBlock(
    piece: KlotskiPiece,
    selected: Boolean,
    hinted: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val typo = LocalAppTypography.current
    val bg = when (piece.type) {
        KlotskiPieceType.CAO -> CaoColor
        KlotskiPieceType.V_GENERAL, KlotskiPieceType.H_GENERAL -> GeneralColor
        KlotskiPieceType.SOLDIER -> SoldierColor
    }
    // 提示的块：红描边 + 呼吸动画（reduceMotion 时静止高亮）
    val breathAlpha = if (hinted && !reduceMotion) {
        rememberInfiniteTransition(label = "hint-breath").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "hint-breath-alpha",
        ).value
    } else {
        1f
    }
    val borderColor = when {
        hinted -> HintColor.copy(alpha = breathAlpha)
        selected -> SelectedBorder
        else -> when (piece.type) {
            KlotskiPieceType.CAO -> CaoBorder
            KlotskiPieceType.V_GENERAL, KlotskiPieceType.H_GENERAL -> GeneralBorder
            KlotskiPieceType.SOLDIER -> SoldierBorder
        }
    }
    val textStyle = when (piece.type) {
        KlotskiPieceType.CAO -> typo.displayMd
        KlotskiPieceType.SOLDIER -> typo.titleMd
        KlotskiPieceType.V_GENERAL, KlotskiPieceType.H_GENERAL -> typo.titleLg
    }
    val textColor = if (piece.type == KlotskiPieceType.CAO) Color.White else BlockTextColor
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .shadow(2.dp, shape)
            .clip(shape)
            .background(bg)
            .border(
                width = if (selected || hinted) 3.dp else 2.dp,
                color = borderColor,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            piece.type.label,
            color = textColor,
            style = textStyle,
            fontWeight = FontWeight.W700,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExitMarker() {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(6.dp))
                .background(theme.color.accentGold.copy(alpha = 0.35f))
                .border(1.5.dp, theme.color.accentGold, RoundedCornerShape(6.dp))
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "▲ 出口 ▲",
                color = theme.color.textPrimary,
                style = typo.labelMd,
                fontWeight = FontWeight.W700,
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = theme.spacing.md, vertical = theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) theme.color.primaryPressed else theme.color.textDisabled,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (enabled) theme.color.textSecondary else theme.color.textDisabled,
            style = typo.labelMd,
        )
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
