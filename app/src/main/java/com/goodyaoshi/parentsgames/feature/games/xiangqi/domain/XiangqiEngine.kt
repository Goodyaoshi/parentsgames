package com.goodyaoshi.parentsgames.feature.games.xiangqi.domain

import kotlinx.serialization.Serializable

/** 红方棋子用正数表示，黑方用负数，0 表示空位。 */
const val RED = 1
const val BLACK = -1

const val KING = 1      // 帅 / 将
const val ADVISOR = 2   // 仕 / 士
const val ELEPHANT = 3  // 相 / 象
const val HORSE = 4     // 马
const val ROOK = 5      // 车
const val CANNON = 6    // 炮
const val PAWN = 7      // 兵 / 卒

/** 一步棋：from → to。captured 为被吃的棋子编码（0 表示没吃子）。 */
data class Move(val fromR: Int, val fromC: Int, val toR: Int, val toC: Int, val captured: Int = 0)

enum class XiangqiStatus { RED_TURN, BLACK_TURN, RED_WON, BLACK_WON }

@Serializable
data class MoveSnap(val fr: Int, val fc: Int, val tr: Int, val tc: Int, val cap: Int = 0)

@Serializable
data class XiangqiSnapshot(
    val rows: List<String>,
    val turn: Int,
    val history: List<MoveSnap> = emptyList(),
    val mode: String,
    val elapsedMs: Long = 0,
    val difficulty: String? = null,
)

/** 走子规则与将军判定。棋盘 10 行 × 9 列：行 0 在上（黑方），行 9 在下（红方）。 */
object XiangqiRules {
    const val ROWS = 10
    const val COLS = 9

    fun initialBoard(): Array<IntArray> = arrayOf(
        intArrayOf(-ROOK, -HORSE, -ELEPHANT, -ADVISOR, -KING, -ADVISOR, -ELEPHANT, -HORSE, -ROOK),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, -CANNON, 0, 0, 0, 0, 0, -CANNON, 0),
        intArrayOf(-PAWN, 0, -PAWN, 0, -PAWN, 0, -PAWN, 0, -PAWN),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(PAWN, 0, PAWN, 0, PAWN, 0, PAWN, 0, PAWN),
        intArrayOf(0, CANNON, 0, 0, 0, 0, 0, CANNON, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(ROOK, HORSE, ELEPHANT, ADVISOR, KING, ADVISOR, ELEPHANT, HORSE, ROOK),
    )

    fun inBoard(r: Int, c: Int): Boolean = r in 0 until ROWS && c in 0 until COLS

    private fun inPalace(r: Int, c: Int, side: Int): Boolean =
        c in 3..5 && if (side == RED) r in 7..9 else r in 0..2

    private val ORTHO = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val DIAG = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val ELEPHANT_STEPS = arrayOf(2 to 2, 2 to -2, -2 to 2, -2 to -2)
    private val HORSE_STEPS = arrayOf(2 to 1, 2 to -1, -2 to 1, -2 to -1, 1 to 2, 1 to -2, -1 to 2, -1 to -2)
    private val HORSE_LEGS = arrayOf(1 to 0, 1 to 0, -1 to 0, -1 to 0, 0 to 1, 0 to -1, 0 to 1, 0 to -1)

    /** 伪合法着法：不检查走后自己是否被将军。 */
    fun generatePseudoMoves(board: Array<IntArray>, side: Int): List<Move> {
        val moves = ArrayList<Move>(48)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val p = board[r][c]
            if (p == 0 || (p > 0) != (side > 0)) continue
            when (if (p < 0) -p else p) {
                KING -> {
                    for ((dr, dc) in ORTHO) {
                        val nr = r + dr
                        val nc = c + dc
                        if (inPalace(nr, nc, side) && board[nr][nc] * side <= 0) moves.add(Move(r, c, nr, nc))
                    }
                    // 将帅照面时可直接“飞将”吃对方（同时用于搜索中惩罚主动照面）
                    val dir = if (side == RED) -1 else 1
                    var rr = r + dir
                    while (rr in 0 until ROWS) {
                        val q = board[rr][c]
                        if (q != 0) {
                            if (q == -side * KING) moves.add(Move(r, c, rr, c))
                            break
                        }
                        rr += dir
                    }
                }
                ADVISOR -> for ((dr, dc) in DIAG) {
                    val nr = r + dr
                    val nc = c + dc
                    if (inPalace(nr, nc, side) && board[nr][nc] * side <= 0) moves.add(Move(r, c, nr, nc))
                }
                ELEPHANT -> for ((dr, dc) in ELEPHANT_STEPS) {
                    val nr = r + dr
                    val nc = c + dc
                    if (!inBoard(nr, nc)) continue
                    if (side == RED && nr < 5) continue // 相不过河
                    if (side == BLACK && nr > 4) continue
                    if (board[nr][nc] * side > 0) continue
                    if (board[(r + nr) / 2][(c + nc) / 2] != 0) continue // 塞象眼
                    moves.add(Move(r, c, nr, nc))
                }
                HORSE -> for (i in HORSE_STEPS.indices) {
                    val (dr, dc) = HORSE_STEPS[i]
                    val (lr, lc) = HORSE_LEGS[i]
                    val nr = r + dr
                    val nc = c + dc
                    if (!inBoard(nr, nc)) continue
                    if (board[r + lr][c + lc] != 0) continue // 蹩马腿
                    if (board[nr][nc] * side > 0) continue
                    moves.add(Move(r, c, nr, nc))
                }
                ROOK -> for ((dr, dc) in ORTHO) {
                    var nr = r + dr
                    var nc = c + dc
                    while (inBoard(nr, nc)) {
                        val q = board[nr][nc]
                        if (q == 0) {
                            moves.add(Move(r, c, nr, nc))
                        } else {
                            if (q * side < 0) moves.add(Move(r, c, nr, nc))
                            break
                        }
                        nr += dr
                        nc += dc
                    }
                }
                CANNON -> for ((dr, dc) in ORTHO) {
                    var nr = r + dr
                    var nc = c + dc
                    var jumped = false
                    while (inBoard(nr, nc)) {
                        val q = board[nr][nc]
                        if (!jumped) {
                            if (q == 0) moves.add(Move(r, c, nr, nc)) else jumped = true
                        } else if (q != 0) {
                            if (q * side < 0) moves.add(Move(r, c, nr, nc)) // 隔一子吃
                            break
                        }
                        nr += dr
                        nc += dc
                    }
                }
                PAWN -> {
                    val f = if (side == RED) -1 else 1
                    val nr = r + f
                    if (inBoard(nr, c) && board[nr][c] * side <= 0) moves.add(Move(r, c, nr, c))
                    val crossed = if (side == RED) r <= 4 else r >= 5
                    if (crossed) { // 过河后可横走
                        if (c > 0 && board[r][c - 1] * side <= 0) moves.add(Move(r, c, r, c - 1))
                        if (c < COLS - 1 && board[r][c + 1] * side <= 0) moves.add(Move(r, c, r, c + 1))
                    }
                }
            }
        }
        return moves
    }

    /** side 的将/帅是否正被攻击（含将帅照面）。找不到将帅时按被将处理。 */
    fun isInCheck(board: Array<IntArray>, side: Int): Boolean {
        val kingPiece = side * KING
        var kr = -1
        var kc = -1
        outer@ for (r in 0 until ROWS) for (c in 0 until COLS) {
            if (board[r][c] == kingPiece) {
                kr = r
                kc = c
                break@outer
            }
        }
        if (kr < 0) return true
        // 车 / 炮 / 将帅照面（同一直线）
        for ((dr, dc) in ORTHO) {
            var nr = kr + dr
            var nc = kc + dc
            var screen = false
            while (inBoard(nr, nc)) {
                val q = board[nr][nc]
                if (q != 0) {
                    if (!screen) {
                        if (q == -side * ROOK || q == -side * KING) return true
                        screen = true
                    } else {
                        if (q == -side * CANNON) return true
                        break
                    }
                }
                nr += dr
                nc += dc
            }
        }
        // 马（马腿相对马自身的位置判断）
        for (i in HORSE_STEPS.indices) {
            val (a, b) = HORSE_STEPS[i]
            val hr = kr + a
            val hc = kc + b
            if (!inBoard(hr, hc)) continue
            if (board[hr][hc] != -side * HORSE) continue
            val legEmpty = if (a == 2 || a == -2) board[hr - a / 2][hc] == 0 else board[hr][hc - b / 2] == 0
            if (legEmpty) return true
        }
        // 兵 / 卒：正面向前将军
        val enemy = -side
        val forwardRow = if (enemy == RED) kr + 1 else kr - 1
        if (inBoard(forwardRow, kc) && board[forwardRow][kc] == enemy * PAWN) return true
        // 过河兵 / 卒可以横向将军
        val crossedRow = if (enemy == RED) kr <= 4 else kr >= 5
        if (crossedRow) {
            if (kc > 0 && board[kr][kc - 1] == enemy * PAWN) return true
            if (kc < COLS - 1 && board[kr][kc + 1] == enemy * PAWN) return true
        }
        return false
    }

    /** 完全合法着法：过滤掉走后自己被将军（含将帅照面）的“自杀”着法。 */
    fun legalMoves(board: Array<IntArray>, side: Int): List<Move> {
        val result = ArrayList<Move>()
        for (m in generatePseudoMoves(board, side)) {
            val captured = board[m.toR][m.toC]
            board[m.toR][m.toC] = board[m.fromR][m.fromC]
            board[m.fromR][m.fromC] = 0
            val bad = isInCheck(board, side)
            board[m.fromR][m.fromC] = board[m.toR][m.toC]
            board[m.toR][m.toC] = captured
            if (!bad) result.add(m)
        }
        return result
    }
}

/** 中国象棋对局引擎：持有棋盘与着法历史，负责回合流转与终局判定。 */
class XiangqiEngine {

    private val board: Array<IntArray> = XiangqiRules.initialBoard()
    private val history = ArrayDeque<Move>()

    var turn: Int = RED
        private set
    var status: XiangqiStatus = XiangqiStatus.RED_TURN
        private set

    val moveHistory: List<Move> get() = history.toList()

    fun pieceAt(r: Int, c: Int): Int = board[r][c]

    fun boardView(): List<List<Int>> = board.map { it.toList() }

    fun copyBoard(): Array<IntArray> = Array(XiangqiRules.ROWS) { board[it].copyOf() }

    fun generateMoves(side: Int = turn): List<Move> = XiangqiRules.legalMoves(board, side)

    fun isInCheck(side: Int): Boolean = XiangqiRules.isInCheck(board, side)

    fun makeMove(move: Move) {
        val captured = board[move.toR][move.toC]
        history.addLast(Move(move.fromR, move.fromC, move.toR, move.toC, captured))
        board[move.toR][move.toC] = board[move.fromR][move.fromC]
        board[move.fromR][move.fromC] = 0
        turn = -turn
        refreshStatus()
    }

    fun undoMove(): Move? {
        val m = history.removeLastOrNull() ?: return null
        board[m.fromR][m.fromC] = board[m.toR][m.toC]
        board[m.toR][m.toC] = m.captured
        turn = -turn
        status = if (turn == RED) XiangqiStatus.RED_TURN else XiangqiStatus.BLACK_TURN
        return m
    }

    /** 走完后若轮到的一方无合法着法：被将军 = 将死，否则 = 困毙，都判负。 */
    private fun refreshStatus() {
        status = when {
            generateMoves(turn).isEmpty() ->
                if (turn == RED) XiangqiStatus.BLACK_WON else XiangqiStatus.RED_WON
            turn == RED -> XiangqiStatus.RED_TURN
            else -> XiangqiStatus.BLACK_TURN
        }
    }

    fun toSnapshot(mode: String, elapsedMs: Long, difficulty: String? = null): XiangqiSnapshot {
        val rows = board.map { row ->
            row.joinToString("") { v ->
                when {
                    v == 0 -> "."
                    v > 0 -> PIECE_CHARS[v - 1].toString()
                    else -> PIECE_CHARS[-v - 1].lowercaseChar().toString()
                }
            }
        }
        return XiangqiSnapshot(
            rows = rows,
            turn = turn,
            history = history.map { MoveSnap(it.fromR, it.fromC, it.toR, it.toC, it.captured) },
            mode = mode,
            elapsedMs = elapsedMs,
            difficulty = difficulty,
        )
    }

    fun loadFromSnapshot(snap: XiangqiSnapshot) {
        for (r in 0 until XiangqiRules.ROWS) {
            val line = snap.rows.getOrNull(r) ?: continue
            for (c in 0 until XiangqiRules.COLS) {
                val ch = line.getOrNull(c) ?: '.'
                board[r][c] = when {
                    ch == '.' -> 0
                    ch.isUpperCase() -> decodePiece(ch)
                    else -> -decodePiece(ch.uppercaseChar())
                }
            }
        }
        history.clear()
        for (m in snap.history) history.addLast(Move(m.fr, m.fc, m.tr, m.tc, m.cap))
        turn = if (snap.turn < 0) BLACK else RED
        status = if (turn == RED) XiangqiStatus.RED_TURN else XiangqiStatus.BLACK_TURN
    }

    private fun decodePiece(ch: Char): Int {
        val t = PIECE_CHARS.indexOf(ch) + 1
        return if (t in 1..7) t else 0
    }

    private companion object {
        const val PIECE_CHARS = "KABNRCP" // 将/帅 仕/士 相/象 马 车 炮 兵/卒
    }
}
