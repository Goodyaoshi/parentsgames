package com.goodyaoshi.parentsgames.feature.games.mahjong.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class TileFace {
    abstract val displayChar: String
    abstract val familyId: String

    @Serializable data class Bamboo(val n: Int) : TileFace() {
        override val displayChar: String get() = "${n}竹"
        override val familyId: String get() = "BAMBOO_$n"
    }
    @Serializable data class Character(val n: Int) : TileFace() {
        override val displayChar: String get() = "${n}萬"
        override val familyId: String get() = "CHAR_$n"
    }
    @Serializable data class Circle(val n: Int) : TileFace() {
        override val displayChar: String get() = "${n}●"
        override val familyId: String get() = "CIRCLE_$n"
    }
    @Serializable data class Wind(val cardinal: String) : TileFace() {
        override val displayChar: String get() = cardinal
        override val familyId: String get() = "WIND_$cardinal"
    }
    @Serializable data class Dragon(val color: String) : TileFace() {
        override val displayChar: String get() = when (color) { "RED" -> "中"; "GREEN" -> "發"; else -> "白" }
        override val familyId: String get() = "DRAGON_$color"
    }
    @Serializable data class Flower(val n: Int) : TileFace() {
        override val displayChar: String get() = "❀"
        override val familyId: String get() = "FLOWER"      // equivalentes entre si
    }
    @Serializable data class Season(val n: Int) : TileFace() {
        override val displayChar: String get() = "❉"
        override val familyId: String get() = "SEASON"      // equivalentes entre si
    }
}

@Serializable
data class MahjongTile(
    val id: Int,
    val face: TileFace,
    val layer: Int,
    val row: Int,
    val col: Int,
    val removed: Boolean = false,
)

@Serializable
data class MahjongSnapshot(
    val tiles: List<MahjongTile>,
    val hintsUsed: Int,
    val undosUsed: Int,
    val reshufflesUsed: Int,
    val elapsedMs: Long,
)
