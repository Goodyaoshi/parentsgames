package com.goodyaoshi.parentsgames.feature.games.minesweeper.domain

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
data class CellSnap(val mine: Boolean, val revealed: Boolean, val flagged: Boolean, val adj: Int)

@Serializable
data class MinesweeperSnapshot(
    val difficulty: String,
    val grid: List<List<CellSnap>>,
    val flagsUsed: Int,
    val minesPlaced: Boolean,
)

data class Cell(
    val isMine: Boolean = false,
    val isRevealed: Boolean = false,
    val isFlagged: Boolean = false,
    val adjacentMines: Int = 0,
)

enum class Difficulty(val rows: Int, val cols: Int, val mines: Int, val label: String) {
    EASY(6, 6, 5, "简单"),
    MEDIUM(8, 8, 10, "中等"),
    HARD(10, 12, 20, "困难"),
}

sealed interface GameStatus {
    data object Playing : GameStatus
    data object Won : GameStatus
    data class Lost(val mineRow: Int, val mineCol: Int) : GameStatus
}

/** 提示/演示共用的一步：FLAG = 给该格插旗，REVEAL = 翻开该格。 */
enum class HintKind { FLAG, REVEAL }

data class HintMove(val kind: HintKind, val row: Int, val col: Int)

class MinesweeperEngine(val difficulty: Difficulty, val seed: Long = System.currentTimeMillis()) {

    private val rng = Random(seed)
    private val grid: Array<Array<Cell>> = Array(difficulty.rows) {
        Array(difficulty.cols) { Cell() }
    }
    private var minesPlaced = false
    var status: GameStatus = GameStatus.Playing; private set
    var flagsUsed: Int = 0; private set

    val rows: Int get() = difficulty.rows
    val cols: Int get() = difficulty.cols
    val totalMines: Int get() = difficulty.mines
    val flagsRemaining: Int get() = totalMines - flagsUsed

    fun snapshot(): List<List<Cell>> = grid.map { it.toList() }

    fun toSnapshot(): MinesweeperSnapshot = MinesweeperSnapshot(
        difficulty = difficulty.name,
        grid = grid.map { row -> row.map { CellSnap(it.isMine, it.isRevealed, it.isFlagged, it.adjacentMines) } },
        flagsUsed = flagsUsed,
        minesPlaced = minesPlaced,
    )

    fun loadFromSnapshot(snap: MinesweeperSnapshot) {
        for (r in 0 until rows) for (c in 0 until cols) {
            val sc = snap.grid.getOrNull(r)?.getOrNull(c) ?: continue
            grid[r][c] = Cell(sc.mine, sc.revealed, sc.flagged, sc.adj)
        }
        flagsUsed = snap.flagsUsed
        minesPlaced = snap.minesPlaced
        // 快照只在对局进行中保存，恢复后必然回到可玩状态（演示模式退出恢复也依赖这一点）
        status = GameStatus.Playing
    }

    fun tap(r: Int, c: Int): Boolean {
        if (status !is GameStatus.Playing) return false
        val cell = grid[r][c]
        if (cell.isFlagged || cell.isRevealed) return false
        if (!minesPlaced) {
            placeMines(safeR = r, safeC = c)
            minesPlaced = true
        }
        if (grid[r][c].isMine) {
            // Revela todas as minas
            for (i in 0 until rows) for (j in 0 until cols) {
                if (grid[i][j].isMine) grid[i][j] = grid[i][j].copy(isRevealed = true)
            }
            status = GameStatus.Lost(r, c)
            return true
        }
        floodReveal(r, c)
        checkWin()
        return true
    }

    fun toggleFlag(r: Int, c: Int): Boolean {
        if (status !is GameStatus.Playing) return false
        val cell = grid[r][c]
        if (cell.isRevealed) return false
        if (cell.isFlagged) {
            grid[r][c] = cell.copy(isFlagged = false)
            flagsUsed--
        } else {
            if (flagsUsed >= totalMines) return false
            grid[r][c] = cell.copy(isFlagged = true)
            flagsUsed++
        }
        return true
    }

    private fun placeMines(safeR: Int, safeC: Int) {
        val safeZone = mutableSetOf<Pair<Int, Int>>()
        for (dr in -1..1) for (dc in -1..1) {
            val r = safeR + dr; val c = safeC + dc
            if (r in 0 until rows && c in 0 until cols) safeZone.add(r to c)
        }
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until rows) for (j in 0 until cols) {
            if ((i to j) !in safeZone) candidates.add(i to j)
        }
        candidates.shuffle(rng)
        for ((r, c) in candidates.take(totalMines)) {
            grid[r][c] = grid[r][c].copy(isMine = true)
        }
        // Calcular adjacencies
        for (i in 0 until rows) for (j in 0 until cols) {
            if (grid[i][j].isMine) continue
            var count = 0
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val ni = i + dr; val nj = j + dc
                if (ni in 0 until rows && nj in 0 until cols && grid[ni][nj].isMine) count++
            }
            grid[i][j] = grid[i][j].copy(adjacentMines = count)
        }
    }

    private fun floodReveal(r: Int, c: Int) {
        val queue: ArrayDeque<Pair<Int, Int>> = ArrayDeque()
        queue.add(r to c)
        while (queue.isNotEmpty()) {
            val (i, j) = queue.removeFirst()
            val cell = grid[i][j]
            if (cell.isRevealed || cell.isFlagged || cell.isMine) continue
            grid[i][j] = cell.copy(isRevealed = true)
            if (cell.adjacentMines == 0) {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val ni = i + dr; val nj = j + dc
                    if (ni in 0 until rows && nj in 0 until cols) {
                        queue.add(ni to nj)
                    }
                }
            }
        }
    }

    private fun checkWin() {
        for (i in 0 until rows) for (j in 0 until cols) {
            val cell = grid[i][j]
            if (!cell.isMine && !cell.isRevealed) return
        }
        status = GameStatus.Won
    }

    // ----- 提示与演示共用 -----

    private fun neighborsOf(r: Int, c: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until rows && nc in 0 until cols) out.add(nr to nc)
        }
        return out
    }

    /**
     * 按优先级找一步"安全的棋"：
     * 1) 可推断的雷：某已开数字格的未开邻居数 == 数字-已插旗数 → 剩下的未开未插旗邻居全是雷，帮插一面旗；
     * 2) 可推断的安全格：某已开数字格的已插旗数 == 数字 → 其余未开邻居全安全，帮翻开一个；
     * 3) 兜底：从内部雷区挑一个真正的非雷格翻开（优先挨着已开格子多、其次靠中间，像"帮一把"而不是乱开）。
     * @param verifyTruth true 时用真实雷区校验推断结果（演示模式用：玩家插错的旗不能把演示带进雷里）。
     */
    fun findHint(verifyTruth: Boolean = false): HintMove? {
        if (status !is GameStatus.Playing) return null
        // 1. 可推断的雷 → 插旗（旗用完就没法帮插，跳过这一类）
        if (flagsRemaining > 0) {
            for (r in 0 until rows) for (c in 0 until cols) {
                val cell = grid[r][c]
                if (!cell.isRevealed || cell.adjacentMines == 0) continue
                val ns = neighborsOf(r, c)
                val hidden = ns.count { !grid[it.first][it.second].isRevealed }
                if (hidden != cell.adjacentMines) continue
                val target = ns.firstOrNull {
                    !grid[it.first][it.second].isRevealed && !grid[it.first][it.second].isFlagged
                } ?: continue
                if (!verifyTruth || grid[target.first][target.second].isMine) {
                    return HintMove(HintKind.FLAG, target.first, target.second)
                }
            }
        }
        // 2. 可推断的安全格 → 翻开一个
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = grid[r][c]
            if (!cell.isRevealed || cell.adjacentMines == 0) continue
            val ns = neighborsOf(r, c)
            val flagged = ns.count { grid[it.first][it.second].isFlagged }
            if (flagged != cell.adjacentMines) continue
            val target = ns.firstOrNull {
                !grid[it.first][it.second].isRevealed && !grid[it.first][it.second].isFlagged
            } ?: continue
            if (!verifyTruth || !grid[target.first][target.second].isMine) {
                return HintMove(HintKind.REVEAL, target.first, target.second)
            }
        }
        // 3. 兜底：真正的非雷格，优先挨着已开格子多、其次靠中间
        var best: HintMove? = null
        var bestScore = -1
        var bestDist = Double.MAX_VALUE
        val midR = (rows - 1) / 2.0
        val midC = (cols - 1) / 2.0
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = grid[r][c]
            if (cell.isMine || cell.isRevealed || cell.isFlagged) continue
            var score = 0
            for ((nr, nc) in neighborsOf(r, c)) {
                if (grid[nr][nc].isRevealed) score++
            }
            val dist = (r - midR) * (r - midR) + (c - midC) * (c - midC)
            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                bestScore = score
                bestDist = dist
                best = HintMove(HintKind.REVEAL, r, c)
            }
        }
        return best
    }
}
