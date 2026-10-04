package com.goodyaoshi.parentsgames.feature.games.solitaire.domain

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
data class SolitaireSnapshot(
    val tableau: List<List<String>>,        // "S:5:T" = spade rank-5 face-up
    val foundations: List<List<String>>,
    val stock: List<String>,
    val waste: List<String>,
    val moves: Int,
)

/**
 * Engine de Klondike (Paciência clássica) — single draw, foundations por naipe.
 *
 * Layout:
 *  - 7 colunas de tableau (col i tem i+1 cartas, a do topo virada)
 *  - 4 foundations (uma por naipe, monta do Ás ao Rei)
 *  - Stock + Waste (vira 1 carta por vez do stock pro waste)
 *
 * Regras de movimento no tableau:
 *  - Só Rei pode ir para coluna vazia
 *  - Carta segue sequência DESCENDENTE com naipe ALTERNADO (vermelho/preto)
 *
 * Regras de foundation:
 *  - Começa no Ás, sobe um a um (2, 3, ..., K) do MESMO naipe
 *
 * Vitória: todas as 52 cartas nas foundations.
 */
class SolitaireEngine(
    seed: Long = System.currentTimeMillis(),
    /** true = 构造式必赢发牌（默认，新游戏/重新开始/演示都用它）；false = 旧真随机发牌（仅演示兜底筛选用）。 */
    constructed: Boolean = true,
) {

    val tableau: Array<MutableList<Card>> = Array(7) { mutableListOf() }
    val foundations: Array<MutableList<Card>> = Array(4) { mutableListOf() }
    val stock: MutableList<Card> = mutableListOf()
    val waste: MutableList<Card> = mutableListOf()

    var moves: Int = 0; private set
    var stockResets: Int = 0; private set

    init { if (constructed) generateConstructedDeal(seed) else deal(seed) }

    private fun deal(seed: Long) {
        val deck = mutableListOf<Card>()
        for (suit in Suit.entries) for (rank in Rank.entries) {
            deck.add(Card(suit, rank, faceUp = false))
        }
        deck.shuffle(Random(seed))
        for (col in 0 until 7) {
            for (j in 0..col) {
                val card = deck.removeAt(0)
                tableau[col].add(card.flip(j == col))
            }
        }
        stock.addAll(deck)
    }

    /**
     * 构造式发牌——"从必赢结局反推"（替换真随机发牌，让零规则知识的老人也一定能赢）：
     *  - 牌列只放各花色的 1~7（低牌），每列是同一花色的连续升序 run：顶牌最小、正面朝上，
     *    其余背面朝下。打出顶牌即翻开下一张，而下一张恰好接得上基础堆（前驱刚被打出去），
     *    整列自级联清空，全程不需要任何牌列之间的移动；
     *  - 库存堆只放 8~K（高牌，共 24 张），单张翻牌 + 无限回收，任意顺序都能逐张进基础堆；
     *  - 每个花色的 1~7 拆成两个 run，四种拆法 (7)/(6,1)/(5,2)/(4,3) 与四个花色随机一一对应，
     *    run 尺寸恰好匹配 7/6+1/5+2/4+3 号列（1 基列号）；
     *  - 随机性来源：花色↔拆法的随机双射（4! 种）+ 库存洗牌。每局结构一致但外观不同，必然可赢。
     */
    private fun generateConstructedDeal(seed: Long) {
        val rnd = Random(seed)
        val bigCols = intArrayOf(6, 5, 4, 3)    // 大 run 所在列（0 基）：7/6/5/4 张
        val smallCols = intArrayOf(-1, 0, 1, 2) // 对应小 run 所在列：无/1/2/3 张
        val suits = Suit.entries.toMutableList().also { it.shuffle(rnd) }
        for (s in 0 until 4) {
            val suit = suits[s]
            val big = bigCols[s] + 1 // 大 run 尺寸 = 列号+1
            // 大 run：rank 1..big，从列底往顶放，顶（最后放入）= rank 1 正面朝上
            for (v in big downTo 1) {
                tableau[bigCols[s]].add(Card(suit, Rank.entries[v - 1], faceUp = v == 1))
            }
            // 小 run：rank (big+1)..7，顶 = big+1 正面朝上，等大 run 打完即可接续
            val sc = smallCols[s]
            if (sc >= 0) {
                for (v in 7 downTo big + 1) {
                    tableau[sc].add(Card(suit, Rank.entries[v - 1], faceUp = v == big + 1))
                }
            }
        }
        // 库存堆：4 个花色的 8~K 共 24 张，随机洗乱（无限回收保证任意顺序都可赢）
        val highs = mutableListOf<Card>()
        for (suit in Suit.entries) for (v in 8..13) {
            highs.add(Card(suit, Rank.entries[v - 1], faceUp = false))
        }
        highs.shuffle(rnd)
        stock.addAll(highs)
        checkConstructedDeal()
    }

    /** 构造自检（fail-fast，防构造代码写错）：列尺寸/单花色连续/朝向/库存 24 张高牌/52 张不重不漏。 */
    private fun checkConstructedDeal() {
        val seen = HashSet<Card>()
        for (col in 0 until 7) {
            val list = tableau[col]
            require(list.size == col + 1) { "构造发牌：第 ${col + 1} 列张数错误" }
            list.forEachIndexed { j, c ->
                require(seen.add(c)) { "构造发牌：出现重复牌" }
                require(c.suit == list[0].suit) { "构造发牌：列内花色混杂" }
                if (j > 0) require(list[j - 1].rank.value == c.rank.value + 1) { "构造发牌：列内点数不连续" }
            }
            require(list.last().faceUp && list.dropLast(1).all { !it.faceUp }) { "构造发牌：牌面朝向错误" }
        }
        require(foundations.all { it.isEmpty() } && waste.isEmpty() && stock.size == 24) {
            "构造发牌：堆分布错误"
        }
        stock.forEach { c ->
            require(seen.add(c)) { "构造发牌：出现重复牌" }
            require(c.rank.value >= 8) { "构造发牌：库存混入低牌" }
        }
        require(seen.size == 52) { "构造发牌：52 张不齐" }
    }

    /** Vira uma carta do stock para o waste (ou reseta se stock vazio). */
    fun drawFromStock(): Boolean {
        if (stock.isEmpty()) {
            if (waste.isEmpty()) return false
            stock.addAll(waste.reversed().map { it.flip(false) })
            waste.clear()
            stockResets++
            return true
        }
        val card = stock.removeAt(stock.size - 1)
        waste.add(card.flip(true))
        moves++
        return true
    }

    /**
     * Tenta mover cartas de [from] (com `fromIndex` no caso do tableau,
     * indicando a primeira carta selecionada da pilha) para [to].
     * Retorna `true` se moveu.
     */
    fun tryMove(from: Pile, fromIndex: Int, to: Pile): Boolean {
        val cards = collectMovableCards(from, fromIndex) ?: return false
        if (cards.isEmpty()) return false
        if (!canPlaceOn(cards, to)) return false

        // Executa a movimentação
        removeFromSource(from, fromIndex)
        placeOnDest(cards, to)
        flipTopIfNeeded(from)
        moves++
        return true
    }

    /**
     * 只读查询：[from]（tableau 时从 fromIndex 起）当前所有合法去处。
     * 供"选中提示去处"和"唯一去处自动完成"使用，不改动牌局。
     */
    fun legalTargets(from: Pile, fromIndex: Int): List<Pile> {
        val cards = collectMovableCards(from, fromIndex) ?: return emptyList()
        if (cards.isEmpty()) return emptyList()
        val targets = mutableListOf<Pile>()
        for (i in 0 until 7) {
            if (from is Pile.Tableau && from.index == i) continue
            val to = Pile.Tableau(i)
            if (canPlaceOn(cards, to)) targets.add(to)
        }
        for (i in 0 until 4) {
            if (from is Pile.Foundation && from.index == i) continue
            val to = Pile.Foundation(i)
            if (canPlaceOn(cards, to)) targets.add(to)
        }
        return targets
    }

    /**
     * Tenta enviar a carta do topo desta pilha para a foundation correspondente
     * (atalho de auto-move via duplo-clique etc.). Retorna true se moveu.
     */
    fun autoToFoundation(from: Pile): Boolean {
        val topCard = topOf(from) ?: return false
        if (!topCard.faceUp) return false
        val fIdx = topCard.suit.foundationIndex
        return tryMove(from, lastIndexOf(from), Pile.Foundation(fIdx))
    }

    fun isWin(): Boolean = foundations.sumOf { it.size } == 52

    /**
     * 死局检测：是否一张能走的牌都没有。
     * 依次检查（任一成立即未死局）：
     *  1. 翻库循环里有牌可打 —— 单张翻牌 + 无限回收，stock/waste 里每张牌迟早都会
     *     翻到弃牌堆顶，所以只要其中任何一张能上基础堆或牌列，玩家就有得走；
     *  2. 牌列内部或牌列→基础堆存在合法移动；
     *  3. 基础堆回移能解锁 —— 模拟把基础堆顶牌移回牌列一次（排除"放回去"这一步），
     *     之后若出现 1/2 的走法，说明回移有用，不算死局。
     */
    fun isDeadlocked(): Boolean {
        if (isWin()) return false
        if (hasAnyCycleMove()) return false
        if (hasAnyTableauMove()) return false
        return !anyFoundationPullbackUnlocks()
    }

    /** 死局恢复：把牌列 + 弃牌堆的牌全部收回、洗入库存堆；基础堆已放好的牌保留不动，
     *  因此之后总能接着往下玩（每种花色缺的下一张必在重洗后的牌堆里）。记一步。 */
    fun reshuffleBoard() {
        val cards = mutableListOf<Card>()
        for (col in 0 until 7) {
            cards.addAll(tableau[col])
            tableau[col].clear()
        }
        cards.addAll(waste)
        waste.clear()
        cards.shuffle()
        stock.addAll(cards.map { it.flip(false) })
        moves++
    }

    private fun hasAnyCycleMove(): Boolean {
        if (stock.isEmpty() && waste.isEmpty()) return false
        for (card in stock + waste) {
            if (canPlaceOn(listOf(card), Pile.Foundation(card.suit.foundationIndex))) return true
            for (i in 0 until 7) {
                if (canPlaceOn(listOf(card), Pile.Tableau(i))) return true
            }
        }
        return false
    }

    private fun hasAnyTableauMove(excludeFromCol: Int = -1, excludeToFoundation: Int = -1): Boolean {
        for (i in 0 until 7) {
            val list = tableau[i]
            val firstFaceUp = list.indexOfFirst { it.faceUp }
            if (firstFaceUp == -1) continue
            for (j in firstFaceUp until list.size) {
                val run = list.subList(j, list.size).toList()
                if (run.any { !it.faceUp }) continue
                for (k in 0 until 7) {
                    if (k == i) continue
                    if (canPlaceOn(run, Pile.Tableau(k))) return true
                }
                if (j == list.size - 1) {
                    // 回移模拟时排除"刚拉出来的那张原路放回基础堆"，那不算解锁
                    val isPullbackUndo =
                        i == excludeFromCol && run.first().suit.foundationIndex == excludeToFoundation
                    if (!isPullbackUndo &&
                        canPlaceOn(run, Pile.Foundation(run.first().suit.foundationIndex))
                    ) return true
                }
            }
        }
        return false
    }

    private fun anyFoundationPullbackUnlocks(): Boolean {
        for (f in 0 until 4) {
            val fl = foundations[f]
            if (fl.isEmpty()) continue
            val top = fl.last()
            for (t in 0 until 7) {
                if (!canPlaceOn(listOf(top), Pile.Tableau(t))) continue
                // 模拟一次回移，看是否产生新的走法
                fl.removeAt(fl.size - 1)
                tableau[t].add(top)
                val unlocked = hasAnyCycleMove() || hasAnyTableauMove(excludeFromCol = t, excludeToFoundation = f)
                tableau[t].removeAt(tableau[t].size - 1)
                fl.add(top)
                if (unlocked) return true
            }
        }
        return false
    }

    fun toSnapshot(): SolitaireSnapshot = SolitaireSnapshot(
        tableau = tableau.map { pile -> pile.map { encodeCard(it) } },
        foundations = foundations.map { pile -> pile.map { encodeCard(it) } },
        stock = stock.map { encodeCard(it) },
        waste = waste.map { encodeCard(it) },
        moves = moves,
    )

    fun loadFromSnapshot(snap: SolitaireSnapshot) {
        for (i in 0 until 7) {
            tableau[i].clear()
            tableau[i].addAll(snap.tableau.getOrNull(i)?.mapNotNull { decodeCard(it) }.orEmpty())
        }
        for (i in 0 until 4) {
            foundations[i].clear()
            foundations[i].addAll(snap.foundations.getOrNull(i)?.mapNotNull { decodeCard(it) }.orEmpty())
        }
        stock.clear(); stock.addAll(snap.stock.mapNotNull { decodeCard(it) })
        waste.clear(); waste.addAll(snap.waste.mapNotNull { decodeCard(it) })
        moves = snap.moves
    }

    private fun encodeCard(c: Card): String = "${c.suit.name[0]}:${c.rank.value}:${if (c.faceUp) "U" else "D"}"
    private fun decodeCard(s: String): Card? {
        val parts = s.split(":")
        if (parts.size != 3) return null
        val suit = Suit.entries.firstOrNull { it.name[0] == parts[0][0] } ?: return null
        val rank = Rank.entries.firstOrNull { it.value == parts[1].toIntOrNull() } ?: return null
        return Card(suit, rank, faceUp = parts[2] == "U")
    }

    // ----- Internals -----

    private fun collectMovableCards(from: Pile, fromIndex: Int): List<Card>? = when (from) {
        is Pile.Tableau -> {
            val list = tableau[from.index]
            if (fromIndex !in list.indices) null
            else {
                val candidates = list.subList(fromIndex, list.size).toList()
                if (candidates.any { !it.faceUp }) null else candidates
            }
        }
        is Pile.Foundation -> {
            val list = foundations[from.index]
            if (list.isEmpty()) null else listOf(list.last())
        }
        Pile.Waste -> if (waste.isEmpty()) null else listOf(waste.last())
    }

    private fun canPlaceOn(cards: List<Card>, to: Pile): Boolean {
        if (cards.isEmpty()) return false
        return when (to) {
            is Pile.Tableau -> {
                val list = tableau[to.index]
                val first = cards.first()
                if (list.isEmpty()) first.rank == Rank.KING
                else {
                    val top = list.last()
                    if (!top.faceUp) false
                    else top.rank.value == first.rank.value + 1 && top.suit.isRed != first.suit.isRed
                }
            }
            is Pile.Foundation -> {
                if (cards.size != 1) return false
                val card = cards.first()
                val list = foundations[to.index]
                if (list.isEmpty()) card.rank == Rank.ACE && card.suit.foundationIndex == to.index
                else {
                    val top = list.last()
                    top.suit == card.suit && card.rank.value == top.rank.value + 1
                }
            }
            Pile.Waste -> false
        }
    }

    private fun removeFromSource(from: Pile, fromIndex: Int) {
        when (from) {
            is Pile.Tableau -> {
                val list = tableau[from.index]
                while (list.size > fromIndex) list.removeAt(list.size - 1)
            }
            is Pile.Foundation -> foundations[from.index].removeAt(foundations[from.index].size - 1)
            Pile.Waste -> waste.removeAt(waste.size - 1)
        }
    }

    private fun placeOnDest(cards: List<Card>, to: Pile) {
        when (to) {
            is Pile.Tableau -> tableau[to.index].addAll(cards)
            is Pile.Foundation -> foundations[to.index].addAll(cards)
            Pile.Waste -> {}
        }
    }

    private fun flipTopIfNeeded(from: Pile) {
        if (from is Pile.Tableau) {
            val list = tableau[from.index]
            if (list.isNotEmpty() && !list.last().faceUp) {
                list[list.size - 1] = list.last().flip(true)
            }
        }
    }

    private fun topOf(pile: Pile): Card? = when (pile) {
        is Pile.Tableau -> tableau[pile.index].lastOrNull()
        is Pile.Foundation -> foundations[pile.index].lastOrNull()
        Pile.Waste -> waste.lastOrNull()
    }

    private fun lastIndexOf(pile: Pile): Int = when (pile) {
        is Pile.Tableau -> (tableau[pile.index].size - 1).coerceAtLeast(0)
        is Pile.Foundation -> (foundations[pile.index].size - 1).coerceAtLeast(0)
        Pile.Waste -> 0
    }
}
