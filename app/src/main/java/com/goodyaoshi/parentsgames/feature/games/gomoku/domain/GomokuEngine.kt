package com.goodyaoshi.parentsgames.feature.games.gomoku.domain

import kotlinx.serialization.Serializable

/** 棋局状态：黑先，五连（含长连）即胜，棋盘下满为平局。 */
enum class GomokuStatus {
    BlackTurn, WhiteTurn, BlackWon, WhiteWon, Draw;

    val isPlaying: Boolean
        get() = this == BlackTurn || this == WhiteTurn
}

enum class GomokuMode { AI_MODE, TWO_PLAYER }

@Serializable
data class GomokuSnapshot(
    val board: List<Int> = List(GomokuEngine.SIZE * GomokuEngine.SIZE) { GomokuEngine.EMPTY },
    val mode: String = GomokuMode.AI_MODE.name,
    val status: String = GomokuStatus.BlackTurn.name,
    val lastMove: Int? = null,
    val moves: List<Int> = emptyList(),
    val difficulty: String? = null,
)

/** 15×15 五子棋引擎，0=空 1=黑 2=白，黑先。 */
class GomokuEngine(val mode: GomokuMode = GomokuMode.AI_MODE) {

    companion object {
        const val SIZE = 15
        const val EMPTY = 0
        const val BLACK = 1
        const val WHITE = 2
        private val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
    }

    val board = IntArray(SIZE * SIZE)
    var status: GomokuStatus = GomokuStatus.BlackTurn
        private set
    var lastMove: Int? = null
        private set

    private val history = mutableListOf<Int>()

    fun reset() {
        board.fill(EMPTY)
        status = GomokuStatus.BlackTurn
        lastMove = null
        history.clear()
    }

    fun place(r: Int, c: Int): Boolean {
        if (r !in 0 until SIZE || c !in 0 until SIZE) return false
        return placeAt(r * SIZE + c)
    }

    fun placeAt(index: Int): Boolean {
        if (!status.isPlaying) return false
        if (index !in board.indices) return false
        if (board[index] != EMPTY) return false
        val side = if (status == GomokuStatus.BlackTurn) BLACK else WHITE
        board[index] = side
        history.add(index)
        lastMove = index
        status = when {
            isWinningLine(index, side) -> if (side == BLACK) GomokuStatus.BlackWon else GomokuStatus.WhiteWon
            history.size == SIZE * SIZE -> GomokuStatus.Draw
            side == BLACK -> GomokuStatus.WhiteTurn
            else -> GomokuStatus.BlackTurn
        }
        return true
    }

    /** 落子后检查该点是否连成五子以上（横竖斜，长连也算胜）。 */
    private fun isWinningLine(index: Int, side: Int): Boolean {
        val r = index / SIZE
        val c = index % SIZE
        for ((dr, dc) in DIRECTIONS) {
            var count = 1
            var rr = r + dr
            var cc = c + dc
            while (rr in 0 until SIZE && cc in 0 until SIZE && board[rr * SIZE + cc] == side) {
                count++
                rr += dr
                cc += dc
            }
            rr = r - dr
            cc = c - dc
            while (rr in 0 until SIZE && cc in 0 until SIZE && board[rr * SIZE + cc] == side) {
                count++
                rr -= dr
                cc -= dc
            }
            if (count >= 5) return true
        }
        return false
    }

    /** 撤销一手；棋盘为空时返回 false。 */
    fun undo(): Boolean {
        val last = history.removeLastOrNull() ?: return false
        board[last] = EMPTY
        lastMove = history.lastOrNull()
        status = if (history.size % 2 == 0) GomokuStatus.BlackTurn else GomokuStatus.WhiteTurn
        return true
    }

    fun toSnapshot(difficulty: String? = null): GomokuSnapshot = GomokuSnapshot(
        board = board.toList(),
        mode = mode.name,
        status = status.name,
        lastMove = lastMove,
        moves = history.toList(),
        difficulty = difficulty,
    )

    fun loadFromSnapshot(snapshot: GomokuSnapshot) {
        reset()
        if (snapshot.board.size == SIZE * SIZE) {
            for (i in board.indices) {
                val v = snapshot.board[i]
                board[i] = if (v == BLACK || v == WHITE) v else EMPTY
            }
        }
        history.clear()
        history.addAll(snapshot.moves.filter { it in board.indices && board[it] != EMPTY }.distinct())
        lastMove = history.lastOrNull()
        status = runCatching { GomokuStatus.valueOf(snapshot.status) }.getOrElse {
            if (history.size % 2 == 0) GomokuStatus.BlackTurn else GomokuStatus.WhiteTurn
        }
    }
}
