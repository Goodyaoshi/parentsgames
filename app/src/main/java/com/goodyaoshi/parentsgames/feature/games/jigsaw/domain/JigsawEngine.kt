package com.goodyaoshi.parentsgames.feature.games.jigsaw.domain

import kotlin.math.hypot

/**
 * Lógica de jogo do jigsaw — modelo "slot puzzle" (tabuleiro com slots fixos),
 * pensado para pessoas mais velhas:
 *  - Cada peça tem UM único encaixe correto, no tabuleiro central;
 *  - Arrasto é sempre preso dentro da tela;
 *  - Ao soltar perto do slot certo, a peça se alinha e trava;
 *  - Sem rotação — peças ficam sempre "de frente".
 */
class JigsawEngine(
    val board: JigsawBoard,
    initialStates: List<PieceState> = board.initialStates,
    /**
     * 吸附容差：碎片中心距正确插槽中心小于「碎片核心尺寸的 45%」即自动入位，
     * 对老人足够宽容。
     */
    val snapTolerancePx: Float = board.cellSizePx * 0.45f,
) {
    private val statesById: MutableMap<Int, PieceState> =
        initialStates.associateBy { it.pieceId }.toMutableMap()

    val states: List<PieceState> get() = statesById.values.toList()

    val placedCount: Int get() = statesById.values.count { it.placed }

    fun stateOf(pieceId: Int): PieceState? = statesById[pieceId]

    fun definitionOf(pieceId: Int): PieceDefinition? =
        board.pieces.firstOrNull { it.id == pieceId }

    /** 已入位的碎片锁定，不能再拖。 */
    fun isPlaced(pieceId: Int): Boolean = statesById[pieceId]?.placed == true

    /**
     * 拖动碎片（仅未入位的），并把位置 clamp 在屏幕范围内，
     * 保证碎片永远不会被拖出屏幕。
     */
    fun moveBy(pieceId: Int, dx: Float, dy: Float): Boolean {
        val state = statesById[pieceId] ?: return false
        if (state.placed) return false
        val size = board.pieceSizePx.toFloat()
        val maxX = (board.viewportWidthPx - size).coerceAtLeast(0f)
        val maxY = (board.viewportHeightPx - size).coerceAtLeast(0f)
        statesById[pieceId] = state.copy(
            xPx = (state.xPx + dx).coerceIn(0f, maxX),
            yPx = (state.yPx + dy).coerceIn(0f, maxY),
        )
        return true
    }

    /**
     * 松手时尝试吸附：碎片中心与其正确插槽中心的距离小于容差 →
     * 对齐到插槽并锁定。返回是否入位。
     */
    fun tryPlace(pieceId: Int): Boolean {
        val state = statesById[pieceId] ?: return false
        if (state.placed) return false
        val def = definitionOf(pieceId) ?: return false
        val centerX = state.xPx + board.pieceSizePx / 2f
        val centerY = state.yPx + board.pieceSizePx / 2f
        val dx = board.slotCenterX(def.gridCol) - centerX
        val dy = board.slotCenterY(def.gridRow) - centerY
        if (hypot(dx, dy) >= snapTolerancePx) return false
        statesById[pieceId] = state.copy(
            xPx = board.homeX(def.gridCol),
            yPx = board.homeY(def.gridRow),
            placed = true,
        )
        return true
    }

    /** 胜利 = 全部碎片吸附完成。 */
    fun isComplete(): Boolean = statesById.values.all { it.placed }
}
