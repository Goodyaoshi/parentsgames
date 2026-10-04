package com.goodyaoshi.parentsgames.feature.games.sudoku.domain

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
data class SudokuSnapshot(
    val difficulty: String,
    val givens: List<Int>,
    val values: List<Int>,
    val elapsedMs: Long = 0,
)

data class SudokuCell(
    val given: Boolean = false,
    val value: Int = 0,
    val error: Boolean = false,
)

/**
 * EASY 为 6×6（2 行×3 列的小宫，数字 1-6），对老人更友好；
 * MEDIUM/HARD 为标准 9×9，挖空数量不同。
 */
enum class Difficulty(
    val rows: Int,
    val cols: Int,
    val boxRows: Int,
    val boxCols: Int,
    val digitMax: Int,
    val blanks: Int,
    val label: String,
) {
    EASY(6, 6, 2, 3, 6, 20, "简单"),
    MEDIUM(9, 9, 3, 3, 9, 45, "中等"),
    HARD(9, 9, 3, 3, 9, 54, "困难"),
}

sealed interface GameStatus {
    data object Playing : GameStatus
    data object Won : GameStatus
}

class SudokuEngine(val difficulty: Difficulty, val seed: Long = System.currentTimeMillis()) {

    private val rng = Random(seed)
    val rows = difficulty.rows
    val cols = difficulty.cols
    val digitMax = difficulty.digitMax
    private val size = rows * cols
    private val boxCountRows = rows / difficulty.boxRows
    private val boxCountCols = cols / difficulty.boxCols

    private val puzzle = IntArray(size)
    private val solution = IntArray(size)
    private val board = IntArray(size)

    var status: GameStatus = GameStatus.Playing
        private set
    var selected: Pair<Int, Int>? = null

    init {
        generate()
    }

    private fun boxId(r: Int, c: Int): Int =
        (r / difficulty.boxRows) * boxCountCols + (c / difficulty.boxCols)

    private fun canPlace(grid: IntArray, r: Int, c: Int, v: Int): Boolean {
        for (i in 0 until cols) if (grid[r * cols + i] == v) return false
        for (i in 0 until rows) if (grid[i * cols + c] == v) return false
        val br = r / difficulty.boxRows * difficulty.boxRows
        val bc = c / difficulty.boxCols * difficulty.boxCols
        for (dr in 0 until difficulty.boxRows) for (dc in 0 until difficulty.boxCols) {
            if (grid[(br + dr) * cols + bc + dc] == v) return false
        }
        return true
    }

    /** 回溯法把整盘填满；randomize=true 用于生成终盘（候选数字随机），false 用于求解（确定性）。 */
    private fun fillGrid(grid: IntArray, randomize: Boolean): Boolean {
        fun dfs(pos: Int): Boolean {
            if (pos == size) return true
            if (grid[pos] != 0) return dfs(pos + 1)
            val r = pos / cols
            val c = pos % cols
            val digits = if (randomize) (1..digitMax).shuffled(rng) else (1..digitMax).toList()
            for (v in digits) {
                if (canPlace(grid, r, c, v)) {
                    grid[pos] = v
                    if (dfs(pos + 1)) return true
                    grid[pos] = 0
                }
            }
            return false
        }
        return dfs(0)
    }

    /** 解数计数器：最多数到 limit 个即停。挖空时要求恰好 1 个解。 */
    private fun countSolutions(grid: IntArray, limit: Int): Int {
        val rowMask = IntArray(rows)
        val colMask = IntArray(cols)
        val boxMask = IntArray(boxCountRows * boxCountCols)
        for (r in 0 until rows) for (c in 0 until cols) {
            val v = grid[r * cols + c]
            if (v != 0) {
                val bit = 1 shl v
                val b = boxId(r, c)
                if ((rowMask[r] or colMask[c] or boxMask[b]) and bit != 0) return 0
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
            }
        }
        var count = 0
        fun dfs(): Boolean {
            var p = -1
            for (i in grid.indices) if (grid[i] == 0) { p = i; break }
            if (p == -1) { count++; return count >= limit }
            val r = p / cols
            val c = p % cols
            val b = boxId(r, c)
            val used = rowMask[r] or colMask[c] or boxMask[b]
            for (v in 1..digitMax) {
                val bit = 1 shl v
                if (used and bit != 0) continue
                grid[p] = v
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
                val stop = dfs()
                grid[p] = 0
                rowMask[r] = rowMask[r] and bit.inv()
                colMask[c] = colMask[c] and bit.inv()
                boxMask[b] = boxMask[b] and bit.inv()
                if (stop) return true
            }
            return false
        }
        dfs()
        return count
    }

    private fun generate() {
        val sol = IntArray(size)
        check(fillGrid(sol, randomize = true)) { "生成终盘失败" }
        sol.copyInto(solution)
        sol.copyInto(puzzle)

        // 对称挖空：随机顺序成对移除（中心格单独），每次移除后校验唯一解，不唯一就放回。
        var removed = 0
        val tried = BooleanArray(size)
        val positions = (0 until size).shuffled(rng)
        for (p in positions) {
            if (removed >= difficulty.blanks) break
            if (tried[p] || puzzle[p] == 0) continue
            tried[p] = true
            val sym = size - 1 - p
            val pair = sym != p && !tried[sym] && puzzle[sym] != 0
            if (sym != p) tried[sym] = true
            val backupP = puzzle[p]
            val backupSym = if (pair) puzzle[sym] else 0
            puzzle[p] = 0
            if (pair) puzzle[sym] = 0
            if (countSolutions(puzzle, 2) == 1) {
                removed += if (pair) 2 else 1
            } else {
                puzzle[p] = backupP
                if (pair) puzzle[sym] = backupSym
            }
        }
        puzzle.copyInto(board)
    }

    fun snapshotBoard(): List<List<SudokuCell>> {
        return List(rows) { r ->
            List(cols) { c ->
                val i = r * cols + c
                SudokuCell(
                    given = puzzle[i] != 0,
                    value = board[i],
                    error = puzzle[i] == 0 && board[i] != 0 && board[i] != solution[i],
                )
            }
        }
    }

    fun select(r: Int, c: Int) {
        selected = r to c
    }

    /** 填入数字；同数字再点一次 = 清除。给定数字不可改。返回是否有变化。 */
    fun setNumber(r: Int, c: Int, v: Int): Boolean {
        if (status !is GameStatus.Playing) return false
        val i = r * cols + c
        if (puzzle[i] != 0) return false
        val newV = if (board[i] == v) 0 else v
        if (board[i] == newV) return false
        board[i] = newV
        checkWin()
        return true
    }

    fun erase(r: Int, c: Int): Boolean {
        if (status !is GameStatus.Playing) return false
        val i = r * cols + c
        if (puzzle[i] != 0 || board[i] == 0) return false
        board[i] = 0
        return true
    }

    /** 提示：优先填选中格（空或错），否则填任意一个空格，填的是唯一解的数字。返回填的格子。 */
    fun hint(): Pair<Int, Int>? {
        if (status !is GameStatus.Playing) return null
        var target: Int = selected?.let { (r, c) -> r * cols + c } ?: -1
        val targetOk = target in 0 until size && puzzle[target] == 0 &&
            board[target] != solution[target]
        if (!targetOk) {
            target = board.indices.firstOrNull { board[it] != solution[it] } ?: return null
        }
        board[target] = solution[target]
        val pos = target / cols to target % cols
        selected = pos
        checkWin()
        return pos
    }

    /** 「看演示」用：以当前局面为起点接着求解，返回按求解顺序的填格步骤；当前局面无解返回 null。 */
    fun solveStepsFromCurrent(): List<SudokuSolver.Step>? =
        SudokuSolver.solve(board, rows, cols, difficulty.boxRows, difficulty.boxCols, digitMax)

    /** 「看演示」用：把电脑解出的数字填进空格（给定格与已填格不动）；最后一格填完会照常触发胜利判定。 */
    fun demoFill(r: Int, c: Int, v: Int) {
        val i = r * cols + c
        if (puzzle[i] != 0 || board[i] != 0) return
        board[i] = v
        checkWin()
    }

    private fun checkWin() {
        for (i in 0 until size) {
            if (board[i] != solution[i]) return
        }
        status = GameStatus.Won
        selected = null
    }

    fun toSnapshot(elapsedMs: Long): SudokuSnapshot = SudokuSnapshot(
        difficulty = difficulty.name,
        givens = puzzle.toList(),
        values = board.toList(),
        elapsedMs = elapsedMs,
    )

    /** 恢复失败返回 false（ViewModel 会丢弃快照开新局）。 */
    fun loadFromSnapshot(snap: SudokuSnapshot): Boolean {
        if (snap.givens.size != size || snap.values.size != size) return false
        if (snap.givens.any { it < 0 || it > digitMax } || snap.values.any { it < 0 || it > digitMax }) return false
        for (i in 0 until size) {
            if (snap.values[i] != 0 && snap.givens[i] != 0 && snap.values[i] != snap.givens[i]) return false
        }
        // 题目必须恰好唯一解，否则旧数据不可信
        val probe = snap.givens.toIntArray()
        if (countSolutions(probe, 2) != 1) return false
        val work = snap.givens.toIntArray()
        if (!fillGrid(work, randomize = false)) return false
        work.copyInto(solution)
        snap.givens.toIntArray().copyInto(puzzle)
        snap.values.toIntArray().copyInto(board)
        selected = null
        status = GameStatus.Playing
        return true
    }
}
