package com.goodyaoshi.parentsgames.feature.games.frogger

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.feature.games.frogger.domain.COLS
import com.goodyaoshi.parentsgames.feature.games.frogger.domain.FrogStatus
import com.goodyaoshi.parentsgames.feature.games.frogger.domain.LaneType
import com.goodyaoshi.parentsgames.feature.games.frogger.domain.ROWS

@Composable
fun FroggerScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: FroggerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    // FIX F03: pausa game loop quando app vai pro background
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onLifecyclePause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onLifecycleResume() }

    val hasProgress = state.score > 0 && state.status == FrogStatus.ALIVE
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        GameTopBar(
            title = "青蛙过马路",
            subtitle = "${state.goalsReached}/5 次过河",
            onBackClick = onBack,
            rightContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // FIX V806: 3 corações visíveis em vez de "3 vidas" texto
                    repeat(3) { i ->
                        Text(
                            if (i < state.lives) "♥" else "♡",
                            color = if (i < state.lives) Color(0xFFE24B4A) else theme.color.textDisabled,
                            modifier = Modifier.padding(horizontal = 1.dp),
                        )
                    }
                    Spacer(Modifier.size(theme.spacing.sm))
                    Text(
                        "%,d".format(state.score),
                        color = theme.color.textPrimary,
                        style = typo.numericMd,
                        modifier = Modifier.padding(end = theme.spacing.md),
                    )
                }
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.xxs)
                .aspectRatio(COLS.toFloat() / ROWS.toFloat())
                .clip(RoundedCornerShape(theme.radii.md)),
        ) {
            FroggerCanvas(state = state)
            if (state.status != FrogStatus.ALIVE) {
                EndOverlay(
                    won = state.status == FrogStatus.WON,
                    score = state.score,
                    onRestart = viewModel::restart,
                    onHome = onHome,
                )
            }
        }

        Spacer(Modifier.height(theme.spacing.xs))
        // D-pad
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = theme.spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DPadButton(Icons.Outlined.KeyboardArrowUp, viewModel::moveUp)
                Spacer(Modifier.height(4.dp))
                Row {
                    DPadButton(Icons.Outlined.KeyboardArrowLeft, viewModel::moveLeft)
                    Spacer(Modifier.size(56.dp))
                    DPadButton(Icons.Outlined.KeyboardArrowRight, viewModel::moveRight)
                }
                Spacer(Modifier.height(4.dp))
                DPadButton(Icons.Outlined.KeyboardArrowDown, viewModel::moveDown)
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                HelpButton(steps = TutorialContent.frogger)
            }
        }
        Spacer(Modifier.height(theme.spacing.xs))
    }
    TutorialFirstTime(name = "frogger", steps = TutorialContent.frogger)
    }
}

@Composable
private fun DPadButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(theme.color.primary.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = theme.color.textInverse, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun FroggerCanvas(state: FroggerUiState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cellW = w / COLS
        val cellH = h / ROWS

        // Fundo por lane
        for (lane in state.lanes) {
            val color = when (lane.type) {
                LaneType.SAFE -> if (lane.row == 0) Color(0xFF7A9B7E) else Color(0xFFE6DAC6)
                LaneType.CAR -> Color(0xFF2C2A26)
                LaneType.WATER -> Color(0xFF4A8AB8)
            }
            drawRect(
                color,
                Offset(0f, lane.row * cellH),
                Size(w, cellH),
            )
            // Linhas da rua
            if (lane.type == LaneType.CAR) {
                val dashY = lane.row * cellH + cellH * 0.5f
                var x = 0f
                while (x < w) {
                    drawRect(
                        Color.White,
                        Offset(x, dashY - 1.5f),
                        Size(cellW * 0.4f, 3f),
                    )
                    x += cellW
                }
            }
        }

        // Obstáculos
        for ((row, items) in state.obstaclePositions) {
            val lane = state.lanes[row]
            val color = if (lane.type == LaneType.CAR) Color(0xFFE24B4A) else Color(0xFF8B5A2B)
            for ((startCol, len) in items) {
                val x = startCol * cellW
                drawRect(
                    color,
                    Offset(x + 2f, row * cellH + 4f),
                    Size(cellW * len - 4f, cellH - 8f),
                )
                if (lane.type == LaneType.WATER) {
                    // Detalhe do tronco
                    drawLine(
                        Color(0xFF6B3F1F),
                        Offset(x + 4f, row * cellH + cellH / 2f),
                        Offset(x + cellW * len - 4f, row * cellH + cellH / 2f),
                        strokeWidth = 2f,
                    )
                }
            }
        }

        // FIX V801: goal pads (5 lily pads na row 0)
        val padW = cellW * (COLS.toFloat() / 5f) * 0.8f
        for (i in 0 until 5) {
            val px = (i + 0.5f) * (cellW * COLS.toFloat() / 5f)
            val py = cellH * 0.5f
            val filled = state.goalSlots.getOrNull(i) == true
            drawOval(
                color = if (filled) Color(0xFF1E6B3C) else Color(0xFF4F6A53).copy(alpha = 0.4f),
                topLeft = Offset(px - padW / 2f, py - cellH * 0.35f),
                size = androidx.compose.ui.geometry.Size(padW, cellH * 0.7f),
            )
            if (filled) {
                drawCircle(Color(0xFFD4A24A), radius = cellH * 0.15f, center = Offset(px, py))
            }
        }

        // Sapo com rotação visual baseada na última direção (FIX V802)
        val fx = state.frog.colFloat * cellW + cellW / 2f
        val fy = state.frog.row * cellH + cellH / 2f
        val r = minOf(cellW, cellH) * 0.36f

        // Rotaciona sob o centro do sapo
        rotate(degrees = state.facingDeg, pivot = Offset(fx, fy)) {
            drawCircle(Color(0xFF1E6B3C), radius = r, center = Offset(fx, fy))
            drawCircle(Color(0xFF3E8C5B), radius = r * 0.7f, center = Offset(fx, fy))
            // Olhinhos (sempre "para frente" considerando rotation)
            drawCircle(Color.White, radius = r * 0.18f, center = Offset(fx - r * 0.35f, fy - r * 0.35f))
            drawCircle(Color.White, radius = r * 0.18f, center = Offset(fx + r * 0.35f, fy - r * 0.35f))
            drawCircle(Color.Black, radius = r * 0.08f, center = Offset(fx - r * 0.35f, fy - r * 0.35f))
            drawCircle(Color.Black, radius = r * 0.08f, center = Offset(fx + r * 0.35f, fy - r * 0.35f))
        }
    }
}

@Composable
private fun EndOverlay(won: Boolean, score: Long, onRestart: () -> Unit, onHome: () -> Unit) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
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
            Text(if (won) "你赢了！" else "就差一点！", color = theme.color.textPrimary, style = typo.displayMd)
            Spacer(Modifier.height(theme.spacing.sm))
            Text("得分：%,d".format(score), color = theme.color.textSecondary, style = typo.bodyLg)
            Spacer(Modifier.height(theme.spacing.lg))
            com.goodyaoshi.parentsgames.core.ui.PrimaryButton(
                text = "再玩一局",
                onClick = onRestart,
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
