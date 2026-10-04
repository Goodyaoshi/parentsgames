package com.goodyaoshi.parentsgames.feature.games.colorsort.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goodyaoshi.parentsgames.feature.games.colorsort.domain.LiquidColor
import com.goodyaoshi.parentsgames.feature.games.colorsort.domain.Tube

private val GlassEdge = Color(0xFF6B665E)
private val GlassFill = Color(0x18FFFFFF)         // vidro semi-transparente
private val GlassShine = Color(0x55FFFFFF)        // brilho lateral
private val GlassHighlight = Color(0x88FFFFFF)    // luz no topo
private val GlassShadow = Color(0x22000000)       // sombra dentro

@Composable
fun GlassTube(
    tube: Tube,
    width: Dp = 52.dp,
    height: Dp = 190.dp,
    selected: Boolean = false,
    capacity: Int = tube.capacity,
) {
    Canvas(modifier = Modifier.size(width = width, height = height)) {
        drawTube(tube, selected, capacity)
    }
}

private fun DrawScope.drawTube(tube: Tube, selected: Boolean, capacity: Int) {
    val w = size.width
    val h = size.height
    val edgeW = if (selected) 2.5f else 1.8f
    val borderColor = if (selected) Color(0xFFD4A24A) else GlassEdge

    // Construir path do interior do tubo (forma de U)
    // - Topo retangular aberto
    // - Bottom arredondado
    val interior = Path().apply {
        val radius = w * 0.42f
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w, h - radius)
        // Curva da base (semicírculo)
        arcTo(
            rect = Rect(0f, h - 2 * radius, w, h),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        lineTo(0f, 0f)
        close()
    }

    // Fundo: vidro semi-transparente
    drawPath(interior, GlassFill)

    // Líquido: dentro do clip do interior, desenhar bandas
    clipPath(interior) {
        val sliceHeight = h * 0.85f / capacity
        val bottomMargin = h * 0.04f
        tube.units.forEachIndexed { idx, color ->
            // Bandas vêm do fundo para cima: a unidade 0 fica na base
            val bandBottom = h - bottomMargin - idx * sliceHeight
            val bandTop = bandBottom - sliceHeight
            drawRect(
                color = liquidColor(color),
                topLeft = Offset(0f, bandTop),
                size = Size(w, sliceHeight + 1f),
            )
        }
    }

    // Highlight lateral (luz)
    val shinePath = Path().apply {
        moveTo(w * 0.08f, h * 0.05f)
        lineTo(w * 0.20f, h * 0.05f)
        lineTo(w * 0.20f, h * 0.85f)
        lineTo(w * 0.08f, h * 0.90f)
        close()
    }
    clipPath(interior) {
        drawPath(shinePath, GlassShine)
    }

    // Borda do tubo (deve incluir base curva e abertura no topo, mas SEM tampar topo)
    val borderPath = Path().apply {
        val radius = w * 0.42f
        moveTo(0f, 0f)
        lineTo(0f, h - radius)
        arcTo(
            rect = Rect(0f, h - 2 * radius, w, h),
            startAngleDegrees = 180f,
            sweepAngleDegrees = -180f,
            forceMoveTo = false,
        )
        lineTo(w, 0f)
    }
    drawPath(borderPath, borderColor, style = Stroke(width = edgeW))

    // Sombra interna no topo (sutil)
    drawRect(
        color = GlassShadow,
        topLeft = Offset(0f, 0f),
        size = Size(w, h * 0.04f),
    )

    // "Boca" do tubo (pequena lip no topo)
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(-1.5f, 0f),
        size = Size(w + 3f, 4f),
        cornerRadius = CornerRadius(1.5f),
        style = Stroke(width = edgeW),
    )
}

fun liquidColor(c: LiquidColor): Color = when (c) {
    LiquidColor.RED -> Color(0xFFE24B4A)
    LiquidColor.BLUE -> Color(0xFF378ADD)
    LiquidColor.GREEN -> Color(0xFF639922)
    LiquidColor.YELLOW -> Color(0xFFEFB12A)
    LiquidColor.ORANGE -> Color(0xFFD85A30)
    LiquidColor.PURPLE -> Color(0xFF7B5E8C)
    LiquidColor.PINK -> Color(0xFFD4537E)
    LiquidColor.CYAN -> Color(0xFF4FB3B3)
}
