package com.goodyaoshi.parentsgames.feature.games.jigsaw.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Slicer canônico do jigsaw — VERSÃO 2 com correções críticas.
 *
 * Inspirações: headbreaker (JS), shamim-akhtar (Unity), piecemaker (Python).
 *
 * Correções nesta versão:
 *  1. Convenção de "outward" uniforme em todas as 4 arestas (era inversa em RIGHT/BOTTOM)
 *  2. Variação aleatória da curva agora SHARED entre peças vizinhas (mesmo seed por aresta)
 *  3. Knob extent ≤ knobInset (não excede o padding do bitmap, evita clipping)
 *  4. 4 cubic Beziers compõem cada knob (entrada, subida, descida, saída) com lip clássico
 */
object JigsawSlicer {

    /** Padding do bitmap como ratio do cellSize. É também o knob max extent. */
    private const val KNOB_INSET_RATIO = 0.30f

    /** Variação aleatória entre peças (±X% da curva). */
    private const val EDGE_VARIATION = 0.04f

    /** Coeficiente máximo do bulbo (≤ 1.0 para fit em knobInset com safety margin). */
    private const val BULB_EXTENT = 0.95f

    /** "Lip" — pequena inflexão pra dentro no início/fim do knob (negativo significa entrar). */
    private const val LIP_DEPTH = 0.08f

    fun knobInsetForCell(cellSize: Int): Int =
        (cellSize * KNOB_INSET_RATIO).toInt().coerceAtLeast(10)

    fun chooseGrid(targetPieces: Int, imageAspect: Float): Pair<Int, Int> {
        var best: Pair<Int, Int>? = null
        var bestScore = Float.MAX_VALUE
        for (cols in 2..targetPieces) {
            val rows = (targetPieces.toFloat() / cols).toInt()
            if (rows < 2) continue
            val piecesActual = cols * rows
            val aspectError = abs(((cols.toFloat() / rows) / imageAspect) - 1f)
            val countError = abs(piecesActual - targetPieces).toFloat() / targetPieces
            val score = aspectError * 2f + countError
            if (score < bestScore) {
                bestScore = score
                best = cols to rows
            }
        }
        return best ?: (4 to 3)
    }

    fun assignEdges(rows: Int, cols: Int, seed: Long): List<PieceDefinition> {
        val rng = Random(seed)
        val edges = Array(rows) { Array(cols) { mutableMapOf<Side, EdgeType>() } }

        for (r in 0 until rows) for (c in 0 until cols) {
            edges[r][c][Side.TOP] = when {
                r == 0 -> EdgeType.FLAT
                else -> opposite(edges[r - 1][c][Side.BOTTOM]!!)
            }
            edges[r][c][Side.LEFT] = when {
                c == 0 -> EdgeType.FLAT
                else -> opposite(edges[r][c - 1][Side.RIGHT]!!)
            }
            edges[r][c][Side.BOTTOM] = when {
                r == rows - 1 -> EdgeType.FLAT
                rng.nextBoolean() -> EdgeType.TAB
                else -> EdgeType.SLOT
            }
            edges[r][c][Side.RIGHT] = when {
                c == cols - 1 -> EdgeType.FLAT
                rng.nextBoolean() -> EdgeType.TAB
                else -> EdgeType.SLOT
            }
        }

        val out = mutableListOf<PieceDefinition>()
        var id = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            out.add(
                PieceDefinition(
                    id = id++,
                    gridRow = r,
                    gridCol = c,
                    edges = edges[r][c].toMap(),
                )
            )
        }
        return out
    }

    private fun opposite(e: EdgeType): EdgeType = when (e) {
        EdgeType.TAB -> EdgeType.SLOT
        EdgeType.SLOT -> EdgeType.TAB
        EdgeType.FLAT -> EdgeType.FLAT
    }

    /**
     * Seed único por aresta horizontal (entre row r e r+1) na coluna c.
     * Peças (r, c) e (r+1, c) compartilham essa aresta — ambas usam o mesmo seed.
     */
    private fun hEdgeSeed(boardSeed: Long, r: Int, c: Int): Long =
        boardSeed xor (r * 1_000_000L + c * 7L + 0xA1B2C3L)

    /**
     * Seed único por aresta vertical (entre col c e c+1) na linha r.
     * Peças (r, c) e (r, c+1) compartilham — ambas usam o mesmo seed.
     */
    private fun vEdgeSeed(boardSeed: Long, r: Int, c: Int): Long =
        boardSeed xor (r * 1_000_000L + c * 7L + 0xD4E5F6L)

    /**
     * Constrói o Path do contorno da peça em coordenadas locais do bitmap.
     * Origem (0,0) é canto superior-esquerdo do bitmap.
     * O "miolo" (cell interior) começa em (knobInset, knobInset).
     */
    fun buildPiecePath(
        piece: PieceDefinition,
        cellSize: Float,
        knobInset: Float,
        boardSeed: Long,
    ): Path {
        val path = Path()
        val left = knobInset
        val top = knobInset
        val right = knobInset + cellSize
        val bottom = knobInset + cellSize

        // RNG por aresta (shared entre peças vizinhas)
        val topRng = if (piece.gridRow == 0) Random(0L)
            else Random(hEdgeSeed(boardSeed, piece.gridRow - 1, piece.gridCol))
        val rightRng = Random(vEdgeSeed(boardSeed, piece.gridRow, piece.gridCol))
        val bottomRng = Random(hEdgeSeed(boardSeed, piece.gridRow, piece.gridCol))
        val leftRng = if (piece.gridCol == 0) Random(0L)
            else Random(vEdgeSeed(boardSeed, piece.gridRow, piece.gridCol - 1))

        path.moveTo(left, top)

        // Convenção CORRETA: outward = -1 uniforme para TAB ir pra fora em todas as direções
        // (em traversal clockwise no sistema de Y crescente para baixo)

        appendEdge(
            path,
            from = Offset(left, top), to = Offset(right, top),
            type = piece.edges[Side.TOP]!!, knobInset = knobInset,
            outward = -1f, rng = topRng,
        )
        appendEdge(
            path,
            from = Offset(right, top), to = Offset(right, bottom),
            type = piece.edges[Side.RIGHT]!!, knobInset = knobInset,
            outward = -1f, rng = rightRng,
        )
        appendEdge(
            path,
            from = Offset(right, bottom), to = Offset(left, bottom),
            type = piece.edges[Side.BOTTOM]!!, knobInset = knobInset,
            outward = -1f, rng = bottomRng,
        )
        appendEdge(
            path,
            from = Offset(left, bottom), to = Offset(left, top),
            type = piece.edges[Side.LEFT]!!, knobInset = knobInset,
            outward = -1f, rng = leftRng,
        )

        path.close()
        return path
    }

    /**
     * Acrescenta uma aresta ao path. FLAT é linha reta; TAB/SLOT é knob com 4 cubic Beziers.
     */
    private fun appendEdge(
        path: Path,
        from: Offset, to: Offset,
        type: EdgeType, knobInset: Float,
        outward: Float, rng: Random,
    ) {
        if (type == EdgeType.FLAT) {
            path.lineTo(to.x, to.y)
            return
        }
        // sign positivo → curva vai pra fora (TAB); negativo → pra dentro (SLOT)
        val sign = if (type == EdgeType.TAB) outward else -outward

        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = hypot(dx, dy)

        // Normal perpendicular à aresta (consistente para todas as 4 direções)
        val nx = -dy / len
        val ny = dx / len

        // Variações shared entre vizinhas
        val v1 = 1f + (rng.nextFloat() - 0.5f) * EDGE_VARIATION
        val v2 = 1f + (rng.nextFloat() - 0.5f) * EDGE_VARIATION

        val k = knobInset
        val bulb = k * BULB_EXTENT       // max distância do knob
        val lip = k * LIP_DEPTH          // pequena inflexão pra dentro

        fun pt(t: Float, off: Float): Pair<Float, Float> {
            val px = from.x + dx * t + nx * off * sign
            val py = from.y + dy * t + ny * off * sign
            return px to py
        }

        // Segmento 1: linha reta até o início do "lip"
        // (entra com pequena inflexão pra dentro pra criar o pescoço característico)
        val (a1x, a1y) = pt(0.30f, 0f)
        val (a2x, a2y) = pt(0.34f, -lip * v1)        // entra pra dentro
        val (a3x, a3y) = pt(0.38f, bulb * 0.20f * v1)
        path.cubicTo(a1x, a1y, a2x, a2y, a3x, a3y)

        // Segmento 2: subida do lado esquerdo do bulbo
        val (b1x, b1y) = pt(0.38f, bulb * 0.65f * v1)
        val (b2x, b2y) = pt(0.42f, bulb * v2)
        val (b3x, b3y) = pt(0.50f, bulb * v2)        // topo do bulbo (centro)
        path.cubicTo(b1x, b1y, b2x, b2y, b3x, b3y)

        // Segmento 3: descida do lado direito do bulbo
        val (c1x, c1y) = pt(0.58f, bulb * v2)
        val (c2x, c2y) = pt(0.62f, bulb * v2)
        val (c3x, c3y) = pt(0.62f, bulb * 0.65f * v1)
        path.cubicTo(c1x, c1y, c2x, c2y, c3x, c3y)

        // Segmento 4: saída (espelho do segmento 1)
        val (d1x, d1y) = pt(0.62f, bulb * 0.20f * v1)
        val (d2x, d2y) = pt(0.66f, -lip * v1)
        val (d3x, d3y) = pt(0.70f, 0f)
        path.cubicTo(d1x, d1y, d2x, d2y, d3x, d3y)

        path.lineTo(to.x, to.y)
    }

    /**
     * Recorta uma peça do bitmap-fonte usando o path como máscara.
     */
    fun renderPieceBitmap(
        sourceBitmap: Bitmap,
        piece: PieceDefinition,
        cellSize: Int,
        knobInset: Int,
        seedForVariation: Long,
    ): androidx.compose.ui.graphics.ImageBitmap {
        val w = cellSize + 2 * knobInset
        val h = cellSize + 2 * knobInset
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val path = buildPiecePath(piece, cellSize.toFloat(), knobInset.toFloat(), seedForVariation)
        val androidPath = path.asAndroidPath()

        canvas.save()
        canvas.clipPath(androidPath)

        // Source region — incluindo o knobInset extra em volta da célula central
        val srcX = piece.gridCol * cellSize - knobInset
        val srcY = piece.gridRow * cellSize - knobInset
        val srcRect = Rect(
            max(0, srcX),
            max(0, srcY),
            min(sourceBitmap.width, srcX + w),
            min(sourceBitmap.height, srcY + h),
        )
        val dstLeft = if (srcX < 0) -srcX else 0
        val dstTop = if (srcY < 0) -srcY else 0
        val dstRect = Rect(
            dstLeft,
            dstTop,
            dstLeft + srcRect.width(),
            dstTop + srcRect.height(),
        )
        canvas.drawBitmap(sourceBitmap, srcRect, dstRect, null)
        canvas.restore()

        // Contorno suave (anti-aliased) — preto translúcido pra dar definição entre peças
        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = 0x66000000.toInt()
            isAntiAlias = true
        }
        canvas.drawPath(androidPath, stroke)

        return out.asImageBitmap()
    }
}
