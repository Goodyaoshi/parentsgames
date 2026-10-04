package com.goodyaoshi.parentsgames.feature.games.frogger.domain

import kotlin.random.Random

const val COLS = 9
const val ROWS = 13   // 0 = topo (objetivo), 12 = base (início)

enum class LaneType { SAFE, CAR, WATER }

data class Lane(
    val row: Int,
    val type: LaneType,
    val direction: Int,     // +1 direita, -1 esquerda
    val speed: Float,       // células por segundo
    val gap: Float,         // espaço entre obstáculos
    val obstacleLen: Int,   // tamanho em células do carro/tronco
    val offset: Float = 0f, // posição do primeiro obstáculo
)

data class Frog(val row: Int, val col: Int, val colFloat: Float = col.toFloat())

enum class FrogStatus { ALIVE, DEAD_CAR, DEAD_WATER, WON }

class FroggerEngine(seed: Long = System.currentTimeMillis()) {
    private val rng = Random(seed)
    var frog: Frog = Frog(row = ROWS - 1, col = COLS / 2, colFloat = (COLS / 2).toFloat()); private set
    var facingDeg: Float = 0f; private set       // FIX V802: rotação do sapo
    var goalSlots: BooleanArray = BooleanArray(5); private set   // FIX V801: slots de chegada
    var lives: Int = 3; private set
    var score: Long = 0L; private set
    var status: FrogStatus = FrogStatus.ALIVE; private set
    var elapsedSec: Float = 0f; private set
    var goalsReached: Int = 0; private set
    private var bestRowReached: Int = ROWS - 1
    var lastDeathReason: FrogStatus? = null; private set

    val lanes: List<Lane> = buildLanes()

    private fun buildLanes(): List<Lane> {
        val out = mutableListOf<Lane>()
        // Linha 0: objetivo (safe — chegou)
        out.add(Lane(0, LaneType.SAFE, 0, 0f, 0f, 0))
        // Linhas 1..4: água com troncos
        for (r in 1..4) {
            val dir = if (r % 2 == 0) 1 else -1
            out.add(Lane(r, LaneType.WATER, dir, 1.0f + r * 0.15f, 3.5f, 3))
        }
        // Linha 5: meio (safe)
        out.add(Lane(5, LaneType.SAFE, 0, 0f, 0f, 0))
        // Linhas 6..11: estrada com carros
        for (r in 6..11) {
            val dir = if (r % 2 == 0) -1 else 1
            val speed = 1.0f + (11 - r) * 0.25f
            out.add(Lane(r, LaneType.CAR, dir, speed, 4.5f, 1))
        }
        // Linha 12: base (safe)
        out.add(Lane(12, LaneType.SAFE, 0, 0f, 0f, 0))
        return out
    }

    private val laneOffsets: FloatArray = FloatArray(lanes.size).also { arr ->
        for (i in arr.indices) arr[i] = rng.nextFloat() * 6f
    }

    fun update(dtSec: Float) {
        if (status != FrogStatus.ALIVE) return
        elapsedSec += dtSec
        for (i in lanes.indices) {
            val l = lanes[i]
            if (l.type == LaneType.SAFE) continue
            laneOffsets[i] = (laneOffsets[i] + l.direction * l.speed * dtSec)
            val range = (l.obstacleLen + l.gap)
            while (laneOffsets[i] > range) laneOffsets[i] -= range
            while (laneOffsets[i] < -range) laneOffsets[i] += range
        }
        // Se o sapo está em uma linha de água, sai junto com o tronco
        val laneOfFrog = lanes[frog.row]
        if (laneOfFrog.type == LaneType.WATER) {
            val carriedDelta = laneOfFrog.direction * laneOfFrog.speed * dtSec
            val newColFloat = frog.colFloat + carriedDelta
            frog = frog.copy(col = newColFloat.toInt(), colFloat = newColFloat)
        }
        checkCollision()
        if (status == FrogStatus.ALIVE && frog.row == 0) {
            // FIX V801: marca o goal slot mais próximo (de 5 slots no topo)
            val slotIdx = (frog.col * 5 / COLS).coerceIn(0, 4)
            if (!goalSlots[slotIdx]) {
                goalSlots[slotIdx] = true
                score += 100
                goalsReached++
            } else {
                // já preencheu esse slot — retorna sem ganho
                score += 10
            }
            frog = Frog(row = ROWS - 1, col = COLS / 2, colFloat = (COLS / 2).toFloat())
            bestRowReached = ROWS - 1
            facingDeg = 0f
            if (goalsReached >= 5) status = FrogStatus.WON
        }
    }

    fun moveUp() { facingDeg = 0f; move(-1, 0) }
    fun moveDown() { facingDeg = 180f; move(1, 0) }
    fun moveLeft() { facingDeg = 270f; move(0, -1) }
    fun moveRight() { facingDeg = 90f; move(0, 1) }

    private fun move(dr: Int, dc: Int) {
        if (status != FrogStatus.ALIVE) return
        val nr = (frog.row + dr).coerceIn(0, ROWS - 1)
        val nc = (frog.col + dc).coerceIn(0, COLS - 1)
        frog = frog.copy(row = nr, col = nc, colFloat = nc.toFloat())
        if (dr < 0 && nr < bestRowReached) {
            bestRowReached = nr
            score += 10
        }
        checkCollision()
    }

    fun obstaclePositions(): Map<Int, List<Pair<Float, Int>>> {
        // Para cada lane, retorna lista (colStart, length) das obstáculos visíveis
        val result = HashMap<Int, List<Pair<Float, Int>>>(lanes.size)
        for ((i, l) in lanes.withIndex()) {
            if (l.type == LaneType.SAFE) continue
            val items = mutableListOf<Pair<Float, Int>>()
            val step = (l.obstacleLen + l.gap)
            var pos = laneOffsets[i]
            while (pos > -l.obstacleLen) pos -= step
            while (pos < COLS + l.obstacleLen) {
                items.add(pos to l.obstacleLen)
                pos += step
            }
            result[i] = items
        }
        return result
    }

    private fun checkCollision() {
        val lane = lanes[frog.row]
        val positions = obstaclePositions()[frog.row].orEmpty()
        // FIX F11: hit-test usando colFloat com tolerância
        val hit = positions.any { (start, len) ->
            frog.colFloat + 0.4f >= start && frog.colFloat + 0.6f < start + len
        }
        // Sai do tabuleiro horizontalmente em água = morte
        if (lane.type == LaneType.WATER && (frog.colFloat < -0.5f || frog.colFloat > COLS - 0.5f)) {
            registerDeath(FrogStatus.DEAD_WATER); return
        }
        when (lane.type) {
            LaneType.CAR -> if (hit) registerDeath(FrogStatus.DEAD_CAR)
            LaneType.WATER -> if (!hit) registerDeath(FrogStatus.DEAD_WATER)
            LaneType.SAFE -> Unit
        }
    }

    private fun registerDeath(reason: FrogStatus) {
        lives--
        lastDeathReason = reason
        if (lives <= 0) status = reason
        else respawn()
    }

    private fun respawn() {
        frog = Frog(row = ROWS - 1, col = COLS / 2, colFloat = (COLS / 2).toFloat())
        bestRowReached = ROWS - 1
    }
}
