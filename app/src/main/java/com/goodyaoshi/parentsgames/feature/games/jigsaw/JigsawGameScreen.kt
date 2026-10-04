package com.goodyaoshi.parentsgames.feature.games.jigsaw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodyaoshi.parentsgames.core.theme.AppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.GameBackGuard
import com.goodyaoshi.parentsgames.core.ui.GameTopBar
import com.goodyaoshi.parentsgames.core.ui.HelpButton
import com.goodyaoshi.parentsgames.core.ui.TutorialContent
import com.goodyaoshi.parentsgames.core.ui.TutorialFirstTime
import com.goodyaoshi.parentsgames.core.ui.WinOverlay
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.JigsawBoard
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.PieceState

@Composable
fun JigsawGameScreen(
    photoId: Long,
    pieceCount: Int,
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: JigsawViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    // 参考大图弹层开关：平时收起，点左下角「参考图」胶囊才看，不再常驻遮挡
    var showReference by remember { mutableStateOf(false) }

    // 有进度（已至少拼入一块）时，返回需要二次确认，防误触丢进度
    val hasProgress = (state as? JigsawUiState.Playing)?.let { p ->
        !p.completed && p.states.any { it.placed }
    } ?: false

    // FIX J13: recycle bitmaps ao sair da tela para liberar memória
    DisposableEffect(Unit) {
        onDispose {
            val playing = state as? JigsawUiState.Playing
            playing?.board?.pieceBitmaps?.values?.forEach { bmp ->
                runCatching { bmp.asAndroidBitmap().recycle() }
            }
        }
    }

    GameBackGuard(hasProgress = hasProgress, onConfirmedExit = onBack) {
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        GameTopBar(
            title = "拼图",
            subtitle = when (val s = state) {
                is JigsawUiState.Playing ->
                    "${s.states.count { it.placed }} / ${s.board.pieces.size}"
                else -> null
            },
            onBackClick = onBack,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.color.bgTubeWell)
                    .onSizeChanged { size ->
                        viewModel.initialize(
                            photoId = photoId,
                            pieceCount = pieceCount,
                            viewportW = size.width,
                            viewportH = size.height,
                        )
                    },
            ) {
                when (val s = state) {
                    is JigsawUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Text(
                                "正在准备拼图…",
                                color = theme.color.textSecondary,
                                style = typo.bodyLg,
                            )
                        }
                    }
                    is JigsawUiState.Playing -> {
                        InteractiveJigsawCanvas(
                            board = s.board,
                            states = s.states,
                            justSnappedIds = s.justSnappedPieceIds,
                            reduceMotion = theme.reduceMotion,
                            onDragPiece = { id, dx, dy -> viewModel.onPieceDrag(id, dx, dy) },
                            onReleasePiece = { id -> viewModel.onPieceReleased(id) },
                        )
                        if (s.completed) {
                            WinOverlay(
                                timeLabel = formatTime(s.elapsedMs),
                                secondaryLabel = "块数",
                                secondaryValue = s.board.pieces.size.toString(),
                                onPlayAgain = onBack,
                                onHome = onHome,
                            )
                        }
                    }
                    is JigsawUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Text(
                                s.message,
                                color = theme.color.feedbackError,
                                style = typo.bodyLg,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(theme.spacing.md),
            ) {
                HelpButton(steps = TutorialContent.jigsaw)
            }
            // 左下角「参考图」胶囊：点击才看大图，平时不遮挡拼块
            val playing = state as? JigsawUiState.Playing
            if (playing != null && !playing.completed) {
                JigsawReferencePillButton(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(theme.spacing.md),
                    onClick = { showReference = true },
                )
            }
            if (showReference && playing != null) {
                JigsawReferenceDialog(
                    image = playing.board.referenceImage,
                    onDismiss = { showReference = false },
                )
            }
        }
    }
    TutorialFirstTime(name = "jigsaw", steps = TutorialContent.jigsaw)
    }
}

/**
 * 左下角「参考图」胶囊按钮：
 * - 半透明白底小胶囊，平时不展示大图、不遮挡棋盘上的拼块；
 * - 与右下角「玩法」按钮对称；点击事件自身消费，不会透传给棋盘手势。
 */
@Composable
private fun JigsawReferencePillButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    val pillShape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(pillShape)
            .background(Color.White.copy(alpha = 0.8f))
            .border(1.dp, theme.color.borderStrong, pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.sm, vertical = theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Visibility,
            contentDescription = null,
            tint = theme.color.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(theme.spacing.xxs))
        Text("参考图", color = theme.color.textPrimary, style = typo.labelMd)
    }
}

/**
 * 参考大图全屏弹层：
 * - 用独立窗口 Dialog 承载（compose Box 内嵌全屏遮罩容易被手势层约束压扁）；
 * - 黑 60% 遮罩 + 居中约 70% 屏宽大图，点图任意处或点「关闭」回到对局。
 */
@Composable
private fun JigsawReferenceDialog(
    image: ImageBitmap,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val theme = LocalAppTheme.current
        val typo = LocalAppTypography.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                JigsawReferenceImage(
                    image = image,
                    theme = theme,
                    onClick = onDismiss,
                )
                Spacer(Modifier.height(theme.spacing.lg))
                val pillShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(theme.color.bgSurface)
                        .border(1.dp, theme.color.borderStrong, pillShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = theme.spacing.xl, vertical = theme.spacing.sm),
                ) {
                    Text("关闭", color = theme.color.textPrimary, style = typo.labelLg)
                }
            }
        }
    }
}

/**
 * 参考图卡片：圆角 + 描边，点击任意处关闭。
 */
@Composable
private fun JigsawReferenceImage(
    image: ImageBitmap,
    theme: AppTheme,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(theme.radii.lg)
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .aspectRatio(image.width.toFloat() / image.height)
            .clip(shape)
            .border(2.dp, theme.color.borderStrong, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = "参考图",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 中央拼板 + 简化手势的拼图画布（对老人最简单的「插槽拼图」）：
 * - 屏幕中央固定一块拼板，按原图长宽比，占宽约 70%，板上画出每个碎片的目标插槽轮廓（淡色格子）；
 * - 手势只有一种：单指按住碎片拖动；松手时靠近正确插槽就自动吸附入位并锁定；
 * - 已入位的碎片盖住自己的插槽（完成一片亮一片），插槽轮廓随之消失；
 * - 没有旋转、没有双指缩放——碎片永远正放，拖不出屏幕。
 */
@Composable
private fun InteractiveJigsawCanvas(
    board: JigsawBoard,
    states: List<PieceState>,
    justSnappedIds: Set<Int>,
    reduceMotion: Boolean,
    onDragPiece: (id: Int, dx: Float, dy: Float) -> Unit,
    onReleasePiece: (id: Int) -> Unit,
) {
    val theme = LocalAppTheme.current

    // 当前拖动的碎片 id
    var dragId by remember { mutableStateOf<Int?>(null) }

    // Always lê o estado mais recente nos handlers de gesto
    val statesState = rememberUpdatedState(states)
    val boardState = rememberUpdatedState(board)

    // Pulse glow para peças recém-encaixadas（尊重 reduceMotion：直接跳过动画）
    val snapPulse = remember { Animatable(0f) }
    LaunchedEffect(justSnappedIds.hashCode()) {
        if (justSnappedIds.isNotEmpty()) {
            if (reduceMotion) {
                snapPulse.snapTo(0f)
            } else {
                snapPulse.snapTo(1f)
                snapPulse.animateTo(0f, animationSpec = tween(durationMillis = 350))
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(board.seed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    // Hit test no espaço local do canvas；已入位的碎片不可再拖
                    val hitId = hitTest(statesState.value, boardState.value, down.position)
                    dragId = hitId
                    var currentId = hitId
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            // 手指抬起 → 尝试吸附入位
                            currentId?.let { onReleasePiece(it) }
                            dragId = null
                            break
                        }
                        val delta = change.positionChange()
                        if (currentId != null && delta != Offset.Zero) {
                            onDragPiece(currentId!!, delta.x, delta.y)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        drawBoardWithSlots(board, states, theme)

        val byId = states.associateBy { it.pieceId }
        // 绘制顺序：已入位(底层) → 散落碎片 → 拖动中的碎片(最上层)
        val orderedPieces = board.pieces.sortedBy { piece ->
            val s = byId[piece.id]
            when {
                s == null -> -1
                s.placed -> 0
                piece.id == dragId -> 2
                else -> 1
            }
        }

        for (piece in orderedPieces) {
            val s = byId[piece.id] ?: continue
            val bmp = board.pieceBitmaps[piece.id] ?: continue
            val isDragging = piece.id == dragId
            val isJustSnapped = piece.id in justSnappedIds

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                nativeCanvas.save()

                if (isJustSnapped && snapPulse.value > 0f) {
                    // 入位轻微动画：轻微放大回弹 + 金色光晕
                    val pulse = snapPulse.value
                    val cx = s.xPx + bmp.width / 2f
                    val cy = s.yPx + bmp.height / 2f
                    val settle = 1f + 0.06f * pulse
                    nativeCanvas.scale(settle, settle, cx, cy)
                    val glowPaint = android.graphics.Paint().apply {
                        color = 0xFFD4A24A.toInt()
                        alpha = (pulse * 130).toInt().coerceIn(0, 255)
                        maskFilter = android.graphics.BlurMaskFilter(
                            10f,
                            android.graphics.BlurMaskFilter.Blur.OUTER,
                        )
                    }
                    nativeCanvas.drawBitmap(
                        bmp.asAndroidBitmap().extractAlpha(),
                        s.xPx,
                        s.yPx,
                        glowPaint,
                    )
                }

                if (isDragging) {
                    val shadowPaint = android.graphics.Paint().apply {
                        color = 0x66000000.toInt()
                        maskFilter = android.graphics.BlurMaskFilter(
                            14f,
                            android.graphics.BlurMaskFilter.Blur.NORMAL,
                        )
                    }
                    nativeCanvas.drawBitmap(
                        bmp.asAndroidBitmap().extractAlpha(),
                        s.xPx + 5f,
                        s.yPx + 10f,
                        shadowPaint,
                    )
                }

                nativeCanvas.drawBitmap(
                    bmp.asAndroidBitmap(),
                    s.xPx,
                    s.yPx,
                    null,
                )

                nativeCanvas.restore()
            }
        }
    }
}

/**
 * 画中央拼板：白色半透明圆角面板 + 描边，再画每个未完成碎片的目标插槽
 * （淡色描边格子）；碎片入位后盖住自己的插槽，轮廓即消失（完成一片亮一片）。
 */
private fun DrawScope.drawBoardWithSlots(
    board: JigsawBoard,
    states: List<PieceState>,
    theme: AppTheme,
) {
    val cell = board.cellSizePx.toFloat()
    val boardW = board.cols * cell
    val boardH = board.rows * cell
    val inset = 10f
    val panelLeft = board.boardLeftPx - inset
    val panelTop = board.boardTopPx - inset
    val panelSize = Size(boardW + inset * 2, boardH + inset * 2)
    val corner = CornerRadius(14f, 14f)

    drawRoundRect(
        color = theme.color.bgSurface.copy(alpha = 0.72f),
        topLeft = Offset(panelLeft, panelTop),
        size = panelSize,
        cornerRadius = corner,
    )
    drawRoundRect(
        color = theme.color.borderStrong,
        topLeft = Offset(panelLeft, panelTop),
        size = panelSize,
        cornerRadius = corner,
        style = Stroke(width = 3f),
    )

    val placedIds = states.filter { it.placed }.map { it.pieceId }.toSet()
    for (piece in board.pieces) {
        if (piece.id in placedIds) continue
        val sx = board.boardLeftPx + piece.gridCol * cell
        val sy = board.boardTopPx + piece.gridRow * cell
        drawRect(
            color = theme.color.bgSurfaceMuted.copy(alpha = 0.5f),
            topLeft = Offset(sx, sy),
            size = Size(cell, cell),
        )
        drawRect(
            color = theme.color.borderStrong.copy(alpha = 0.55f),
            topLeft = Offset(sx, sy),
            size = Size(cell, cell),
            style = Stroke(width = 2f),
        )
    }
}

/**
 * Hit-test pelo path real (alpha do bitmap), não bbox；已入位的碎片不参与拾取。
 */
private fun hitTest(states: List<PieceState>, board: JigsawBoard, offset: Offset): Int? {
    val size = board.pieceSizePx
    for (s in states.asReversed()) {
        if (s.placed) continue
        val localX = (offset.x - s.xPx).toInt()
        val localY = (offset.y - s.yPx).toInt()
        if (localX !in 0 until size || localY !in 0 until size) continue
        val bmp = board.pieceBitmaps[s.pieceId]?.asAndroidBitmap() ?: continue
        if (localX >= bmp.width || localY >= bmp.height) continue
        val pixel = runCatching { bmp.getPixel(localX, localY) }.getOrNull() ?: continue
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha > 32) return s.pieceId
    }
    return null
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
