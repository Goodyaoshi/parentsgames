package com.goodyaoshi.parentsgames.feature.games.tetris.domain

import kotlin.random.Random

const val BOARD_ROWS = 16
const val BOARD_COLS = 8

enum class Tetromino(val color: Long, val blocks: List<List<Pair<Int, Int>>>) {
    // Cada blocks[rotation] = lista de células ocupadas (linha, coluna) relativas ao pivô
    I(0xFF4FB3B3, listOf(
        listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
        listOf(0 to 2, 1 to 2, 2 to 2, 3 to 2),
        listOf(2 to 0, 2 to 1, 2 to 2, 2 to 3),
        listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
    )),
    O(0xFFEFB12A, listOf(
        listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
    )),
    T(0xFF7B5E8C, listOf(
        listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2),
        listOf(0 to 1, 1 to 1, 1 to 2, 2 to 1),
        listOf(1 to 0, 1 to 1, 1 to 2, 2 to 1),
        listOf(0 to 1, 1 to 0, 1 to 1, 2 to 1),
    )),
    L(0xFFD85A30, listOf(
        listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2),
        listOf(0 to 1, 1 to 1, 2 to 1, 2 to 2),
        listOf(1 to 0, 1 to 1, 1 to 2, 2 to 0),
        listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
    )),
    J(0xFF378ADD, listOf(
        listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2),
        listOf(0 to 1, 0 to 2, 1 to 1, 2 to 1),
        listOf(1 to 0, 1 to 1, 1 to 2, 2 to 2),
        listOf(0 to 1, 1 to 1, 2 to 0, 2 to 1),
    )),
    S(0xFF639922, listOf(
        listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1),
        listOf(0 to 1, 1 to 1, 1 to 2, 2 to 2),
        listOf(1 to 1, 1 to 2, 2 to 0, 2 to 1),
        listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
    )),
    Z(0xFFE24B4A, listOf(
        listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2),
        listOf(0 to 2, 1 to 1, 1 to 2, 2 to 1),
        listOf(1 to 0, 1 to 1, 2 to 1, 2 to 2),
        listOf(0 to 1, 1 to 0, 1 to 1, 2 to 0),
    )),
}

data class FallingPiece(
    val type: Tetromino,
    val rotation: Int,
    val row: Int,    // topo da bounding-box no board
    val col: Int,    // esquerda da bounding-box
) {
    fun cells(): List<Pair<Int, Int>> =
        type.blocks[rotation % type.blocks.size].map { (r, c) -> (row + r) to (col + c) }
}

class TetrisEngine(seed: Long = System.currentTimeMillis()) {
    private val rng = Random(seed)
    val board: Array<IntArray> = Array(BOARD_ROWS) { IntArray(BOARD_COLS) }

    init {
        for (i in 0 until BOARD_ROWS) for (j in 0 until BOARD_COLS) board[i][j] = -1
    }

    var current: FallingPiece? = null; private set

    // FIX X12: 7-bag randomizer — garante distribuição justa de peças
    private val bag = ArrayDeque<Tetromino>()
    var next: Tetromino = nextFromBag(); private set
    var lockGracePeriodActive: Boolean = false; private set
    var lockGraceStartedAt: Long = 0; private set
    var pointsLastLineClear: Long = 0; private set
    var score: Long = 0L; private set
    var level: Int = 1; private set
    var lines: Int = 0; private set
    var gameOver: Boolean = false; private set
    var lastClearedRows: List<Int> = emptyList(); private set

    fun spawnNext(): Boolean {
        val type = next
        next = nextFromBag()
        val piece = FallingPiece(type = type, rotation = 0, row = 0, col = (BOARD_COLS / 2) - 2)
        if (collides(piece)) {
            gameOver = true
            return false
        }
        current = piece
        lockGracePeriodActive = false
        return true
    }

    private fun nextFromBag(): Tetromino {
        if (bag.isEmpty()) {
            bag.addAll(Tetromino.entries.shuffled(rng))
        }
        return bag.removeFirst()
    }

    fun moveLeft(): Boolean = tryMove { it.copy(col = it.col - 1) }.also { if (it) onSuccessfulMove() }
    fun moveRight(): Boolean = tryMove { it.copy(col = it.col + 1) }.also { if (it) onSuccessfulMove() }

    fun rotate(): Boolean = tryMove {
        it.copy(rotation = (it.rotation + 1) % it.type.blocks.size)
    }.also { if (it) onSuccessfulMove() }

    /** Quando movimento bem-sucedido durante lock grace, reseta o timer. */
    private fun onSuccessfulMove() {
        if (lockGracePeriodActive) {
            lockGraceStartedAt = System.currentTimeMillis()
        }
    }

    /** Retorna se a peça atual está pousada (não pode descer mais). */
    fun pieceOnGround(): Boolean {
        val piece = current ?: return false
        return collides(piece.copy(row = piece.row + 1))
    }

    /** Tenta lockar a peça SE o lock grace expirou. */
    fun maybeLockAfterGrace(graceMs: Long = 500L): Boolean {
        val piece = current ?: return false
        if (!pieceOnGround()) {
            lockGracePeriodActive = false
            return false
        }
        if (!lockGracePeriodActive) {
            lockGracePeriodActive = true
            lockGraceStartedAt = System.currentTimeMillis()
            return false
        }
        if (System.currentTimeMillis() - lockGraceStartedAt >= graceMs) {
            lockPiece(piece)
            return true
        }
        return false
    }

    /**
     * Tick natural: tenta descer. Se não der, ATIVA lock grace (não trava imediatamente).
     * O ViewModel deve chamar `maybeLockAfterGrace()` para travar quando grace expirar.
     */
    fun tick(): Boolean {
        val piece = current ?: return false
        val moved = piece.copy(row = piece.row + 1)
        if (collides(moved)) {
            // FIX X07: lock delay
            if (!lockGracePeriodActive) {
                lockGracePeriodActive = true
                lockGraceStartedAt = System.currentTimeMillis()
            }
            return false
        }
        current = moved
        lockGracePeriodActive = false
        return true
    }

    fun softDrop(): Boolean {
        val piece = current ?: return false
        val moved = piece.copy(row = piece.row + 1)
        if (collides(moved)) {
            lockPiece(piece)
            return false
        }
        current = moved
        score += 1
        return true
    }

    fun hardDrop() {
        var piece = current ?: return
        var dropped = 0
        while (true) {
            val n = piece.copy(row = piece.row + 1)
            if (collides(n)) break
            piece = n
            dropped++
        }
        current = piece
        score += dropped * 2L
        lockPiece(piece)
    }

    private fun tryMove(transform: (FallingPiece) -> FallingPiece): Boolean {
        val piece = current ?: return false
        val moved = transform(piece)
        if (collides(moved)) return false
        current = moved
        return true
    }

    private fun collides(piece: FallingPiece): Boolean {
        for ((r, c) in piece.cells()) {
            if (r !in 0 until BOARD_ROWS || c !in 0 until BOARD_COLS) return true
            if (board[r][c] >= 0) return true
        }
        return false
    }

    private fun lockPiece(piece: FallingPiece) {
        for ((r, c) in piece.cells()) {
            if (r in 0 until BOARD_ROWS && c in 0 until BOARD_COLS) {
                board[r][c] = piece.type.ordinal
            }
        }
        val cleared = clearFullRows()
        pointsLastLineClear = 0
        if (cleared.isNotEmpty()) {
            lines += cleared.size
            val baseScore = when (cleared.size) {
                1 -> 100
                2 -> 300
                3 -> 500
                else -> 800
            }
            val gained = baseScore.toLong() * level
            score += gained
            pointsLastLineClear = gained
            level = (lines / 10 + 1).coerceAtLeast(1)
        }
        lastClearedRows = cleared
        current = null
        lockGracePeriodActive = false
    }

    private fun clearFullRows(): List<Int> {
        val rowsToClear = mutableListOf<Int>()
        for (r in 0 until BOARD_ROWS) {
            if ((0 until BOARD_COLS).all { board[r][it] >= 0 }) rowsToClear.add(r)
        }
        if (rowsToClear.isEmpty()) return emptyList()
        // Mover as linhas de cima para baixo
        val newBoard = Array(BOARD_ROWS) { IntArray(BOARD_COLS) { -1 } }
        var writeRow = BOARD_ROWS - 1
        for (r in BOARD_ROWS - 1 downTo 0) {
            if (r in rowsToClear) continue
            for (c in 0 until BOARD_COLS) newBoard[writeRow][c] = board[r][c]
            writeRow--
        }
        for (i in 0 until BOARD_ROWS) board[i] = newBoard[i]
        return rowsToClear
    }

    private fun randomTetromino(): Tetromino = Tetromino.entries.random(rng)

    /** Intervalo de tick em ms baseado no nível. */
    fun tickInterval(): Long = (1000L - (level - 1) * 80L).coerceAtLeast(120L)
}
