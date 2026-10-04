package com.goodyaoshi.parentsgames.feature.games.mahjong.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodyaoshi.parentsgames.feature.games.mahjong.domain.TileFace

// 提示高亮主色：品牌红，配 3dp 发光描边，让老人一眼看出提示指的是哪两张牌
private val HintRed = Color(0xFFC03A2B)

private val IvoryFace = Color(0xFFF5EDD8)
private val IvoryFaceSelected = Color(0xFFFFE9A5)
private val IvoryFaceDim = Color(0xFFE0D5BB)
private val IvoryEdgeLight = Color(0xFFFAF4E1)
private val IvoryEdgeMid = Color(0xFFE8DDC3)
private val IvoryEdgeDark = Color(0xFFC9B891)
private val InkBlack = Color(0xFF1F1B17)
private val InkRed = Color(0xFFB8351F)
private val InkGreen = Color(0xFF1E6B3C)
private val InkBlue = Color(0xFF1D5FA5)

enum class TileVisualState { FREE, SELECTED, BLOCKED, HINTED }

@Composable
fun MahjongTile(
    face: TileFace,
    state: TileVisualState,
    width: Dp = 48.dp,
    height: Dp = 64.dp,
    depth: Dp = 5.dp,
    // 提示呼吸脉动（0..1）：由屏幕层 rememberInfiniteTransition 驱动光晕明暗；
    // 「减弱动效」模式下传固定 1f，只保留静态描边
    hintPulse: Float = 1f,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.size(width = width + depth, height = height + depth)) {
        val depthPx = depth.toPx()
        val w = width.toPx()
        val h = height.toPx()
        val cornerPx = 4f.dp.toPx()

        // Sombra escura à direita (perspectiva)
        val rightPath = Path().apply {
            moveTo(w, 0f)
            lineTo(w + depthPx, depthPx)
            lineTo(w + depthPx, h + depthPx)
            lineTo(w, h)
            close()
        }
        drawPath(rightPath, IvoryEdgeDark)

        // Sombra média abaixo (perspectiva)
        val bottomPath = Path().apply {
            moveTo(0f, h)
            lineTo(w, h)
            lineTo(w + depthPx, h + depthPx)
            lineTo(depthPx, h + depthPx)
            close()
        }
        drawPath(bottomPath, IvoryEdgeMid)

        // Face do tile
        val faceColor = when (state) {
            TileVisualState.SELECTED -> IvoryFaceSelected
            TileVisualState.BLOCKED -> IvoryFaceDim
            else -> IvoryFace
        }
        drawRoundRect(
            color = faceColor,
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = CornerRadius(cornerPx),
        )

        // Highlight da luz (topo/esquerda)
        drawLine(IvoryEdgeLight, Offset(4f, 2f), Offset(w - 4f, 2f), 1.5f)
        drawLine(IvoryEdgeLight, Offset(2f, 4f), Offset(2f, h - 4f), 1.5f)
        // Sombra interna (baixo/direita)
        drawLine(IvoryEdgeMid, Offset(4f, h - 2f), Offset(w - 4f, h - 2f), 1f)

        // Borda externa
        drawRoundRect(
            color = InkBlack.copy(alpha = 0.55f),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(width = 1f),
        )

        // 提示高亮：品牌红 3dp 发光描边 + 呼吸光晕（替代原来细而不显眼的琥珀色描边）
        if (state == TileVisualState.HINTED) {
            val main = 3.dp.toPx()          // 主描边宽度：3dp，始终清晰可见
            val pad = 1.5f.dp.toPx()        // 主描边略外扩，避免被牌面深色边框盖住
            // 远层光晕：宽度与透明度随呼吸脉动，形成「发光」循环
            drawRoundRect(
                color = HintRed.copy(alpha = 0.08f + 0.14f * hintPulse),
                topLeft = Offset(-pad - main, -pad - main),
                size = Size(w + (pad + main) * 2f, h + (pad + main) * 2f),
                cornerRadius = CornerRadius(cornerPx + pad + main),
                style = Stroke(width = main * (1.8f + 0.9f * hintPulse)),
            )
            // 近层光晕：呼吸幅度更明显
            drawRoundRect(
                color = HintRed.copy(alpha = 0.20f + 0.30f * hintPulse),
                topLeft = Offset(-pad - main * 0.5f, -pad - main * 0.5f),
                size = Size(w + (pad + main * 0.5f) * 2f, h + (pad + main * 0.5f) * 2f),
                cornerRadius = CornerRadius(cornerPx + pad + main * 0.5f),
                style = Stroke(width = main * (1.3f + 0.5f * hintPulse)),
            )
            // 主描边：3dp 品牌红，不随呼吸变化，保证任何瞬间都能看清
            drawRoundRect(
                color = HintRed,
                topLeft = Offset(-pad, -pad),
                size = Size(w + pad * 2f, h + pad * 2f),
                cornerRadius = CornerRadius(cornerPx + pad),
                style = Stroke(width = main),
            )
        }

        // Desenho da face
        drawTileFace(face, w, h, textMeasurer)
    }
}

private fun DrawScope.drawTileFace(
    face: TileFace,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer,
) {
    when (face) {
        is TileFace.Bamboo -> drawBamboo(face.n, w, h)
        is TileFace.Character -> drawCharacter(face.n, w, h, textMeasurer)
        is TileFace.Circle -> drawCircles(face.n, w, h)
        is TileFace.Wind -> drawWind(face.cardinal, w, h, textMeasurer)
        is TileFace.Dragon -> drawDragon(face.color, w, h, textMeasurer)
        is TileFace.Flower -> drawFlower(face.n, w, h)
        is TileFace.Season -> drawSeason(face.n, w, h, textMeasurer)
    }
}

// ===== Helpers =====

/** Layout normalizado: List<(xNorm, yNorm)> em 0..1 do face. */
private fun layoutDots(n: Int): List<Pair<Float, Float>> = when (n) {
    1 -> listOf(0.5f to 0.5f)
    2 -> listOf(0.5f to 0.25f, 0.5f to 0.75f)
    3 -> listOf(0.25f to 0.25f, 0.5f to 0.5f, 0.75f to 0.75f)
    4 -> listOf(0.30f to 0.27f, 0.70f to 0.27f, 0.30f to 0.73f, 0.70f to 0.73f)
    5 -> listOf(0.30f to 0.27f, 0.70f to 0.27f, 0.5f to 0.5f, 0.30f to 0.73f, 0.70f to 0.73f)
    6 -> listOf(0.30f to 0.27f, 0.70f to 0.27f, 0.30f to 0.50f, 0.70f to 0.50f, 0.30f to 0.73f, 0.70f to 0.73f)
    7 -> listOf(0.30f to 0.20f, 0.70f to 0.20f, 0.50f to 0.40f, 0.30f to 0.55f, 0.70f to 0.55f, 0.30f to 0.80f, 0.70f to 0.80f)
    8 -> listOf(0.27f to 0.20f, 0.50f to 0.20f, 0.73f to 0.20f, 0.27f to 0.50f, 0.73f to 0.50f, 0.27f to 0.80f, 0.50f to 0.80f, 0.73f to 0.80f)
    9 -> listOf(0.27f to 0.20f, 0.50f to 0.20f, 0.73f to 0.20f, 0.27f to 0.50f, 0.50f to 0.50f, 0.73f to 0.50f, 0.27f to 0.80f, 0.50f to 0.80f, 0.73f to 0.80f)
    else -> emptyList()
}

// ===== BAMBU =====
private fun DrawScope.drawBamboo(n: Int, w: Float, h: Float) {
    if (n == 1) {
        drawBird(w, h)
        return
    }
    val positions = layoutDots(n)
    for ((xN, yN) in positions) {
        drawBambooStick(w * xN, h * yN, w * 0.13f, h * 0.10f)
    }
}

private fun DrawScope.drawBambooStick(cx: Float, cy: Float, halfW: Float, halfH: Float) {
    // 3 segmentos verticais com leve gap entre eles, em verde escuro
    val segH = halfH * 0.6f
    val gap = halfH * 0.1f
    for (i in 0..2) {
        val top = cy - halfH + i * (segH + gap)
        drawRoundRect(
            color = InkGreen,
            topLeft = Offset(cx - halfW / 2f, top),
            size = Size(halfW, segH),
            cornerRadius = CornerRadius(halfW * 0.3f),
        )
    }
    // Folhinha
    drawCircle(
        color = InkGreen.copy(alpha = 0.7f),
        radius = halfW * 0.55f,
        center = Offset(cx + halfW * 0.5f, cy - halfH * 0.6f),
    )
}

private fun DrawScope.drawBird(w: Float, h: Float) {
    val cx = w / 2f
    val cy = h / 2f
    // Corpo (oval)
    drawOval(
        color = Color(0xFFC97B5C),
        topLeft = Offset(cx - w * 0.18f, cy - h * 0.10f),
        size = Size(w * 0.36f, h * 0.20f),
    )
    // Cabeça
    drawCircle(
        color = Color(0xFFC97B5C),
        radius = w * 0.10f,
        center = Offset(cx + w * 0.13f, cy - h * 0.13f),
    )
    // Asa
    drawOval(
        color = Color(0xFF8B4F33),
        topLeft = Offset(cx - w * 0.13f, cy - h * 0.05f),
        size = Size(w * 0.22f, h * 0.11f),
    )
    // Olho
    drawCircle(
        color = InkBlack,
        radius = 1.5f,
        center = Offset(cx + w * 0.16f, cy - h * 0.15f),
    )
    // Bico
    val beak = Path().apply {
        moveTo(cx + w * 0.21f, cy - h * 0.13f)
        lineTo(cx + w * 0.28f, cy - h * 0.10f)
        lineTo(cx + w * 0.21f, cy - h * 0.08f)
        close()
    }
    drawPath(beak, Color(0xFFD4A24A))
    // Galho
    drawLine(InkGreen, Offset(w * 0.18f, h * 0.78f), Offset(w * 0.82f, h * 0.78f), 2f)
}

// ===== CARACTERE 萬 =====
private fun DrawScope.drawCharacter(
    n: Int,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer,
) {
    val numText = textMeasurer.measure(
        "$n",
        style = TextStyle(
            color = InkRed,
            fontSize = pxToSp(w * 0.34f),
            fontWeight = FontWeight.W700,
        ),
    )
    drawText(numText, topLeft = Offset((w - numText.size.width) / 2f, h * 0.06f))

    val wanText = textMeasurer.measure(
        "萬",
        style = TextStyle(
            color = InkBlack,
            fontSize = pxToSp(w * 0.42f),
            fontWeight = FontWeight.W600,
        ),
    )
    drawText(wanText, topLeft = Offset((w - wanText.size.width) / 2f, h * 0.42f))
}

// ===== CÍRCULO =====
private fun DrawScope.drawCircles(n: Int, w: Float, h: Float) {
    val positions = layoutDots(n)
    val r = w * 0.10f
    positions.forEachIndexed { idx, (xN, yN) ->
        val color = when (idx % 3) {
            0 -> InkRed
            1 -> InkBlue
            else -> InkGreen
        }
        val cx = w * xN
        val cy = h * yN
        drawCircle(color, radius = r, center = Offset(cx, cy))
        drawCircle(IvoryFace, radius = r * 0.55f, center = Offset(cx, cy))
        drawCircle(color, radius = r * 0.22f, center = Offset(cx, cy))
    }
}

// ===== VENTOS =====
private fun DrawScope.drawWind(
    cardinal: String,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer,
) {
    val text = textMeasurer.measure(
        cardinal,
        style = TextStyle(
            color = InkBlack,
            fontSize = pxToSp(w * 0.65f),
            fontWeight = FontWeight.W700,
        ),
    )
    drawText(
        text,
        topLeft = Offset((w - text.size.width) / 2f, (h - text.size.height) / 2f),
    )
}

// ===== DRAGÕES =====
private fun DrawScope.drawDragon(
    color: String,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer,
) {
    if (color == "WHITE") {
        // Branco: moldura dupla azul (tradição)
        drawRect(
            color = InkBlue,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.64f),
            style = Stroke(width = 2f),
        )
        drawRect(
            color = InkBlue,
            topLeft = Offset(w * 0.27f, h * 0.27f),
            size = Size(w * 0.46f, h * 0.46f),
            style = Stroke(width = 1.5f),
        )
        return
    }
    val (char, ink) = when (color) {
        "RED" -> "中" to InkRed
        "GREEN" -> "發" to InkGreen
        else -> "白" to InkBlue
    }
    val text = textMeasurer.measure(
        char,
        style = TextStyle(
            color = ink,
            fontSize = pxToSp(w * 0.70f),
            fontWeight = FontWeight.W700,
        ),
    )
    drawText(
        text,
        topLeft = Offset((w - text.size.width) / 2f, (h - text.size.height) / 2f),
    )
}

// ===== FLOR =====
private fun DrawScope.drawFlower(n: Int, w: Float, h: Float) {
    val colors = listOf(
        Color(0xFFD4537E),  // 1 rosa
        Color(0xFFE5B23A),  // 2 amarelo
        Color(0xFF7A9B7E),  // 3 sage
        Color(0xFFC97B5C),  // 4 terracotta
    )
    val color = colors[(n - 1).coerceIn(0, 3)]
    val cx = w / 2f
    val cy = h / 2f
    val petalR = w * 0.14f
    for (i in 0 until 5) {
        val angle = (i * 72 - 90) * Math.PI / 180
        val px = cx + (kotlin.math.cos(angle) * w * 0.18f).toFloat()
        val py = cy + (kotlin.math.sin(angle) * w * 0.18f).toFloat()
        drawCircle(color, radius = petalR, center = Offset(px, py))
    }
    drawCircle(Color(0xFFD4A24A), radius = w * 0.10f, center = Offset(cx, cy))
    // Folha pequena no topo
    drawCircle(
        color = InkGreen.copy(alpha = 0.6f),
        radius = w * 0.05f,
        center = Offset(cx + w * 0.05f, cy - w * 0.30f),
    )
}

// ===== ESTAÇÃO =====
private fun DrawScope.drawSeason(
    n: Int,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer,
) {
    val (char, color) = when (n) {
        1 -> "春" to Color(0xFF5E8C5B)    // Primavera
        2 -> "夏" to Color(0xFFE5B23A)    // Verão
        3 -> "秋" to Color(0xFFC97B5C)    // Outono
        else -> "冬" to Color(0xFF1D5FA5)  // Inverno
    }
    val text = textMeasurer.measure(
        char,
        style = TextStyle(
            color = color,
            fontSize = pxToSp(w * 0.55f),
            fontWeight = FontWeight.W700,
        ),
    )
    drawText(
        text,
        topLeft = Offset((w - text.size.width) / 2f, (h - text.size.height) / 2f),
    )
}

// Converte px → sp dentro do DrawScope (precisa de density)
private fun DrawScope.pxToSp(px: Float): TextUnit = (px / density).sp
