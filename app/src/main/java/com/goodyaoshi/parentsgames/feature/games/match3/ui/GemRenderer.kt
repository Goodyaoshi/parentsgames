package com.goodyaoshi.parentsgames.feature.games.match3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.goodyaoshi.parentsgames.feature.games.match3.domain.CellContent
import com.goodyaoshi.parentsgames.feature.games.match3.domain.GemType
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Gem(cell: CellContent, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val type = cell.type ?: return@Canvas
        when (cell) {
            is CellContent.Normal -> drawGem(type, size)
            is CellContent.FlameH -> {
                drawGem(type, size)
                drawHorizontalFlame(size)
            }
            is CellContent.FlameV -> {
                drawGem(type, size)
                drawVerticalFlame(size)
            }
            is CellContent.Bomb -> {
                drawGem(type, size)
                drawBombHalo(size)
            }
            CellContent.Empty -> Unit
        }
    }
}

private fun DrawScope.drawGem(type: GemType, size: Size) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val color = gemColor(type)
    val highlight = highlightColor(type)

    when (type) {
        GemType.RAIN -> drawRaindrop(cx, cy, w, h, color, highlight)
        GemType.LEAF -> drawLeaf(cx, cy, w, h, color, highlight)
        GemType.BLOOM -> drawBloom(cx, cy, w, h, color, highlight)
        GemType.SUN -> drawSun(cx, cy, w, h, color, highlight)
        GemType.MOON -> drawMoon(cx, cy, w, h, color, highlight)
        GemType.EMBER -> drawEmber(cx, cy, w, h, color, highlight)
    }
}

private fun gemColor(t: GemType) = when (t) {
    GemType.RAIN -> Color(0xFF378ADD)
    GemType.LEAF -> Color(0xFF5E8C5B)
    GemType.BLOOM -> Color(0xFFD4537E)
    GemType.SUN -> Color(0xFFE5B23A)
    GemType.MOON -> Color(0xFF7B5E8C)
    GemType.EMBER -> Color(0xFFD85A30)
}

private fun highlightColor(t: GemType) = when (t) {
    GemType.RAIN -> Color(0xFFB5D4F4)
    GemType.LEAF -> Color(0xFFC0DD97)
    GemType.BLOOM -> Color(0xFFF4C0D1)
    GemType.SUN -> Color(0xFFFAC775)
    GemType.MOON -> Color(0xFFCECBF6)
    GemType.EMBER -> Color(0xFFF5C4B3)
}

private fun DrawScope.drawRaindrop(cx: Float, cy: Float, w: Float, h: Float, color: Color, hi: Color) {
    val r = minOf(w, h) * 0.36f
    val drop = Path().apply {
        moveTo(cx, cy - r * 1.25f)
        cubicTo(cx + r * 0.8f, cy - r * 0.6f, cx + r, cy + r * 0.3f, cx, cy + r)
        cubicTo(cx - r, cy + r * 0.3f, cx - r * 0.8f, cy - r * 0.6f, cx, cy - r * 1.25f)
        close()
    }
    drawPath(drop, color)
    // Highlight superior
    drawCircle(hi.copy(alpha = 0.7f), radius = r * 0.22f, center = Offset(cx - r * 0.25f, cy - r * 0.45f))
}

private fun DrawScope.drawLeaf(cx: Float, cy: Float, w: Float, h: Float, color: Color, hi: Color) {
    val r = minOf(w, h) * 0.40f
    val leaf = Path().apply {
        moveTo(cx, cy + r)
        cubicTo(cx - r * 1.1f, cy + r * 0.3f, cx - r * 0.6f, cy - r * 1.0f, cx, cy - r)
        cubicTo(cx + r * 0.6f, cy - r * 1.0f, cx + r * 1.1f, cy + r * 0.3f, cx, cy + r)
        close()
    }
    drawPath(leaf, color)
    // Nervura central
    drawLine(
        color = hi.copy(alpha = 0.8f),
        start = Offset(cx, cy - r * 0.9f),
        end = Offset(cx, cy + r * 0.9f),
        strokeWidth = 1.5f,
    )
}

private fun DrawScope.drawBloom(cx: Float, cy: Float, w: Float, h: Float, color: Color, hi: Color) {
    val r = minOf(w, h) * 0.18f
    for (i in 0 until 5) {
        val angle = (i * 72 - 90) * Math.PI.toFloat() / 180f
        val px = cx + cos(angle) * r * 1.25f
        val py = cy + sin(angle) * r * 1.25f
        drawCircle(color, radius = r, center = Offset(px, py))
    }
    drawCircle(Color(0xFFE5B23A), radius = r * 0.6f, center = Offset(cx, cy))
}

private fun DrawScope.drawSun(cx: Float, cy: Float, w: Float, h: Float, color: Color, hi: Color) {
    val r = minOf(w, h) * 0.28f
    // Raios
    for (i in 0 until 8) {
        val angle = (i * 45) * Math.PI.toFloat() / 180f
        val x1 = cx + cos(angle) * r * 1.1f
        val y1 = cy + sin(angle) * r * 1.1f
        val x2 = cx + cos(angle) * r * 1.5f
        val y2 = cy + sin(angle) * r * 1.5f
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2.5f)
    }
    drawCircle(color, radius = r, center = Offset(cx, cy))
    drawCircle(hi.copy(alpha = 0.55f), radius = r * 0.35f, center = Offset(cx - r * 0.2f, cy - r * 0.2f))
}

private fun DrawScope.drawMoon(cx: Float, cy: Float, w: Float, h: Float, color: Color, hi: Color) {
    val r = minOf(w, h) * 0.36f
    // Lua crescente: círculo grande - círculo offset
    val moonPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r))
    }
    val cutPath = Path().apply {
        val offset = r * 0.45f
        addOval(androidx.compose.ui.geometry.Rect(cx - r + offset, cy - r, cx + r + offset, cy + r))
    }
    val crescent = Path().apply {
        op(moonPath, cutPath, androidx.compose.ui.graphics.PathOperation.Difference)
    }
    drawPath(crescent, color)
    // Pontinho de estrela
    drawCircle(hi.copy(alpha = 0.9f), radius = 2f, center = Offset(cx + r * 0.5f, cy - r * 0.4f))
}

private fun DrawScope.drawEmber(cx: Float, cy: Float, w: Float, h: Float, color: Color, hi: Color) {
    val r = minOf(w, h) * 0.38f
    val flame = Path().apply {
        moveTo(cx, cy - r * 1.1f)
        cubicTo(cx + r * 0.7f, cy - r * 0.3f, cx + r, cy + r * 0.4f, cx + r * 0.3f, cy + r * 0.9f)
        cubicTo(cx, cy + r * 0.5f, cx, cy + r * 0.7f, cx - r * 0.3f, cy + r * 0.9f)
        cubicTo(cx - r, cy + r * 0.4f, cx - r * 0.7f, cy - r * 0.3f, cx, cy - r * 1.1f)
        close()
    }
    drawPath(flame, color)
    val inner = Path().apply {
        moveTo(cx, cy - r * 0.6f)
        cubicTo(cx + r * 0.3f, cy - r * 0.2f, cx + r * 0.35f, cy + r * 0.2f, cx, cy + r * 0.5f)
        cubicTo(cx - r * 0.35f, cy + r * 0.2f, cx - r * 0.3f, cy - r * 0.2f, cx, cy - r * 0.6f)
        close()
    }
    drawPath(inner, Color(0xFFFAC775))
}

private fun DrawScope.drawHorizontalFlame(size: Size) {
    val cy = size.height / 2f
    // Linha de fogo
    drawLine(
        color = Color.White.copy(alpha = 0.95f),
        start = Offset(size.width * 0.08f, cy),
        end = Offset(size.width * 0.92f, cy),
        strokeWidth = 2.5f,
    )
}

private fun DrawScope.drawVerticalFlame(size: Size) {
    val cx = size.width / 2f
    drawLine(
        color = Color.White.copy(alpha = 0.95f),
        start = Offset(cx, size.height * 0.08f),
        end = Offset(cx, size.height * 0.92f),
        strokeWidth = 2.5f,
    )
}

private fun DrawScope.drawBombHalo(size: Size) {
    val r = minOf(size.width, size.height) * 0.5f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = r * 0.95f,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f),
    )
}
