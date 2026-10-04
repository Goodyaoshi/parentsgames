package com.goodyaoshi.parentsgames.feature.games.colorsort

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.goodyaoshi.parentsgames.core.ui.WinOverlay
import com.goodyaoshi.parentsgames.feature.games.colorsort.domain.Tube
import com.goodyaoshi.parentsgames.feature.games.colorsort.ui.GlassTube

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColorSortScreen(
    stage: Int,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onNextStage: (Int) -> Unit,
    viewModel: ColorSortViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    LaunchedEffect(stage) { viewModel.initialize(stage) }

    val hasProgress = state.moves > 0 && !state.completed
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        GameTopBar(
            title = "颜色分类",
            subtitle = "第 $stage / 30 关 · ${state.moves} 步",
            onBackClick = onBack,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(theme.spacing.xxs)
                .clip(RoundedCornerShape(theme.radii.lg))
                .background(theme.color.bgTubeWell),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(theme.spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                // Tamanho dos tubos adaptado ao espaço: mantém proporção 52:190,
                // cabe em ~2 linhas (3 para muitas provetas) e cresce até 66dp de largura
                val tubeCount = state.tubes.size.coerceAtLeast(1)
                val targetRows = if (tubeCount <= 12) 2 else 3
                val perRow = ((tubeCount + targetRows - 1) / targetRows).coerceAtLeast(1)
                val gapH = 6.dp
                val gapV = 10.dp
                val aspect = 190f / 52f
                val wByWidth = (maxWidth - gapH * (perRow - 1)) / perRow
                val wByHeight = ((maxHeight - gapV * (targetRows - 1)) / targetRows) / aspect
                val tubeW = minOf(wByWidth, wByHeight, 66.dp)
                val tubeH = tubeW * aspect
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(gapH, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(gapV, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.tubes.forEachIndexed { idx, tube ->
                        TubeColumn(
                            tube = tube,
                            tubeWidth = tubeW,
                            tubeHeight = tubeH,
                            selected = state.selectedTube == idx,
                            isPourSource = state.pour?.from == idx,
                            isPourDest = state.pour?.to == idx,
                            pourPhase = if (state.pour?.from == idx) state.pour?.phase else null,
                            isError = state.errorTube == idx,
                            onClick = { viewModel.onTubeTap(idx) },
                        )
                    }
                }
            }
            if (state.completed) {
                WinOverlay(
                    timeLabel = formatTime(state.elapsedMs),
                    secondaryLabel = "步数",
                    secondaryValue = state.moves.toString(),
                    onPlayAgain = { onNextStage(stage + 1) },
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
            ActionButton(label = "撤销", icon = Icons.Outlined.Undo, onClick = viewModel::onUndo)
            ActionButton(label = "加试管", icon = Icons.Outlined.Add, onClick = viewModel::onAddTube)
            ActionButton(label = "重新开始", icon = Icons.Outlined.RestartAlt, onClick = viewModel::onRestart)
            HelpButton(steps = TutorialContent.colorSort)
        }
    }
    TutorialFirstTime(name = "colorsort", steps = TutorialContent.colorSort)
    }
}

@Composable
private fun TubeColumn(
    tube: Tube,
    tubeWidth: androidx.compose.ui.unit.Dp,
    tubeHeight: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    isPourSource: Boolean,
    isPourDest: Boolean,
    pourPhase: PourPhase?,
    isError: Boolean,
    onClick: () -> Unit,
) {
    // Lift quando selecionado OU quando está derramando
    val raised = selected || isPourSource
    val lift by animateDpAsState(
        targetValue = if (raised) (-18).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tube-lift",
    )

    // Tilt quando derramando (rotaciona em direção ao destino)
    val tiltDeg by animateFloatAsState(
        targetValue = when (pourPhase) {
            PourPhase.TILT, PourPhase.POURING -> 22f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 200),
        label = "tube-tilt",
    )

    // Shake quando erro
    val shakeX by animateFloatAsState(
        targetValue = if (isError) 10f else 0f,
        animationSpec = if (isError)
            spring(dampingRatio = 0.2f, stiffness = Spring.StiffnessHigh)
        else tween(durationMillis = 60),
        label = "tube-shake",
    )

    Box(
        modifier = Modifier
            .offset(y = lift)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                rotationZ = tiltDeg
                translationX = shakeX
                // FIX V401: pivô perto da boca direita (onde derrama)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.20f)
            },
        ) {
            GlassTube(tube = tube, width = tubeWidth, height = tubeHeight, selected = selected)
        }
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
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.sm, vertical = theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = theme.color.primaryPressed, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = theme.color.textSecondary, style = typo.labelMd)
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
