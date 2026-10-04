package com.goodyaoshi.parentsgames.feature.games.match3.domain

import kotlin.random.Random

import kotlinx.serialization.Serializable

enum class GemType { RAIN, LEAF, BLOOM, SUN, MOON, EMBER }

@Serializable
data class Match3Snapshot(
    val cells: List<String>,   // ROWS * COLS encoded como "type:variant"
    val score: Long,
    val stage: Int,
)

/**
 * Conteúdo de uma célula. Pode ser gema normal, gema especial ou vazio.
 * Especiais detonam efeito ao serem incluídas em um match (mesma cor da gema base).
 */
sealed class CellContent {
    abstract val type: GemType?

    data class Normal(override val type: GemType) : CellContent()
    /** Limpa toda a linha quando ativada. */
    data class FlameH(override val type: GemType) : CellContent()
    /** Limpa toda a coluna. */
    data class FlameV(override val type: GemType) : CellContent()
    /** Limpa um 3x3 ao redor. */
    data class Bomb(override val type: GemType) : CellContent()

    /** "Vazio" só ocorre em transições; tabuleiro estável é sempre preenchido. */
    object Empty : CellContent() {
        override val type: GemType? = null
    }
}

const val BOARD_SIZE = 6

/** 6x6=36 格盘面比 8x8 小，颜色减为 5 种以保证匹配频率（ elders 友好）。 */
val PLAYABLE_GEMS: List<GemType> = GemType.entries.take(5)

class Match3Engine(seed: Long = System.currentTimeMillis()) {
    private val rng = Random(seed)
    private val board: Array<Array<CellContent>> = Array(BOARD_SIZE) {
        Array(BOARD_SIZE) { CellContent.Empty as CellContent }
    }

    var score: Long = 0L; private set
    var stage: Int = 1; private set
    private val stageTargets = longArrayOf(0, 5_000, 12_000, 25_000, 50_000, 100_000)

    init { initialize() }

    private fun initialize() {
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            board[r][c] = CellContent.Normal(pickAvoidingMatch(r, c))
        }
    }

    private fun pickAvoidingMatch(r: Int, c: Int): GemType {
        val all = PLAYABLE_GEMS.toMutableSet()
        if (c >= 2) {
            val a = board[r][c - 1].type; val b = board[r][c - 2].type
            if (a != null && a == b) all.remove(a)
        }
        if (r >= 2) {
            val a = board[r - 1][c].type; val b = board[r - 2][c].type
            if (a != null && a == b) all.remove(a)
        }
        return all.random(rng)
    }

    fun snapshot(): List<List<CellContent>> = board.map { it.toList() }

    /** Verifica se um swap formaria match SEM aplicar de fato. */
    fun peekSwapWouldMatch(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        if (!areAdjacent(r1, c1, r2, c2)) return false
        val a = board[r1][c1]; val b = board[r2][c2]
        if (a is CellContent.Empty || b is CellContent.Empty) return false
        board[r1][c1] = b; board[r2][c2] = a
        val matches = findAllMatches()
        // Reverte
        board[r1][c1] = a; board[r2][c2] = b
        return matches.isNotEmpty()
    }

    fun trySwap(r1: Int, c1: Int, r2: Int, c2: Int): SwapResult {
        if (!areAdjacent(r1, c1, r2, c2)) return SwapResult.Invalid
        val a = board[r1][c1]; val b = board[r2][c2]
        if (a is CellContent.Empty || b is CellContent.Empty) return SwapResult.Invalid
        board[r1][c1] = b; board[r2][c2] = a
        val matches = findAllMatches()
        if (matches.isEmpty()) {
            board[r1][c1] = a; board[r2][c2] = b
            return SwapResult.NoMatch
        }
        var cascadeLevel = 0
        var totalGained = 0L
        var currentMatches = matches
        while (currentMatches.isNotEmpty()) {
            val gained = removeAndScore(currentMatches, cascadeLevel)
            totalGained += gained
            applyGravity()
            currentMatches = findAllMatches()
            cascadeLevel++
        }
        score += totalGained
        return SwapResult.Matched(totalGained, cascadeLevel)
    }

    /**
     * Variante "step-by-step" para animação. Aplica o swap e retorna se formou matches,
     * SEM remover ainda. UI deve então chamar [clearMatchesStep] / [applyGravityStep] em loop.
     */
    fun applySwapOnly(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        if (!areAdjacent(r1, c1, r2, c2)) return false
        val a = board[r1][c1]; val b = board[r2][c2]
        if (a is CellContent.Empty || b is CellContent.Empty) return false
        board[r1][c1] = b; board[r2][c2] = a
        val matches = findAllMatches()
        if (matches.cells.isEmpty()) {
            // Reverter
            board[r1][c1] = a; board[r2][c2] = b
            return false
        }
        return true
    }

    /** Encontra matches atuais e marca-os como Empty. Retorna info p/ animação. */
    fun clearMatchesStep(cascadeLevel: Int): ClearResult? {
        val matches = findAllMatches()
        if (matches.cells.isEmpty()) return null
        val gained = removeAndScore(matches, cascadeLevel)
        score += gained
        return ClearResult(cells = matches.cells.toSet(), gained = gained)
    }

    /**
     * Aplica gravidade após [clearMatchesStep] e gera novas gemas no topo.
     * Retorna mapa: (rDestino, cDestino) → rOrigem (de onde a gema caiu).
     * Cells que receberam gemas novas têm rOrigem = -1.
     */
    fun applyGravityStep(): Map<Pair<Int, Int>, Int> {
        val moves = mutableMapOf<Pair<Int, Int>, Int>()
        for (c in 0 until BOARD_SIZE) {
            // Para cada coluna, colapsa células não-vazias para baixo
            val column = (0 until BOARD_SIZE).map { board[it][c] }
            val nonEmptyWithOrigin = mutableListOf<Pair<CellContent, Int>>()
            for ((idx, cell) in column.withIndex()) {
                if (cell !is CellContent.Empty) nonEmptyWithOrigin.add(cell to idx)
            }
            // Preenche de baixo pra cima
            var writeRow = BOARD_SIZE - 1
            for ((cell, originRow) in nonEmptyWithOrigin.reversed()) {
                if (writeRow != originRow) {
                    board[writeRow][c] = cell
                    board[originRow][c] = CellContent.Empty
                    moves[(writeRow to c)] = originRow
                }
                writeRow--
            }
            // Topo recebe novas gemas
            for (r in writeRow downTo 0) {
                board[r][c] = CellContent.Normal(PLAYABLE_GEMS.random(rng))
                moves[(r to c)] = -1
            }
        }
        return moves
    }

    data class ClearResult(val cells: Set<Pair<Int, Int>>, val gained: Long)

    private fun areAdjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean =
        (r1 == r2 && kotlin.math.abs(c1 - c2) == 1) ||
            (c1 == c2 && kotlin.math.abs(r1 - r2) == 1)

    /** Encontra runs ≥3 e marca células incluindo runs especiais. */
    internal fun findAllMatches(): MatchResult {
        val cells = mutableSetOf<Pair<Int, Int>>()
        val runs = mutableListOf<Run>()

        // horizontais
        for (r in 0 until BOARD_SIZE) {
            var startC = 0
            for (c in 1..BOARD_SIZE) {
                val same = c < BOARD_SIZE &&
                    board[r][c].type != null &&
                    board[r][c].type == board[r][c - 1].type
                if (!same) {
                    val len = c - startC
                    if (len >= 3) {
                        val type = board[r][startC].type!!
                        runs.add(Run(orientation = RunOrientation.HORIZONTAL, row = r, col = startC, length = len, type = type))
                        for (k in 0 until len) cells.add(r to (startC + k))
                    }
                    startC = c
                }
            }
        }
        // verticais
        for (c in 0 until BOARD_SIZE) {
            var startR = 0
            for (r in 1..BOARD_SIZE) {
                val same = r < BOARD_SIZE &&
                    board[r][c].type != null &&
                    board[r][c].type == board[r - 1][c].type
                if (!same) {
                    val len = r - startR
                    if (len >= 3) {
                        val type = board[startR][c].type!!
                        runs.add(Run(orientation = RunOrientation.VERTICAL, row = startR, col = c, length = len, type = type))
                        for (k in 0 until len) cells.add((startR + k) to c)
                    }
                    startR = r
                }
            }
        }
        return MatchResult(cells = cells, runs = runs)
    }

    private fun removeAndScore(result: MatchResult, cascadeLevel: Int): Long {
        // Gerar gemas especiais conforme tamanho das runs
        val specialsToPlace = mutableListOf<Pair<Pair<Int, Int>, CellContent>>()
        for (run in result.runs) {
            if (run.length >= 4) {
                val midCol = if (run.orientation == RunOrientation.HORIZONTAL) run.col + run.length / 2 else run.col
                val midRow = if (run.orientation == RunOrientation.VERTICAL) run.row + run.length / 2 else run.row
                val special: CellContent = when {
                    run.length >= 5 -> CellContent.Bomb(run.type)
                    run.orientation == RunOrientation.HORIZONTAL -> CellContent.FlameH(run.type)
                    else -> CellContent.FlameV(run.type)
                }
                specialsToPlace.add((midRow to midCol) to special)
            }
        }

        // Expandir efeito de especiais incluídos no conjunto removido
        val finalCells = result.cells.toMutableSet()
        for ((r, c) in result.cells) {
            when (val cell = board[r][c]) {
                is CellContent.FlameH -> for (cc in 0 until BOARD_SIZE) finalCells.add(r to cc)
                is CellContent.FlameV -> for (rr in 0 until BOARD_SIZE) finalCells.add(rr to c)
                is CellContent.Bomb -> {
                    for (rr in (r - 1)..(r + 1)) for (cc in (c - 1)..(c + 1)) {
                        if (rr in 0 until BOARD_SIZE && cc in 0 until BOARD_SIZE) finalCells.add(rr to cc)
                    }
                }
                else -> {}
            }
        }

        var base = 0L
        for ((r, c) in finalCells) {
            board[r][c] = CellContent.Empty
            base += 10
        }
        for ((pos, content) in specialsToPlace) {
            if (pos.first in 0 until BOARD_SIZE && pos.second in 0 until BOARD_SIZE) {
                board[pos.first][pos.second] = content
            }
        }
        val multiplier = 1.0 + 0.5 * cascadeLevel
        return (base * multiplier).toLong()
    }

    private fun applyGravity() {
        for (c in 0 until BOARD_SIZE) {
            var write = BOARD_SIZE - 1
            for (r in BOARD_SIZE - 1 downTo 0) {
                if (board[r][c] !is CellContent.Empty) {
                    if (write != r) {
                        board[write][c] = board[r][c]
                        board[r][c] = CellContent.Empty
                    }
                    write--
                }
            }
            for (r in write downTo 0) {
                board[r][c] = CellContent.Normal(PLAYABLE_GEMS.random(rng))
            }
        }
    }

    fun isStageComplete(): Boolean {
        val target = stageTargets.getOrNull(stage) ?: return false
        return score >= target
    }

    /** 是否存在任意一步可消的交换（死局检测）。 */
    fun hasAnyMove(): Boolean {
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            if (c + 1 < BOARD_SIZE && peekSwapWouldMatch(r, c, r, c + 1)) return true
            if (r + 1 < BOARD_SIZE && peekSwapWouldMatch(r, c, r + 1, c)) return true
        }
        return false
    }

    /**
     * 死局重排：只打乱现有棋子（保留特殊子与得分/关卡进度），循环洗直到
     * 「至少存在一步可消且盘面无即消组合」。6×6 盘 5 色几乎必然在前几次内成功。
     */
    fun reshuffleBoard() {
        val cells = board.flatMap { it.toList() }.toMutableList()
        var attempts = 0
        do {
            cells.shuffle(rng)
            var i = 0
            for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
                board[r][c] = cells[i++]
            }
            attempts++
        } while (attempts < 200 && (!hasAnyMove() || findAllMatches().isNotEmpty()))
    }

    fun advanceStage() { if (stage < stageTargets.size - 1) stage++ }

    fun toSnapshot(): Match3Snapshot {
        val cells = mutableListOf<String>()
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            cells.add(encodeCell(board[r][c]))
        }
        return Match3Snapshot(cells = cells, score = score, stage = stage)
    }

    fun loadFromSnapshot(snap: Match3Snapshot) {
        var i = 0
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            board[r][c] = decodeCell(snap.cells.getOrNull(i++) ?: "EMPTY")
        }
        score = snap.score
        stage = snap.stage
    }

    private fun encodeCell(cell: CellContent): String = when (cell) {
        is CellContent.Empty -> "EMPTY"
        is CellContent.Normal -> "N:${cell.type.name}"
        is CellContent.FlameH -> "FH:${cell.type.name}"
        is CellContent.FlameV -> "FV:${cell.type.name}"
        is CellContent.Bomb -> "B:${cell.type.name}"
    }
    private fun decodeCell(s: String): CellContent = when {
        s == "EMPTY" -> CellContent.Empty
        s.startsWith("N:") -> CellContent.Normal(GemType.valueOf(s.substring(2)))
        s.startsWith("FH:") -> CellContent.FlameH(GemType.valueOf(s.substring(3)))
        s.startsWith("FV:") -> CellContent.FlameV(GemType.valueOf(s.substring(3)))
        s.startsWith("B:") -> CellContent.Bomb(GemType.valueOf(s.substring(2)))
        else -> CellContent.Empty
    }
}

internal enum class RunOrientation { HORIZONTAL, VERTICAL }
internal data class Run(
    val orientation: RunOrientation,
    val row: Int,
    val col: Int,
    val length: Int,
    val type: GemType,
)
internal data class MatchResult(val cells: Set<Pair<Int, Int>>, val runs: List<Run>) {
    fun isEmpty() = cells.isEmpty()
    fun isNotEmpty() = cells.isNotEmpty()
}

sealed interface SwapResult {
    data object Invalid : SwapResult
    data object NoMatch : SwapResult
    data class Matched(val pointsGained: Long, val cascadeLevels: Int) : SwapResult
}
