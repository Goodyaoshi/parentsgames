package com.goodyaoshi.parentsgames.feature.games.xiangqi.domain

import kotlin.math.abs
import kotlin.random.Random

/** 人机对战难度：简单 / 中等 / 困难（双人模式不适用）。 */
enum class XiangqiDifficulty(val label: String) {
    EASY("简单"),
    MEDIUM("中等"),
    HARD("困难"),
}

/**
 * 中国象棋 AI：negamax + alpha-beta 剪枝，按难度调整搜索深度，
 * 着法排序按“被吃子价值”从大到小（吃子优先）。估值 = 子力价值 + 少量位置分。
 *
 * 搜索在纯数组棋盘上进行，直接复用 [XiangqiRules] 的着法生成；
 * 根节点用完全合法着法，内层节点用伪合法着法 + “吃王即胜”判定保证正确性。
 *
 * 简单档面向老人陪练：约 38% 的棋直接从合法着法里随机走（只排除走完立即被将死的着法），
 * 其余走深度 1 但给评估值加大随机噪声，看不出两三步的战术，也没有主动做杀能力。
 */
object XiangqiAi {

    // 各难度的搜索深度：简单浅搜 + 偶尔随机，困难为完整强度
    const val EASY_DEPTH = 1
    const val MEDIUM_DEPTH = 2
    const val HARD_DEPTH = 3

    /** 简单难度随机走子的概率（约三四成的棋随机应付，其余用带噪声的浅搜索）。 */
    private const val EASY_RANDOM_PROBABILITY = 0.38

    // 简单难度浅搜索时叠加在叶子评估上的随机噪声幅度（±150~200 分量级）
    private const val EASY_NOISE = 175

    // 帅/将 仕/士 相/象 马 车 炮 兵/卒（下标对应棋子类型）
    private val PIECE_VALUE = intArrayOf(0, 100000, 200, 200, 400, 900, 450, 100)

    private const val MATE = 900000
    private const val INFINITE = 1000000000

    /** 按难度为 side 找一步棋；不修改传入的棋盘，无棋可走时返回 null。 */
    fun findMove(board: Array<IntArray>, side: Int, difficulty: XiangqiDifficulty): Move? = when (difficulty) {
        XiangqiDifficulty.EASY ->
            if (Random.nextDouble() < EASY_RANDOM_PROBABILITY) findEasyRandomMove(board, side)
            else findBestMove(board, side, EASY_DEPTH, EASY_NOISE)
        XiangqiDifficulty.MEDIUM -> findBestMove(board, side, MEDIUM_DEPTH)
        XiangqiDifficulty.HARD -> findBestMove(board, side, HARD_DEPTH)
    }

    /**
     * 为 side 找一步最佳着法；不会修改传入的棋盘。无棋可走时返回 null。
     * @param noise 叠加在每个叶子评估上的随机噪声幅度，0 表示不加（中等/困难/提示用）。
     */
    fun findBestMove(board: Array<IntArray>, side: Int, depth: Int = HARD_DEPTH, noise: Int = 0): Move? {
        val b = Array(board.size) { board[it].copyOf() }
        val moves = XiangqiRules.legalMoves(b, side)
        if (moves.isEmpty()) return null
        val ordered = moves.sortedByDescending { PIECE_VALUE[abs(b[it.toR][it.toC])] }
        var best = ordered.first()
        var alpha = -INFINITE
        for (m in ordered) {
            val captured = b[m.toR][m.toC]
            b[m.toR][m.toC] = b[m.fromR][m.fromC]
            b[m.fromR][m.fromC] = 0
            val score = -negamax(b, -side, depth - 1, -INFINITE, -alpha, 1, noise)
            b[m.fromR][m.fromC] = b[m.toR][m.toC]
            b[m.toR][m.toC] = captured
            if (score > alpha) {
                alpha = score
                best = m
            }
        }
        return best
    }

    /**
     * 简单难度的随机走子：从全部合法着法里随机挑，只排除走完立即被将死的着法
     * （直接送将的着法本来就不是合法着法）；全都立即被将死时退化为随便走一步。
     * 允许白丢大子，这是简单档“能被老人赢下”的关键。
     */
    private fun findEasyRandomMove(board: Array<IntArray>, side: Int): Move? {
        val b = Array(board.size) { board[it].copyOf() }
        val moves = XiangqiRules.legalMoves(b, side)
        if (moves.isEmpty()) return null
        val notMated = moves.filter { m ->
            val captured = b[m.toR][m.toC]
            b[m.toR][m.toC] = b[m.fromR][m.fromC]
            b[m.fromR][m.fromC] = 0
            val mated = isMatedInOne(b, side)
            b[m.fromR][m.fromC] = b[m.toR][m.toC]
            b[m.toR][m.toC] = captured
            !mated
        }
        return (notMated.ifEmpty { moves }).random()
    }

    /** side 刚走完一步的局面上，对方是否存在一步制胜（走完 side 无合法着法 = 将死或困毙，均判负）。 */
    private fun isMatedInOne(board: Array<IntArray>, side: Int): Boolean {
        for (m in XiangqiRules.legalMoves(board, -side)) {
            val captured = board[m.toR][m.toC]
            board[m.toR][m.toC] = board[m.fromR][m.fromC]
            board[m.fromR][m.fromC] = 0
            val mated = XiangqiRules.legalMoves(board, side).isEmpty()
            board[m.fromR][m.fromC] = board[m.toR][m.toC]
            board[m.toR][m.toC] = captured
            if (mated) return true
        }
        return false
    }

    private fun negamax(
        board: Array<IntArray>,
        side: Int,
        depth: Int,
        alpha: Int,
        beta: Int,
        ply: Int,
        noise: Int,
    ): Int {
        if (depth <= 0) {
            val e = evaluate(board, side)
            return if (noise == 0) e else e + Random.nextInt(-noise, noise + 1)
        }
        val moves = XiangqiRules.generatePseudoMoves(board, side)
        if (moves.isEmpty()) return -MATE + ply
        val ordered = moves.sortedByDescending { PIECE_VALUE[abs(board[it.toR][it.toC])] }
        var best = -INFINITE
        var a = alpha
        for (m in ordered) {
            val captured = board[m.toR][m.toC]
            var score: Int
            if (captured == -side * KING) {
                // 直接吃掉对方将/帅 = 胜（越快越好）
                score = MATE - ply
            } else {
                board[m.toR][m.toC] = board[m.fromR][m.fromC]
                board[m.fromR][m.fromC] = 0
                score = -negamax(board, -side, depth - 1, -beta, -a, ply + 1, noise)
                board[m.fromR][m.fromC] = board[m.toR][m.toC]
                board[m.toR][m.toC] = captured
            }
            if (score > best) best = score
            if (best > a) a = best
            if (a >= beta) break
        }
        return best
    }

    /** 局面评估：子力价值 + 少量位置分，从 side 视角返回。 */
    private fun evaluate(board: Array<IntArray>, side: Int): Int {
        var score = 0
        for (r in 0 until XiangqiRules.ROWS) {
            val row = board[r]
            for (c in 0 until XiangqiRules.COLS) {
                val p = row[c]
                if (p == 0) continue
                val type = abs(p)
                var v = PIECE_VALUE[type]
                when (type) {
                    PAWN -> {
                        val crossed = if (p > 0) r <= 4 else r >= 5
                        if (crossed) {
                            v += 100 + (if (p > 0) 4 - r else r - 5) * 12 // 越深入越好
                            if (c in 2..6) v += 6
                        }
                    }
                    HORSE -> if (r in 2..7 && c in 2..6) v += 14
                    CANNON -> if (c in 2..6) v += 8
                    ROOK -> if ((p > 0 && r <= 4) || (p < 0 && r >= 5)) v += 15
                    else -> {}
                }
                score += if (p > 0) v else -v
            }
        }
        return if (side == RED) score else -score
    }
}
