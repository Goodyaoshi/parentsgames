package com.goodyaoshi.parentsgames.feature.games.tetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.VerticalAlignBottom
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.feature.games.tetris.domain.BOARD_COLS
import com.goodyaoshi.parentsgames.feature.games.tetris.domain.BOARD_ROWS
import com.goodyaoshi.parentsgames.feature.games.tetris.domain.Tetromino

@Composable
fun TetrisScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: TetrisViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val hasProgress = state.score > 0 && !state.gameOver
    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        GameTopBar(
            title = "俄罗斯方块",
            subtitle = "等级 ${state.level} · ${state.lines} 行",
            onBackClick = onBack,
            rightContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%,d".format(state.score),
                        color = theme.color.textPrimary,
                        style = typo.numericLg,
                        modifier = Modifier.padding(end = theme.spacing.sm),
                    )
                    // FIX X03: pause acessível
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(theme.color.bgSurfaceMuted)
                            .clickable { viewModel.togglePause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = if (state.paused) "继续" else "暂停",
                            tint = theme.color.primaryPressed,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.size(theme.spacing.xs))
                }
            },
        )

        // Sub-bar com próxima peça
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.md, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("下一个：", color = theme.color.textSecondary, style = typo.labelMd)
            Spacer(Modifier.size(theme.spacing.sm))
            NextPiecePreview(piece = state.next)
            Spacer(Modifier.weight(1f))
            HelpButton(steps = TutorialContent.tetris)
        }

        // FIX X01: usar BoxWithConstraints para garantir que board e controles caibam
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.xxs),
            contentAlignment = Alignment.Center,
        ) {
            // Espaço disponível: maxHeight; reservar ~150dp para controles abaixo (já que controles
            // ficam fora desse Box). Aqui calcula a maior largura possível que mantém aspect 1:2.
            val maxBoardW = maxWidth
            val maxBoardH = maxHeight
            val targetAspect = BOARD_COLS.toFloat() / BOARD_ROWS.toFloat()  // 0.5
            val byWidth = maxBoardW
            val byHeight = maxBoardH * targetAspect
            val finalW = if (byWidth < byHeight) byWidth else byHeight
            val finalH = finalW / targetAspect

            Box(
                modifier = Modifier
                    .size(width = finalW, height = finalH)
                    .clip(RoundedCornerShape(theme.radii.md))
                    .background(Color(0xFF1C1A17))
                    // FIX V701: handler único — distingue tap (rotate) de swipe (mover/drop) por distância
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { viewModel.hardDrop() },
                            onTap = { viewModel.rotate() },
                        )
                    }
                    .pointerInput(Unit) {
                        var totalX = 0f
                        var totalY = 0f
                        var dragged = false
                        detectDragGestures(
                            onDragStart = { totalX = 0f; totalY = 0f; dragged = false },
                            onDragEnd = {
                                if (!dragged) return@detectDragGestures
                                val threshold = 40f
                                if (abs(totalX) > abs(totalY) && abs(totalX) > threshold) {
                                    if (totalX > 0) viewModel.moveRight() else viewModel.moveLeft()
                                } else if (totalY > threshold) {
                                    viewModel.softDrop()
                                }
                                // swipe up sem rotate (evita conflito com tap)
                            },
                            onDrag = { _, dragAmount ->
                                totalX += dragAmount.x
                                totalY += dragAmount.y
                                if (abs(totalX) + abs(totalY) > 10f) dragged = true
                            },
                        )
                    },
            ) {
                TetrisBoard(state = state)
                if (state.gameOver) {
                    GameOverOverlay(score = state.score, onRestart = viewModel::restart, onHome = onHome)
                }
                if (state.paused && !state.gameOver) {
                    PauseScrim(onResume = viewModel::togglePause)
                }
            }
        }

        // Controles SEMPRE visíveis (nunca saem da tela — fora do BoxWithConstraints)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.sm, vertical = theme.spacing.xxs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(icon = Icons.Outlined.KeyboardArrowLeft, onClick = viewModel::moveLeft, label = "←")
            ControlButton(icon = Icons.Outlined.RotateRight, onClick = viewModel::rotate, label = "旋转")
            ControlButton(icon = Icons.Outlined.KeyboardArrowDown, onClick = viewModel::softDrop, label = "下降")
            ControlButton(icon = Icons.Outlined.VerticalAlignBottom, onClick = viewModel::hardDrop, label = "落底")
            ControlButton(icon = Icons.Outlined.KeyboardArrowRight, onClick = viewModel::moveRight, label = "→")
        }
    }
    TutorialFirstTime(name = "tetris", steps = TutorialContent.tetris)
    }
}

@Composable
private fun PauseScrim(onResume: () -> Unit) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onResume),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("已暂停", color = Color.White, style = typo.displayMd)
            Spacer(Modifier.height(theme.spacing.sm))
            Text("点击屏幕继续", color = Color.White.copy(alpha = 0.85f), style = typo.bodyMd)
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(theme.radii.md))
            .background(theme.color.bgSurfaceMuted)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = theme.color.primaryPressed, modifier = Modifier.size(32.dp))
        Text(label, color = theme.color.textSecondary, style = typo.labelMd)
    }
}

@Composable
private fun NextPiecePreview(piece: Tetromino?) {
    if (piece == null) return
    Canvas(modifier = Modifier.size(width = 80.dp, height = 28.dp)) {
        val color = Color(piece.color)
        val cellSize = (size.height / 2f)
        val cells = piece.blocks[0]
        val minR = cells.minOf { it.first }
        val minC = cells.minOf { it.second }
        for ((r, c) in cells) {
            drawRect(
                color = color,
                topLeft = Offset((c - minC) * cellSize, (r - minR) * cellSize),
                size = Size(cellSize - 1f, cellSize - 1f),
            )
        }
    }
}

@Composable
private fun TetrisBoard(state: TetrisUiState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cellW = w / BOARD_COLS
        val cellH = h / BOARD_ROWS

        val gridColor = Color(0xFF2C2A26)
        for (i in 0..BOARD_ROWS) {
            drawLine(gridColor, Offset(0f, i * cellH), Offset(w, i * cellH), strokeWidth = 0.5f)
        }
        for (j in 0..BOARD_COLS) {
            drawLine(gridColor, Offset(j * cellW, 0f), Offset(j * cellW, h), strokeWidth = 0.5f)
        }

        for (r in 0 until BOARD_ROWS) for (c in 0 until BOARD_COLS) {
            val ord = state.grid.getOrNull(r)?.getOrNull(c) ?: -1
            if (ord >= 0) {
                val color = Color(Tetromino.entries[ord].color)
                drawCell(color, c * cellW, r * cellH, cellW, cellH)
            }
        }

        // FIX X06: linhas sendo limpas piscam em branco
        for (clearRow in state.clearingRows) {
            drawRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(0f, clearRow * cellH),
                size = Size(w, cellH),
            )
        }

        val cur = state.current
        if (cur != null) {
            // Ghost piece (FIX X08: só mostra se distância >= 2 linhas)
            var ghostRow = cur.row
            while (true) {
                val ghostCells = cur.copy(row = ghostRow + 1).cells()
                val valid = ghostCells.all { (r, c) ->
                    r in 0 until BOARD_ROWS && c in 0 until BOARD_COLS &&
                        (state.grid.getOrNull(r)?.getOrNull(c) ?: -1) < 0
                }
                if (!valid) break
                ghostRow++
            }
            if (ghostRow - cur.row >= 2) {
                val ghostColor = Color(cur.type.color).copy(alpha = 0.22f)
                val ghostCells = cur.copy(row = ghostRow).cells()
                for ((r, c) in ghostCells) {
                    if (r in 0 until BOARD_ROWS && c in 0 until BOARD_COLS) {
                        drawCell(ghostColor, c * cellW, r * cellH, cellW, cellH, ghost = true)
                    }
                }
            }
            val color = Color(cur.type.color)
            for ((r, c) in cur.cells()) {
                if (r in 0 until BOARD_ROWS && c in 0 until BOARD_COLS) {
                    drawCell(color, c * cellW, r * cellH, cellW, cellH)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCell(
    color: Color,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    ghost: Boolean = false,
) {
    if (ghost) {
        drawRect(color, Offset(x + 1f, y + 1f), Size(w - 2f, h - 2f))
        return
    }
    drawRect(color, Offset(x + 1f, y + 1f), Size(w - 2f, h - 2f))
    drawRect(
        color.copy(alpha = 0.45f),
        Offset(x + 1f, y + 1f),
        Size(w - 2f, h * 0.18f),
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.22f),
        topLeft = Offset(x + 1f, y + h * 0.82f),
        size = Size(w - 2f, h * 0.18f),
    )
}

@Composable
private fun GameOverOverlay(score: Long, onRestart: () -> Unit, onHome: () -> Unit) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 320.dp)
                .clip(RoundedCornerShape(theme.radii.xl))
                .background(theme.color.bgSurface)
                .padding(theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("游戏结束", color = theme.color.textPrimary, style = typo.displayMd)
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
