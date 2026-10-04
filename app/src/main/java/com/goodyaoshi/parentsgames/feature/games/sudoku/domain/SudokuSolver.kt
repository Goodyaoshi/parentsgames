package com.goodyaoshi.parentsgames.feature.games.sudoku.domain

/**
 * 数独回溯求解器（「看演示」用）：以当前局面为起点接着解。
 * 每次挑候选数字最少的空格（MRV）往下试，兼顾速度与演示观赏性；
 * 解出后返回按求解顺序要填入的格子列表；当前局面无解（含已冲突）返回 null。
 */
object SudokuSolver {

    /** 一步演示填格：坐标 + 要填入的数字。 */
    data class Step(val row: Int, val col: Int, val value: Int)

    fun solve(
        start: IntArray,
        rows: Int,
        cols: Int,
        boxRows: Int,
        boxCols: Int,
        digitMax: Int,
    ): List<Step>? {
        val size = rows * cols
        if (start.size != size) return null
        val grid = start.copyOf()
        val boxCountCols = cols / boxCols
        val rowMask = IntArray(rows)
        val colMask = IntArray(cols)
        val boxMask = IntArray((rows / boxRows) * boxCountCols)

        // 先把已有数字入掩码；出现同行/列/宫重复即当前局面冲突，无解
        for (r in 0 until rows) for (c in 0 until cols) {
            val v = grid[r * cols + c]
            if (v == 0) continue
            if (v < 1 || v > digitMax) return null
            val bit = 1 shl v
            val b = (r / boxRows) * boxCountCols + c / boxCols
            if ((rowMask[r] or colMask[c] or boxMask[b]) and bit != 0) return null
            rowMask[r] = rowMask[r] or bit
            colMask[c] = colMask[c] or bit
            boxMask[b] = boxMask[b] or bit
        }

        val steps = mutableListOf<Step>()

        fun dfs(): Boolean {
            var best = -1
            var bestMask = 0
            var bestCount = digitMax + 1
            for (i in 0 until size) {
                if (grid[i] != 0) continue
                val r = i / cols
                val c = i % cols
                val used = rowMask[r] or colMask[c] or boxMask[(r / boxRows) * boxCountCols + c / boxCols]
                var count = 0
                var mask = 0
                for (v in 1..digitMax) {
                    val bit = 1 shl v
                    if (used and bit == 0) { count++; mask = mask or bit }
                }
                if (count == 0) return false
                if (count < bestCount) {
                    bestCount = count
                    best = i
                    bestMask = mask
                    if (count == 1) break
                }
            }
            if (best == -1) return true // 填满
            val r = best / cols
            val c = best % cols
            val b = (r / boxRows) * boxCountCols + c / boxCols
            for (v in 1..digitMax) {
                val bit = 1 shl v
                if (bestMask and bit == 0) continue
                grid[best] = v
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
                if (dfs()) {
                    // 成功路径回溯时记录，最终反转即为电脑真实的填格顺序
                    steps.add(Step(r, c, v))
                    return true
                }
                grid[best] = 0
                rowMask[r] = rowMask[r] and bit.inv()
                colMask[c] = colMask[c] and bit.inv()
                boxMask[b] = boxMask[b] and bit.inv()
            }
            return false
        }

        return if (dfs()) steps.reversed() else null
    }
}
