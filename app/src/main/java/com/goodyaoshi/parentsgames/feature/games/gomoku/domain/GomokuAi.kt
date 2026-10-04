package com.goodyaoshi.parentsgames.feature.games.gomoku.domain

import kotlin.math.abs
import kotlin.random.Random

/** 人机对战难度：简单 / 中等 / 困难（双人模式不适用）。 */
enum class GomokuDifficulty(val label: String) {
    EASY("简单"),
    MEDIUM("中等"),
    HARD("困难"),
}

/**
 * 启发式五子棋 AI，按难度分级：
 * - 简单：面向老人陪练，约 35% 概率随机从候选点落子；其余时候自己能连五就连五，
 *   防守上只堵对方冲四及以上的眼前威胁（成五、活四、冲四），活三故意漏堵，不做连招；
 * - 中等：单层评分（进攻分 + 防守分），会堵冲四、做活三；
 * - 困难：在单层评分上再加一层对手最佳应对（两层搜索），活四、双三等强威胁都会防。
 */
object GomokuAi {

    // 难度对应层数：中等为单层评分，困难再往前看一步对方回应
    private const val MEDIUM_PLY = 1
    private const val HARD_PLY = 2

    /** 简单难度随机落子的概率。 */
    private const val EASY_RANDOM_PROBABILITY = 0.35

    private const val FIVE = 1_000_000        // 成五
    private const val LIVE_FOUR = 100_000     // 活四
    private const val RUSH_FOUR = 10_000      // 冲四
    private const val LIVE_THREE = 9_000      // 活三
    private const val SLEEP_THREE = 1_000     // 眠三
    private const val LIVE_TWO = 500          // 活二
    private const val SLEEP_TWO = 100         // 眠二
    private const val SINGLE = 10             // 孤子

    private val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

    /** 按难度给 side 挑一步落点（棋盘交点下标），无候选点时返回 null。 */
    fun findMove(board: IntArray, side: Int, difficulty: GomokuDifficulty): Int? = when (difficulty) {
        GomokuDifficulty.EASY -> findEasyMove(board, side)
        GomokuDifficulty.MEDIUM -> findBestMove(board, side, MEDIUM_PLY)
        GomokuDifficulty.HARD -> findBestMove(board, side, HARD_PLY)
    }

    /**
     * 给 side 挑一步最佳落点；不会修改传入的棋盘。
     * ply = 1 为单层评分，ply = 2 额外扣除对方最强回应。无候选点时返回 null。
     */
    fun findBestMove(board: IntArray, side: Int, ply: Int = HARD_PLY): Int? {
        val n = GomokuEngine.SIZE
        if (board.size != n * n) return null
        if (board.all { it == GomokuEngine.EMPTY }) return n * n / 2

        val opponent = if (side == GomokuEngine.BLACK) GomokuEngine.WHITE else GomokuEngine.BLACK
        // 困难难度需要在假想落子后推演对方回应，用副本避免改动调用方的棋盘
        val work = if (ply >= HARD_PLY) board.copyOf() else board
        var bestIndex: Int? = null
        var bestScore = Int.MIN_VALUE
        for (index in 0 until n * n) {
            if (work[index] != GomokuEngine.EMPTY) continue
            if (!hasStoneNearby(work, index)) continue
            val attack = scorePoint(work, index, side)
            // 防守分略低于进攻分：自己能成五时优先赢，其余情况优先堵住对方的四冲
            val defend = scorePoint(work, index, opponent) * 9 / 10
            var total = attack + defend + centerBonus(index)
            // 己方此手能直接成五时不必再看对方；否则扣除对方最佳回应的价值
            if (ply >= HARD_PLY && attack < FIVE) {
                work[index] = side
                total -= bestReplyScore(work, opponent, side)
                work[index] = GomokuEngine.EMPTY
            }
            if (total > bestScore) {
                bestScore = total
                bestIndex = index
            }
        }
        return bestIndex
    }

    /**
     * 简单难度：35% 概率随机从候选点落子；其余时候进攻照常（自己能连五就连五），
     * 但防守只看眼前直接威胁——对方冲四及以上（成五、活四、冲四）才堵，活三故意漏堵。
     */
    private fun findEasyMove(board: IntArray, side: Int): Int? {
        val n = GomokuEngine.SIZE
        if (board.size != n * n) return null
        if (board.all { it == GomokuEngine.EMPTY }) return n * n / 2

        val candidates = ArrayList<Int>()
        for (index in 0 until n * n) {
            if (board[index] == GomokuEngine.EMPTY && hasStoneNearby(board, index)) candidates.add(index)
        }
        if (candidates.isEmpty()) return null
        if (Random.nextDouble() < EASY_RANDOM_PROBABILITY) return candidates.random()

        val opponent = if (side == GomokuEngine.BLACK) GomokuEngine.WHITE else GomokuEngine.BLACK
        var bestIndex: Int? = null
        var bestScore = Int.MIN_VALUE
        for (index in candidates) {
            val attack = scorePoint(board, index, side)
            val threat = scorePoint(board, index, opponent)
            // 只堵冲四及以上：对方要做活三、眠三以下的棋假装看不见，也不做防守反击
            val defend = if (threat >= RUSH_FOUR) threat * 9 / 10 else 0
            val total = attack + defend + centerBonus(index)
            if (total > bestScore) {
                bestScore = total
                bestIndex = index
            }
        }
        return bestIndex
    }

    /** 己方落子后，对方最佳一步的价值（对方视角：进攻分 + 堵截分），用于困难档两层推演。 */
    private fun bestReplyScore(board: IntArray, opponent: Int, me: Int): Int {
        val n = GomokuEngine.SIZE
        var best = 0
        for (index in 0 until n * n) {
            if (board[index] != GomokuEngine.EMPTY) continue
            if (!hasStoneNearby(board, index)) continue
            val v = scorePoint(board, index, opponent) + scorePoint(board, index, me) * 9 / 10 + centerBonus(index)
            if (v > best) best = v
        }
        return best
    }

    /** 只考虑与已有棋子切比雪夫距离 ≤ 2 的空点，缩小搜索范围。 */
    private fun hasStoneNearby(board: IntArray, index: Int): Boolean {
        val n = GomokuEngine.SIZE
        val r = index / n
        val c = index % n
        for (dr in -2..2) {
            for (dc in -2..2) {
                if (dr == 0 && dc == 0) continue
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until n && cc in 0 until n && board[rr * n + cc] != GomokuEngine.EMPTY) {
                    return true
                }
            }
        }
        return false
    }

    /** 假设 side 落子在 index，扫描四个方向的连子模式并打分（取最强与次强方向）。 */
    private fun scorePoint(board: IntArray, index: Int, side: Int): Int {
        val n = GomokuEngine.SIZE
        val r = index / n
        val c = index % n
        val scores = IntArray(DIRECTIONS.size)
        DIRECTIONS.forEachIndexed { d, (dr, dc) ->
            var count = 1
            var openEnds = 0
            var rr = r + dr
            var cc = c + dc
            while (rr in 0 until n && cc in 0 until n && board[rr * n + cc] == side) {
                count++
                rr += dr
                cc += dc
            }
            if (rr in 0 until n && cc in 0 until n && board[rr * n + cc] == GomokuEngine.EMPTY) openEnds++
            rr = r - dr
            cc = c - dc
            while (rr in 0 until n && cc in 0 until n && board[rr * n + cc] == side) {
                count++
                rr -= dr
                cc -= dc
            }
            if (rr in 0 until n && cc in 0 until n && board[rr * n + cc] == GomokuEngine.EMPTY) openEnds++
            scores[d] = lineScore(count, openEnds)
        }
        scores.sort()
        var total = scores[3]
        if (scores[2] >= LIVE_THREE) {
            total += scores[2] * 8  // 双向威胁（双活三、四三等）大幅加分
        } else if (scores[2] >= SLEEP_THREE) {
            total += scores[2]
        }
        return total
    }

    private fun lineScore(count: Int, openEnds: Int): Int = when {
        count >= 5 -> FIVE
        count == 4 && openEnds == 2 -> LIVE_FOUR
        count == 4 && openEnds == 1 -> RUSH_FOUR
        count == 3 && openEnds == 2 -> LIVE_THREE
        count == 3 && openEnds == 1 -> SLEEP_THREE
        count == 2 && openEnds == 2 -> LIVE_TWO
        count == 2 && openEnds == 1 -> SLEEP_TWO
        else -> SINGLE
    }

    /** 微小的居中加分，评分相同时偏向棋盘中腹。 */
    private fun centerBonus(index: Int): Int {
        val n = GomokuEngine.SIZE
        val r = index / n
        val c = index % n
        val dist = maxOf(abs(r - n / 2), abs(c - n / 2))
        return (n / 2 - dist) * 2 + 2
    }
}
