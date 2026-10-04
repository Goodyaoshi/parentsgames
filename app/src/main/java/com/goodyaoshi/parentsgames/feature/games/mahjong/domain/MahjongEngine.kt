package com.goodyaoshi.parentsgames.feature.games.mahjong.domain

import kotlin.math.abs
import kotlin.random.Random

/**
 * Engine do Mahjong Solitaire.
 *
 * Layout simplificado para MVP: 4 camadas, 36 peças por camada (6×6),
 * com camadas superiores menores. Total ~108 peças (subset do conjunto
 * tradicional de 144, mantendo balanço de pares).
 */
class MahjongEngine(initialTiles: List<MahjongTile>) {
    private val tiles: MutableList<MahjongTile> = initialTiles.toMutableList()
    private val removalHistory: ArrayDeque<Pair<Int, Int>> = ArrayDeque()

    var hintsUsed: Int = 0; private set
    var undosUsed: Int = 0; private set
    var reshufflesUsed: Int = 0; private set

    fun snapshot(): List<MahjongTile> = tiles.toList()

    fun isFree(tile: MahjongTile): Boolean = isTileFree(tiles, tile)

    fun matches(a: TileFace, b: TileFace): Boolean = a.familyId == b.familyId

    /** Tenta remover par. Retorna true se removeu. */
    fun tryMatch(idA: Int, idB: Int): Boolean {
        val a = tiles.find { it.id == idA && !it.removed } ?: return false
        val b = tiles.find { it.id == idB && !it.removed } ?: return false
        if (a.id == b.id) return false
        if (!isFree(a) || !isFree(b)) return false
        if (!matches(a.face, b.face)) return false
        tiles[tiles.indexOf(a)] = a.copy(removed = true)
        tiles[tiles.indexOf(b)] = b.copy(removed = true)
        removalHistory.addLast(idA to idB)
        return true
    }

    fun undo(): Boolean {
        if (!restoreLastPair()) return false
        undosUsed++
        return true
    }

    /** 撤回上一次消除（不计入 undo 次数），供死局兜底复用。 */
    private fun restoreLastPair(): Boolean {
        val (idA, idB) = removalHistory.removeLastOrNull() ?: return false
        tiles.indexOfFirst { it.id == idA }.takeIf { it >= 0 }?.let {
            tiles[it] = tiles[it].copy(removed = false)
        }
        tiles.indexOfFirst { it.id == idB }.takeIf { it >= 0 }?.let {
            tiles[it] = tiles[it].copy(removed = false)
        }
        return true
    }

    /** Retorna um par válido se houver, para usar como dica. */
    fun findHintPair(): Pair<Int, Int>? {
        val active = tiles.filter { !it.removed && isFree(it) }
        for (i in active.indices) for (j in i + 1 until active.size) {
            if (matches(active[i].face, active[j].face)) {
                return active[i].id to active[j].id
            }
        }
        return null
    }

    fun useHint(): Pair<Int, Int>? {
        val pair = findHintPair() ?: return null
        hintsUsed++
        return pair
    }

    /** 是否还有可配对的走法（死局检测）。 */
    fun hasMoves(): Boolean = findHintPair() != null

    /**
     * 洗牌：只重排剩余牌的牌面，位置布局不动；循环洗直到洗完至少有一对可配。
     * 极端残局（自由牌不足两张的位置性死锁）洗面无效，此时回退撤销直至盘面恢复可配，
     * 保证「洗牌」永远是有效的脱困手段。
     */
    fun reshuffle(seed: Long = System.currentTimeMillis()) {
        reshufflesUsed++
        val rng = Random(seed)
        val activeIdx = tiles.indices.filter { !tiles[it].removed }
        if (activeIdx.size >= 2) {
            var attempts = 0
            do {
                val faces = activeIdx.map { tiles[it].face }.toMutableList()
                faces.shuffle(rng)
                for ((k, i) in activeIdx.withIndex()) {
                    tiles[i] = tiles[i].copy(face = faces[k])
                }
                attempts++
            } while (!hasFreeMatchingPair(tiles) && attempts < 200)
        }
        // 位置性死锁兜底：撤回消除直到重新出现可配对（不计入 undo 次数）
        var guard = 0
        while (!hasFreeMatchingPair(tiles) && removalHistory.isNotEmpty() && guard++ < 500) {
            restoreLastPair()
        }
    }

    fun isComplete(): Boolean = tiles.all { it.removed }

    fun remainingCount(): Int = tiles.count { !it.removed }
}

/** 单张牌是否可取：上层无遮挡，且左右至少一侧无相邻牌。引擎与布局生成共用同一判定。 */
internal fun isTileFree(tiles: List<MahjongTile>, tile: MahjongTile): Boolean {
    if (tile.removed) return false
    val above = tiles.any { t ->
        !t.removed &&
            t.layer == tile.layer + 1 &&
            abs(t.row - tile.row) <= 1 &&
            abs(t.col - tile.col) <= 1
    }
    if (above) return false
    val leftBlocked = tiles.any {
        !it.removed && it.id != tile.id && it.layer == tile.layer &&
            it.row == tile.row && it.col == tile.col - 2
    }
    val rightBlocked = tiles.any {
        !it.removed && it.id != tile.id && it.layer == tile.layer &&
            it.row == tile.row && it.col == tile.col + 2
    }
    return !(leftBlocked && rightBlocked)
}

/** 是否存在至少一对「当前可取且同面」的牌（开局与洗牌的可玩性保证）。 */
internal fun hasFreeMatchingPair(tiles: List<MahjongTile>): Boolean {
    val free = tiles.filter { isTileFree(tiles, it) }
    for (i in free.indices) for (j in i + 1 until free.size) {
        if (free[i].face.familyId == free[j].face.familyId) return true
    }
    return false
}

/** 盘面规格。SMALL 为 72 张小金字塔（默认，老人友好：牌更大）；LARGE 为经典大龟形。 */
enum class MahjongBoardLayout(val tileCount: Int) {
    SMALL(72),
    LARGE(152),
}

/** Constrói um layout MVP em "tartaruga simplificada". */
object MahjongLayoutBuilder {

    fun build(
        layout: MahjongBoardLayout = MahjongBoardLayout.SMALL,
        seed: Long = System.currentTimeMillis(),
    ): List<MahjongTile> = when (layout) {
        // 小盘：逆向成局——模拟消除随机配对后按逆序放置，整局必然可通关且布局不对称
        MahjongBoardLayout.SMALL -> buildSmallSolvable(seed)
        // 大盘：保留经典龟形随机发牌，只保证开局至少一对可配
        MahjongBoardLayout.LARGE -> buildLarge(seed)
    }

    /** 小盘随机成局的最大尝试次数（每次失败换种子重来；理论上第一次就会成功）。 */
    private const val SMALL_BUILD_ATTEMPTS = 8

    /**
     * 小盘逆向成局（随机化版）：先正向「模拟消除」，再按其逆序放置。
     *  - 模拟消除：从满盘 72 张开始，每一步用引擎同款规则（[isTileFree]）找出当前
     *    所有可取的牌，从中随机挑两张不同位置配成一对，记为一步消除；
     *  - 每步消除分配一个成对牌面，玩家按模拟顺序逐步消除即可清盘——整局必然可通关；
     *  - 每步的配对位置完全随机（可跨行、跨列，不再固定「中心向外镜像」），因此
     *    布局每局不同、天然不对称，解决旧版「完美左右对称、一眼看穿」的问题。
     * 理论上每步至少有两张可取牌（最上层非空层的牌永不被压，行端牌至少一侧为空），
     * 极端情况下若仍不足两张，则换种子重试；多次失败退回确定性的中心向外布局兜底。
     */
    private fun buildSmallSolvable(seed: Long): List<MahjongTile> {
        val positions = smallPyramidPositions()
        require(positions.size == MahjongBoardLayout.SMALL.tileCount) {
            "Mahjong small layout expected ${MahjongBoardLayout.SMALL.tileCount} tiles, got ${positions.size}"
        }
        var s = seed
        repeat(SMALL_BUILD_ATTEMPTS) {
            tryBuildSmallByReverseDeal(positions, s)?.let { return it }
            s += 7919L // 与大盘重发同款步进，换种子重试
        }
        // 兜底（理论不可达）：确定性「每行中心向外」布局，同样保证可通关，仅对称
        return buildSmallCenterOut(positions, seed)
    }

    /**
     * 正向模拟消除并按逆序写回牌面；若某步可取牌不足两张（理论不可达）返回 null 触发重试。
     * 每步先从当前盘面筛出全部可取牌（与引擎消除判定完全同一实现），再随机取两个不同位置
     * 配成一对——这对牌在「前序对都已消除」的局面下必然同时可取，构成可通关的消除序。
     */
    private fun tryBuildSmallByReverseDeal(
        positions: List<Triple<Int, Int, Int>>,
        seed: Long,
    ): List<MahjongTile>? {
        val rng = Random(seed)
        val pairs = positions.size / 2
        val faces = generateBalancedDeck(pairs).shuffled(rng)
        // present：模拟消除时仍在盘上的牌（占位牌面，可取判定只用层/行/列坐标）
        val present = positions.mapIndexed { i, (layer, r, c) ->
            MahjongTile(id = i, face = faces[0], layer = layer, row = r, col = c)
        }.toMutableList()
        // 每个位置被消除的步序（0 起）；放置时按此分配成对牌面
        val stepOfSlot = IntArray(positions.size)
        var step = 0
        while (present.isNotEmpty()) {
            val free = present.filter { isTileFree(present, it) }
            if (free.size < 2) return null
            // 随机挑两个不同位置配成一对（可跨行、跨层）
            val first = rng.nextInt(free.size)
            var second = rng.nextInt(free.size - 1)
            if (second >= first) second++
            for (t in listOf(free[first], free[second])) {
                stepOfSlot[t.id] = step
                present.remove(t)
            }
            step++
        }
        // 消除序的逆序即放置序：第 step 步消除的对共用牌面 faces[step]；
        // 输出按（层,行,列）升序编号，与旧版 id 规则一致，快照兼容
        return positions.indices
            .sortedWith(
                compareBy(
                    { positions[it].first },
                    { positions[it].second },
                    { positions[it].third },
                ),
            )
            .mapIndexed { id, slot ->
                val (layer, r, c) = positions[slot]
                MahjongTile(id = id, face = faces[stepOfSlot[slot]], layer = layer, row = r, col = c)
            }
    }

    /** 兜底布局：每行从中心向外成对放置（放置逆序即消除序，必可通关；仅布局对称）。 */
    private fun buildSmallCenterOut(
        positions: List<Triple<Int, Int, Int>>,
        seed: Long,
    ): List<MahjongTile> {
        val rng = Random(seed)
        val faces = generateBalancedDeck(positions.size / 2).shuffled(rng)
        // 按 (层,行) 分组，行内按列升序
        val rows = positions.groupBy({ it.first to it.second }, { it.third })
            .toSortedMap(compareBy({ it.first }, { it.second }))
        val tiles = ArrayList<MahjongTile>(positions.size)
        var id = 0
        var faceIdx = 0
        for ((layerRow, cols) in rows) {
            val (layer, row) = layerRow
            val sorted = cols.sorted()
            var lo = sorted.size / 2 - 1
            var hi = sorted.size / 2
            while (lo >= 0) {
                val face = faces[faceIdx++]
                tiles.add(MahjongTile(id = id++, face = face, layer = layer, row = row, col = sorted[lo]))
                tiles.add(MahjongTile(id = id++, face = face, layer = layer, row = row, col = sorted[hi]))
                lo--
                hi++
            }
        }
        return tiles
    }

    /** 大盘随机发牌；开局至少一对可配，否则换种子重发。 */
    private fun buildLarge(seed: Long): List<MahjongTile> {
        val positions = turtlePositions()
        require(positions.size == MahjongBoardLayout.LARGE.tileCount) {
            "Mahjong layout ${MahjongBoardLayout.LARGE.name} expected ${MahjongBoardLayout.LARGE.tileCount} tiles, got ${positions.size}"
        }
        var s = seed
        while (true) {
            val tiles = dealRandomFaces(positions, s)
            if (hasFreeMatchingPair(tiles)) return tiles
            s += 7919L
        }
    }

    private fun dealRandomFaces(
        positions: List<Triple<Int, Int, Int>>,
        seed: Long,
    ): List<MahjongTile> {
        val pairs = positions.size / 2
        val pairedFaces = generateBalancedDeck(pairs).flatMap { listOf(it, it) }.toMutableList()
        pairedFaces.shuffle(Random(seed))
        return positions.mapIndexed { idx, (layer, r, c) ->
            MahjongTile(id = idx, face = pairedFaces[idx], layer = layer, row = r, col = c)
        }
    }

    /** Gera `pairs` desenhos distintos (face) balanceados. */
    private fun generateBalancedDeck(pairs: Int): List<TileFace> {
        val all = mutableListOf<TileFace>()
        (1..9).forEach { all.add(TileFace.Bamboo(it)) }
        (1..9).forEach { all.add(TileFace.Character(it)) }
        (1..9).forEach { all.add(TileFace.Circle(it)) }
        listOf("東", "南", "西", "北").forEach { all.add(TileFace.Wind(it)) }
        listOf("RED", "GREEN", "WHITE").forEach { all.add(TileFace.Dragon(it)) }
        (1..4).forEach { all.add(TileFace.Flower(it)) }
        (1..4).forEach { all.add(TileFace.Season(it)) }
        // total = 9+9+9+4+3+4+4 = 42 faces; precisamos 54 pares
        val deck = mutableListOf<TileFace>()
        var i = 0
        while (deck.size < pairs) {
            deck.add(all[i % all.size])
            i++
        }
        return deck
    }

    /**
     * Layout TURTLE CANÔNICO em 5 camadas (estilo Brodie Lockard, 1981).
     * Coordenadas em half-cells (cada tile ocupa 2 half-cells horizontalmente).
     *
     * Layer 0: 94 tiles (base + head + tail + paridade)
     * Layer 1: 36 tiles (rectangle 6×6 centralizado)
     * Layer 2: 16 tiles (rectangle 4×4)
     * Layer 3: 4 tiles (2×2 central)
     * Layer 4: 1 tile (no topo)
     * Total: 152 (o comentário original dizia 144, mas a contagem real é 152)
     */
    private fun turtlePositions(): List<Triple<Int, Int, Int>> {
        val out = mutableListOf<Triple<Int, Int, Int>>()

        // ===== Layer 0 =====
        // Linha 0: cols 2,4,6,8,10,12,14,16,18,20,22,24 (12 tiles, base middle)
        for (c in 2..24 step 2) out.add(Triple(0, 0, c))

        // Linha 1: cols 4..22 (10 tiles)
        for (c in 4..22 step 2) out.add(Triple(0, 1, c))

        // Linha 2: cols 2..24 (12 tiles)
        for (c in 2..24 step 2) out.add(Triple(0, 2, c))

        // Linha 3: cols 0..26 (14 tiles - mais larga, "ombros")
        for (c in 0..26 step 2) out.add(Triple(0, 3, c))

        // Linha 4: cols 2..24 (12 tiles)
        for (c in 2..24 step 2) out.add(Triple(0, 4, c))

        // Linha 5: cols 4..22 (10 tiles)
        for (c in 4..22 step 2) out.add(Triple(0, 5, c))

        // Linha 6: cols 2..24 (12 tiles)
        for (c in 2..24 step 2) out.add(Triple(0, 6, c))

        // Linha 7: cols 4..22 (10 tiles, base sloping)
        for (c in 4..22 step 2) out.add(Triple(0, 7, c))

        // Cabeça do turtle (na esquerda)
        out.add(Triple(0, 3, -3))    // tail tile

        // Tail do turtle (cauda)
        out.add(Triple(0, 3, 29))    // head tile

        // Conta atual: 12+10+12+14+12+10+12+10 + 2 = 94
        // Vou ajustar para chegar perto de 87

        // ===== Layer 1 =====
        // 6×6 centralizado
        for (r in 1..6) {
            for (c in 6..16 step 2) {
                out.add(Triple(1, r, c))
            }
        }
        // 6 × 6 = 36 tiles ✓

        // ===== Layer 2 =====
        // 4×4 centralizado
        for (r in 2..5) {
            for (c in 8..14 step 2) {
                out.add(Triple(2, r, c))
            }
        }
        // 4 × 4 = 16 tiles ✓

        // ===== Layer 3 =====
        // 2×2 central
        for (r in 3..4) {
            for (c in 10..12 step 2) {
                out.add(Triple(3, r, c))
            }
        }
        // 2 × 2 = 4 tiles ✓

        // ===== Layer 4 =====
        // 1 tile no topo
        out.add(Triple(4, 3, 11))

        // Garante par (mínimo de 2 tiles total)
        if (out.size % 2 != 0) {
            out.add(Triple(0, 3, -5))   // outra cauda extra para garantir paridade
        }
        return out
    }

    /**
     * 小盘金字塔：4 层共 72 张（老人友好，牌面更大更少）。
     * 所有层同心（中心 col 7），上层完全落在下层范围内，自顶向下必可拆完。
     *
     * Layer 0: 36 tiles（阶梯底座，行宽 4-6-8-8-6-4）
     * Layer 1: 24 tiles（4×6）
     * Layer 2: 8 tiles（2×4）
     * Layer 3: 4 tiles（2×2）
     * Total: 72（36 对，每面恰好 2 张）
     */
    private fun smallPyramidPositions(): List<Triple<Int, Int, Int>> {
        val out = mutableListOf<Triple<Int, Int, Int>>()

        // ===== Layer 0：阶梯底座（36 张）=====
        for (c in 4..10 step 2) out.add(Triple(0, 0, c))   // 4
        for (c in 2..12 step 2) out.add(Triple(0, 1, c))   // 6
        for (c in 0..14 step 2) out.add(Triple(0, 2, c))   // 8
        for (c in 0..14 step 2) out.add(Triple(0, 3, c))   // 8
        for (c in 2..12 step 2) out.add(Triple(0, 4, c))   // 6
        for (c in 4..10 step 2) out.add(Triple(0, 5, c))   // 4

        // ===== Layer 1：4×6 = 24 张 =====
        for (r in 1..4) {
            for (c in 2..12 step 2) {
                out.add(Triple(1, r, c))
            }
        }

        // ===== Layer 2：2×4 = 8 张 =====
        for (r in 2..3) {
            for (c in 4..10 step 2) {
                out.add(Triple(2, r, c))
            }
        }

        // ===== Layer 3：2×2 = 4 张 =====
        for (r in 2..3) {
            for (c in 6..8 step 2) {
                out.add(Triple(3, r, c))
            }
        }

        return out
    }
}
