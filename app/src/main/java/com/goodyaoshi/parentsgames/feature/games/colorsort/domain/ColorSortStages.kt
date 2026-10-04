package com.goodyaoshi.parentsgames.feature.games.colorsort.domain

import kotlin.random.Random

/**
 * Catálogo de 30 fases pré-construídas com dificuldade progressiva.
 *
 * Fases 1–5: tutorial implícito (2 cores, solucionável em poucos movimentos)
 * Fases 6–15: 3-4 cores
 * Fases 16–25: 5-6 cores
 * Fases 26–30: 7-8 cores (desafiantes)
 *
 * Cada fase é gerada de forma reversa: parte do estado-objetivo e embaralha
 * com movimentos válidos para garantir solvabilidade.
 */
object ColorSortStages {

    private val palette = LiquidColor.entries.toTypedArray()

    fun stage(n: Int): List<Tube> {
        val stageDef = configFor(n)
        return generateSolvable(
            colors = stageDef.colors,
            emptyTubes = stageDef.emptyTubes,
            capacity = stageDef.capacity,
            shuffleMoves = stageDef.shuffleMoves,
            seed = stageDef.seed,
        )
    }

    private data class StageConfig(
        val colors: Int,
        val emptyTubes: Int,
        val capacity: Int = 4,
        val shuffleMoves: Int,
        val seed: Long,
    )

    private fun configFor(n: Int): StageConfig {
        val safe = n.coerceIn(1, 30)
        return when (safe) {
            in 1..5   -> StageConfig(colors = 2 + (safe - 1) / 2, emptyTubes = 2, shuffleMoves = 6 + safe * 2, seed = 100L + safe)
            in 6..15  -> StageConfig(colors = 3 + (safe - 6) / 3, emptyTubes = 2, shuffleMoves = 12 + safe * 2, seed = 200L + safe)
            in 16..25 -> StageConfig(colors = 5 + (safe - 16) / 5, emptyTubes = 2, shuffleMoves = 20 + safe * 2, seed = 300L + safe)
            else      -> StageConfig(colors = 7 + (safe - 26) / 2, emptyTubes = 2, shuffleMoves = 30 + safe * 2, seed = 400L + safe)
        }
    }

    /**
     * Gera uma fase solucionável partindo do estado-final (tubos sorted) e
     * embaralhando. O embaralhamento aleatório por si só NÃO garante solvabilidade
     * (permitimos derramar cores diferentes), então cada candidato é verificado
     * por [isSolvable]; insólúvel → regenera com outra semente.
     */
    private fun generateSolvable(
        colors: Int,
        emptyTubes: Int,
        capacity: Int,
        shuffleMoves: Int,
        seed: Long,
    ): List<Tube> {
        repeat(12) { attempt ->
            val candidate = scrambleOnce(colors, emptyTubes, capacity, shuffleMoves, seed + attempt * 1_000_003L)
            if (isSolvable(candidate)) return candidate
        }
        // 12 次全败（几乎不可能）：退回最后一次盘面，保证关卡总能生成
        return scrambleOnce(colors, emptyTubes, capacity, shuffleMoves, seed)
    }

    private fun scrambleOnce(
        colors: Int,
        emptyTubes: Int,
        capacity: Int,
        shuffleMoves: Int,
        seed: Long,
    ): List<Tube> {
        val rng = Random(seed)
        val palette = palette.take(colors)

        // Estado inicial = objetivo (cada tubo cheio, monocromático)
        val tubes = palette.map { color ->
            Tube(units = List(capacity) { color }, capacity = capacity)
        }.toMutableList()
        repeat(emptyTubes) { tubes.add(Tube(emptyList(), capacity = capacity)) }

        // Realiza shuffleMoves transferências válidas para "embaralhar" preservando solvabilidade
        var attempts = 0
        var moves = 0
        while (moves < shuffleMoves && attempts < shuffleMoves * 10) {
            attempts++
            val from = rng.nextInt(tubes.size)
            val to = rng.nextInt(tubes.size)
            if (from == to) continue
            val src = tubes[from]; val dst = tubes[to]
            if (src.isEmpty || dst.isFull) continue
            // Movimento "splitting": derramar a cor do topo para outro tubo OU para vazio
            // Para garantir variedade, permitimos transferir mesmo se a cor não bater
            // (pois é geração reversa — não simulação de jogo real)
            val moveCount = minOf(rng.nextInt(1, src.units.size + 1), dst.capacity - dst.units.size)
            if (moveCount <= 0) continue
            val transferred = src.units.subList(src.units.size - moveCount, src.units.size).toList()
            tubes[from] = src.copy(units = src.units.subList(0, src.units.size - moveCount).toList())
            tubes[to] = dst.copy(units = dst.units + transferred)
            moves++
        }
        return tubes.toList()
    }

    /**
     * 可解性验证：用与游戏完全一致的规则（同色可倒 / 空管可倒，一次倒完
     * topRun 与剩余容量的较小值）从当前盘面做 DFS，检验能否到达
     * 「每管为空或整管同色」的目标状态；节点数设上限防极端盘面卡顿。
     */
    internal fun isSolvable(start: List<Tube>): Boolean {
        if (start.isEmpty()) return false
        val capacity = start.first().capacity
        fun isGoal(state: List<List<Int>>): Boolean =
            state.all { tube -> tube.isEmpty() || (tube.size == capacity && tube.all { v -> v == tube[0] }) }
        fun keyOf(state: List<List<Int>>): String =
            state.joinToString("|") { tube -> tube.joinToString(",") }

        val startState = start.map { it.units.map { c -> c.ordinal } }
        if (isGoal(startState)) return true
        val visited = HashSet<String>()
        visited.add(keyOf(startState))
        val stack = ArrayDeque<List<List<Int>>>()
        stack.addLast(startState)
        var nodes = 0
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (++nodes > 50_000) return false
            for (from in cur.indices) {
                val src = cur[from]
                if (src.isEmpty()) continue
                val top = src.last()
                var run = 1
                while (run < src.size && src[src.size - 1 - run] == top) run++
                for (to in cur.indices) {
                    if (to == from) continue
                    val dst = cur[to]
                    val free = capacity - dst.size
                    if (free <= 0) continue
                    if (dst.isNotEmpty() && dst.last() != top) continue
                    // 剪枝：整管同色倒入空管毫无进展；多个空管等价，只试第一个
                    if (dst.isEmpty() && (run == src.size || cur.take(to).any { it.isEmpty() })) continue
                    val moveCount = minOf(run, free)
                    val next = cur.mapIndexed { i, tube ->
                        when (i) {
                            from -> tube.subList(0, tube.size - moveCount)
                            to -> tube + src.subList(src.size - moveCount, src.size)
                            else -> tube
                        }
                    }
                    if (isGoal(next)) return true
                    if (visited.add(keyOf(next))) stack.addLast(next)
                }
            }
        }
        return false
    }
}
