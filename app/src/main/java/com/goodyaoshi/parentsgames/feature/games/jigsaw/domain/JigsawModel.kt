package com.goodyaoshi.parentsgames.feature.games.jigsaw.domain

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.Serializable

enum class EdgeType { FLAT, TAB, SLOT }

enum class Side { TOP, RIGHT, BOTTOM, LEFT }

/** Definição estática de uma peça (não muda durante a partida). */
data class PieceDefinition(
    val id: Int,
    val gridRow: Int,
    val gridCol: Int,
    val edges: Map<Side, EdgeType>,
)

/**
 * Estado dinâmico de uma peça em jogo — modelo "slot puzzle":
 * cada peça tem um único encaixe correto no tabuleiro central.
 * [placed] = já encaixada no slot (travada, não arrastável).
 */
data class PieceState(
    val pieceId: Int,
    val xPx: Float,
    val yPx: Float,
    val placed: Boolean = false,
)

data class JigsawBoard(
    val rows: Int,
    val cols: Int,
    /** Tamanho da área de jogo (abaixo da top bar), em px. */
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    /** Canto superior-esquerdo do tabuleiro central na tela. */
    val boardLeftPx: Float,
    val boardTopPx: Float,
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val cellSizePx: Int,
    val knobInsetPx: Int,
    val seed: Long,
    val pieces: List<PieceDefinition>,
    val pieceBitmaps: Map<Int, ImageBitmap>,
    /** 完整参考图（与拼块同一份裁剪源），供参考图弹层直接缩放绘制，不重复解码。 */
    val referenceImage: ImageBitmap,
    val initialStates: List<PieceState>,
) {
    /** 单块碎片位图边长（核心格 + 两侧凸起留白）。 */
    val pieceSizePx: Int get() = cellSizePx + 2 * knobInsetPx

    /** 碎片 (row, col) 吸附入位后，其位图左上角的屏幕坐标。 */
    fun homeX(gridCol: Int): Float = boardLeftPx + gridCol * cellSizePx - knobInsetPx

    fun homeY(gridRow: Int): Float = boardTopPx + gridRow * cellSizePx - knobInsetPx

    /** 碎片 (row, col) 目标插槽中心的屏幕坐标。 */
    fun slotCenterX(gridCol: Int): Float = boardLeftPx + (gridCol + 0.5f) * cellSizePx

    fun slotCenterY(gridRow: Int): Float = boardTopPx + (gridRow + 0.5f) * cellSizePx
}

/**
 * 对局快照。带版本号：结构不兼容的旧档在恢复时直接安全弃档开新局，
 * 不做字段迁移（拼板布局/坐标随版本会变，旧坐标没有还原意义）。
 */
@Serializable
data class JigsawSnapshot(
    val version: Int,
    val photoId: Long,
    val pieceCount: Int,
    val seed: Long,
    val viewportW: Int,
    val viewportH: Int,
    val elapsedMs: Long,
    val pieces: List<PieceSnap>,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

/** 碎片状态快照：未吸附碎片=当前摆放位置；已吸附碎片=placed 标记（位置即插槽）。 */
@Serializable
data class PieceSnap(
    val pieceId: Int,
    val xPx: Float,
    val yPx: Float,
    val placed: Boolean = false,
)
