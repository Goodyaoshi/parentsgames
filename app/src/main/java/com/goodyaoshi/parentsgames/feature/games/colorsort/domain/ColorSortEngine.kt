package com.goodyaoshi.parentsgames.feature.games.colorsort.domain

import kotlinx.serialization.Serializable

enum class LiquidColor { RED, BLUE, GREEN, YELLOW, ORANGE, PURPLE, PINK, CYAN }

@Serializable
data class ColorSortSnapshot(
    val tubes: List<List<String>>,  // cores serializadas como nome
    val capacity: Int,
    val movesMade: Int,
    val stage: Int,
)

data class Tube(val units: List<LiquidColor>, val capacity: Int = 4) {
    val isEmpty: Boolean get() = units.isEmpty()
    val isFull: Boolean get() = units.size == capacity
    val top: LiquidColor? get() = units.lastOrNull()
    val freeSpace: Int get() = capacity - units.size
    val topRunSize: Int
        get() {
            val t = top ?: return 0
            var n = 0
            for (i in units.indices.reversed()) {
                if (units[i] == t) n++ else break
            }
            return n
        }
    val isSorted: Boolean
        get() = isEmpty || (units.size == capacity && units.all { it == units.first() })
}

data class Move(val from: Int, val to: Int, val units: List<LiquidColor>)

class ColorSortEngine(initial: List<Tube>) {
    private val tubes: MutableList<Tube> = initial.toMutableList()
    private val history: ArrayDeque<Move> = ArrayDeque()
    var movesMade: Int = 0; private set

    fun snapshot(): List<Tube> = tubes.toList()

    fun canTransfer(from: Int, to: Int): Boolean {
        if (from == to) return false
        val src = tubes.getOrNull(from) ?: return false
        val dst = tubes.getOrNull(to) ?: return false
        if (src.isEmpty || dst.isFull) return false
        if (dst.isEmpty) return true
        return src.top == dst.top
    }

    fun transfer(from: Int, to: Int): Boolean {
        if (!canTransfer(from, to)) return false
        val src = tubes[from]
        val dst = tubes[to]
        val moveCount = minOf(src.topRunSize, dst.freeSpace)
        val transferred = src.units.subList(src.units.size - moveCount, src.units.size).toList()
        tubes[from] = src.copy(units = src.units.subList(0, src.units.size - moveCount).toList())
        tubes[to] = dst.copy(units = dst.units + transferred)
        history.addLast(Move(from = from, to = to, units = transferred))
        movesMade++
        return true
    }

    fun undo(): Boolean {
        val last = history.removeLastOrNull() ?: return false
        val toTube = tubes[last.to]
        val fromTube = tubes[last.from]
        tubes[last.to] = toTube.copy(units = toTube.units.subList(0, toTube.units.size - last.units.size).toList())
        tubes[last.from] = fromTube.copy(units = fromTube.units + last.units)
        // Não incrementa movesMade no undo (corrigido)
        return true
    }

    fun addEmptyTube() {
        tubes.add(Tube(units = emptyList()))
    }

    fun isWin(): Boolean = tubes.all { it.isSorted }

    fun toSnapshot(stage: Int): ColorSortSnapshot = ColorSortSnapshot(
        tubes = tubes.map { tube -> tube.units.map { it.name } },
        capacity = tubes.firstOrNull()?.capacity ?: 4,
        movesMade = movesMade,
        stage = stage,
    )

    fun loadFromSnapshot(snap: ColorSortSnapshot) {
        tubes.clear()
        for (tubeData in snap.tubes) {
            val units = tubeData.mapNotNull { runCatching { LiquidColor.valueOf(it) }.getOrNull() }
            tubes.add(Tube(units = units, capacity = snap.capacity))
        }
        movesMade = snap.movesMade
    }
}

// ColorSortStages foi movido para ColorSortStages.kt
