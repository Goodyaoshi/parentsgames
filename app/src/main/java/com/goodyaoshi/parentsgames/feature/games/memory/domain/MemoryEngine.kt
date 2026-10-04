package com.goodyaoshi.parentsgames.feature.games.memory.domain

import kotlin.random.Random
import kotlinx.serialization.Serializable

/** 色彩鲜明、老年机 emoji 也能显示的常见符号（数量 ≥ 最大对数即可）。 */
private val EmojiPool = listOf(
    "🍓", "🍎", "🍇", "🍊", "🍉", "🍍",
    "🌸", "🌻", "🐼", "🐰", "🐱", "🐢",
    "🐝", "🦋", "🐟", "🌈",
)

@Serializable
data class CardSnap(val emoji: String, val faceUp: Boolean, val matched: Boolean)

@Serializable
data class MemorySnapshot(
    val difficulty: String,
    val cards: List<CardSnap>,
    val matchedPairs: Int,
    val moves: Int,
)

data class MemoryCard(
    val emoji: String,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false,
)

/** 三档难度都固定 4 列，困难档只增加行数，保证卡牌不会因加大难度而变小。 */
enum class Difficulty(val rows: Int, val cols: Int, val pairs: Int, val label: String) {
    EASY(3, 4, 6, "简单"),
    MEDIUM(4, 4, 8, "中等"),
    HARD(6, 4, 12, "困难"),
}

sealed interface GameStatus {
    data object Playing : GameStatus
    data object Won : GameStatus
}

class MemoryEngine(val difficulty: Difficulty, val seed: Long = System.currentTimeMillis()) {

    private val rng = Random(seed)
    private val cards: MutableList<MemoryCard>
    /** 当前翻开、尚未盖回的牌索引（最多 2 张）。 */
    private val flipped = mutableListOf<Int>()

    var status: GameStatus = GameStatus.Playing
        private set
    var matchedPairs: Int = 0
        private set
    var moves: Int = 0
        private set

    init {
        // 每局随机挑 N 对，复制成对后洗匀摆好
        val picked = EmojiPool.shuffled(rng).take(difficulty.pairs)
        cards = (picked + picked).map { MemoryCard(emoji = it) }.toMutableList()
        cards.shuffle(rng)
    }

    val rows: Int get() = difficulty.rows
    val cols: Int get() = difficulty.cols
    val totalPairs: Int get() = difficulty.pairs

    /** 两张牌翻开但不匹配、等待盖回（ViewModel 在此期间禁点）。配对成功时 flipped 已清空。 */
    val isMismatchPending: Boolean get() = flipped.size == 2

    fun tap(r: Int, c: Int): Boolean {
        if (status !is GameStatus.Playing) return false
        if (flipped.size >= 2) return false
        val idx = r * cols + c
        if (idx !in cards.indices) return false
        val card = cards[idx]
        if (card.isMatched || card.isFaceUp) return false
        cards[idx] = card.copy(isFaceUp = true)
        flipped.add(idx)
        if (flipped.size == 2) {
            moves++
            val a = flipped[0]
            val b = flipped[1]
            if (cards[a].emoji == cards[b].emoji) {
                cards[a] = cards[a].copy(isMatched = true)
                cards[b] = cards[b].copy(isMatched = true)
                flipped.clear()
                matchedPairs++
                checkWin()
            }
            // 不匹配：保持翻开，由 ViewModel 延时后调 resolveMismatch() 盖回
        }
        return true
    }

    fun resolveMismatch() {
        if (flipped.size != 2) return
        for (i in flipped) {
            cards[i] = cards[i].copy(isFaceUp = false)
        }
        flipped.clear()
    }

    fun snapshot(): List<MemoryCard> = cards.toList()

    fun toSnapshot(): MemorySnapshot = MemorySnapshot(
        difficulty = difficulty.name,
        cards = cards.map { CardSnap(it.emoji, it.isFaceUp, it.isMatched) },
        matchedPairs = matchedPairs,
        moves = moves,
    )

    /** 恢复进度：进行中翻开的牌全部盖回，只保留已配对状态。 */
    fun loadFromSnapshot(snap: MemorySnapshot) {
        cards.clear()
        cards.addAll(snap.cards.map { MemoryCard(it.emoji, isFaceUp = false, isMatched = it.matched) })
        matchedPairs = snap.matchedPairs
        moves = snap.moves
        flipped.clear()
        if (matchedPairs >= totalPairs) status = GameStatus.Won
    }

    private fun checkWin() {
        if (matchedPairs == totalPairs) status = GameStatus.Won
    }
}
