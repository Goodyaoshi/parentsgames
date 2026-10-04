package com.goodyaoshi.parentsgames.feature.games.jigsaw.domain

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Constrói um [JigsawBoard] a partir de um bitmap e contagem-alvo de peças.
 * Operação pesada — chamar em Dispatchers.Default.
 *
 * Modelo "slot puzzle" para idosos:
 *  - Tabuleiro central fixo (~70% da largura), com um slot por peça;
 *  - Peças espalhadas automaticamente nas faixas livres ao redor do tabuleiro,
 *    sempre dentro da tela e sem empilhar umas em cima das outras.
 */
object JigsawBoardBuilder {

    /** Largura do tabuleiro como fração da largura da área de jogo. */
    private const val BOARD_WIDTH_RATIO = 0.70f

    /** Altura máxima do tabuleiro — deixa faixas livres acima/abaixo para as peças. */
    private const val BOARD_HEIGHT_RATIO = 0.58f

    /**
     * Reserva vertical na base da tela para os botões fixos
     * (「参考图」/「玩法」), para as peças não ficarem por baixo deles.
     */
    private fun bottomReservePx(viewportH: Int): Int =
        (viewportH * 0.085f).toInt().coerceIn(96, 160)

    /**
     * @param sourceBitmap imagem original já com EXIF rotation aplicada
     * @param targetPieces 12, 24, 48, 100
     * @param viewportWidthPx largura da área onde as peças serão exibidas
     * @param viewportHeightPx altura idem
     * @param seed para reprodutibilidade
     */
    fun build(
        sourceBitmap: Bitmap,
        targetPieces: Int,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        seed: Long = System.currentTimeMillis(),
    ): JigsawBoard {
        val imageAspect = sourceBitmap.width.toFloat() / sourceBitmap.height
        val (cols, rows) = JigsawSlicer.chooseGrid(targetPieces, imageAspect)

        val maxBoardW = viewportWidthPx * BOARD_WIDTH_RATIO
        val maxBoardH = viewportHeightPx * BOARD_HEIGHT_RATIO
        val cellByW = maxBoardW / cols
        val cellByH = maxBoardH / rows
        val cellSize = minOf(cellByW, cellByH).toInt().coerceAtLeast(72)
        val knobInset = JigsawSlicer.knobInsetForCell(cellSize)

        // Crop/resize source para cols*cellSize × rows*cellSize
        val needW = cols * cellSize
        val needH = rows * cellSize
        val cropped = cropCenter(sourceBitmap, needW, needH)

        val pieces = JigsawSlicer.assignEdges(rows, cols, seed)

        val bitmaps = HashMap<Int, ImageBitmap>(pieces.size)
        for (piece in pieces) {
            bitmaps[piece.id] = JigsawSlicer.renderPieceBitmap(
                sourceBitmap = cropped,
                piece = piece,
                cellSize = cellSize,
                knobInset = knobInset,
                seedForVariation = seed,
            )
        }

        // Tabuleiro central: horizontalmente centrado; verticalmente centrado
        // no espaço acima da faixa dos botões (assim sobra folga em cima e embaixo).
        val boardLeft = ((viewportWidthPx - needW) / 2f).coerceAtLeast(0f)
        val usableBottom = (viewportHeightPx - bottomReservePx(viewportHeightPx)).coerceAtLeast(needH)
        val boardTop = ((usableBottom - needH) / 2f).coerceAtLeast(4f)

        val initialStates = scatterPieces(
            pieces = pieces,
            viewportW = viewportWidthPx,
            viewportH = viewportHeightPx,
            boardLeft = boardLeft,
            boardTop = boardTop,
            boardW = needW,
            boardH = needH,
            cellSize = cellSize,
            knobInset = knobInset,
            rng = Random(seed),
        )

        return JigsawBoard(
            rows = rows,
            cols = cols,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            boardLeftPx = boardLeft,
            boardTopPx = boardTop,
            imageWidthPx = needW,
            imageHeightPx = needH,
            cellSizePx = cellSize,
            knobInsetPx = knobInset,
            seed = seed,
            pieces = pieces,
            pieceBitmaps = bitmaps,
            referenceImage = cropped.asImageBitmap(),
            initialStates = initialStates,
        )
    }

    private data class ScatterZone(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
    )

    /**
     * 排布所有碎片：按「底部带 → 顶部带 → 左侧带 → 右侧带」的顺序，
     * 在拼板四周的空白带里整齐摆一个网格（带轻微随机抖动）。
     * 间距从“互不重叠”自适应收紧（最低 70% 脚印，允许轻微叠边），
     * 保证所有碎片都完整落在屏幕内、不会拖一步就跑出屏。
     */
    private fun scatterPieces(
        pieces: List<PieceDefinition>,
        viewportW: Int,
        viewportH: Int,
        boardLeft: Float,
        boardTop: Float,
        boardW: Int,
        boardH: Int,
        cellSize: Int,
        knobInset: Int,
        rng: Random,
    ): List<PieceState> {
        val footprint = cellSize + 2 * knobInset
        val half = footprint / 2f
        val boardRight = boardLeft + boardW
        val boardBottom = boardTop + boardH
        val usableBottom = (viewportH - bottomReservePx(viewportH)).toFloat().coerceAtLeast(boardBottom)

        // 顺序即填充优先级：老人手持手机，靠近底部的碎片最容易拿取
        val zones = listOf(
            // 底部带（按钮条之上）
            ScatterZone(0f, boardBottom, viewportW.toFloat(), (usableBottom - boardBottom).coerceAtLeast(0f)),
            // 顶部带
            ScatterZone(0f, 0f, viewportW.toFloat(), boardTop),
            // 拼板左右两条侧带
            ScatterZone(0f, boardTop, boardLeft, boardH.toFloat()),
            ScatterZone(boardRight, boardTop, (viewportW - boardRight).coerceAtLeast(0f), boardH.toFloat()),
        )

        /** 在给定间距下铺格子；装不下全部碎片时返回 null。 */
        fun tryLayout(pitch: Float): List<Offset>? {
            val centers = mutableListOf<Offset>()
            var remaining = pieces.size
            for (zone in zones) {
                if (remaining <= 0) break
                val cols = (zone.w / pitch).toInt()
                val rows = (zone.h / pitch).toInt()
                if (cols <= 0 || rows <= 0) continue
                val take = minOf(remaining, cols * rows)
                val usedRows = (take + cols - 1) / cols
                val cellW = zone.w / cols
                val cellH = zone.h / usedRows
                for (i in 0 until take) {
                    centers.add(
                        Offset(
                            zone.x + ((i % cols) + 0.5f) * cellW,
                            zone.y + ((i / cols) + 0.5f) * cellH,
                        )
                    )
                }
                remaining -= take
            }
            return if (remaining == 0) centers else null
        }

        var pitch = footprint.toFloat()
        val minPitch = footprint * 0.70f
        var found = tryLayout(pitch)
        while (found == null && pitch >= minPitch - 0.5f) {
            pitch *= 0.93f
            found = tryLayout(pitch)
        }
        val slotCenters: List<Offset>
        if (found != null) {
            slotCenters = found
        } else {
            // 极小屏兜底：按最小间距仍装不下时，上下带允许行距压缩（轻微叠边），
            // 但每个碎片中心仍 clamp 在屏内，绝不会越界。
            val centers = mutableListOf<Offset>()
            var remaining = pieces.size
            for (zone in zones) {
                if (remaining <= 0) break
                val cols = ((zone.w / minPitch).toInt()).coerceAtLeast(1)
                val fittedRows = ((zone.h / minPitch).toInt()).coerceAtLeast(1)
                val neededRows = max(fittedRows, (remaining + cols - 1) / cols)
                val cellW = zone.w / cols
                val cellH = zone.h / neededRows
                val take = minOf(remaining, cols * neededRows)
                for (i in 0 until take) {
                    centers.add(
                        Offset(
                            zone.x + ((i % cols) + 0.5f) * cellW,
                            zone.y + ((i / cols) + 0.5f) * cellH,
                        )
                    )
                }
                remaining -= take
            }
            slotCenters = centers
        }

        // 抖动幅度只占用格子间的空隙，不会造成新的重叠
        val gap = (pitch - footprint).coerceAtLeast(0f) * 0.6f
        val shuffled = pieces.shuffled(rng)
        val maxCx = (viewportW - half).coerceAtLeast(half)
        val maxCy = (viewportH - half).coerceAtLeast(half)
        return shuffled.mapIndexed { i, def ->
            val slot = slotCenters[i % slotCenters.size]
            val cx = (slot.x + (rng.nextFloat() - 0.5f) * gap).coerceIn(half, maxCx)
            val cy = (slot.y + (rng.nextFloat() - 0.5f) * gap).coerceIn(half, maxCy)
            PieceState(
                pieceId = def.id,
                xPx = cx - half,
                yPx = cy - half,
                placed = false,
            )
        }
    }

    private fun cropCenter(src: Bitmap, w: Int, h: Int): Bitmap {
        val srcAspect = src.width.toFloat() / src.height
        val dstAspect = w.toFloat() / h

        val (cropW, cropH) = if (srcAspect > dstAspect) {
            (src.height * dstAspect).toInt() to src.height
        } else {
            src.width to (src.width / dstAspect).toInt()
        }
        val x = (src.width - cropW) / 2
        val y = (src.height - cropH) / 2
        val cropped = Bitmap.createBitmap(src, x, y, min(cropW, src.width - x), min(cropH, src.height - y))
        return if (cropped.width == w && cropped.height == h) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, w, h, true)
        }
    }
}
