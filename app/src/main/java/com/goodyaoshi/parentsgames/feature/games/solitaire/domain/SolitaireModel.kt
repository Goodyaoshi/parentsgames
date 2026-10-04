package com.goodyaoshi.parentsgames.feature.games.solitaire.domain

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("♠", false),
    HEARTS("♥", true),
    DIAMONDS("♦", true),
    CLUBS("♣", false);

    val foundationIndex: Int get() = ordinal
}

enum class Rank(val value: Int, val symbol: String) {
    ACE(1, "A"),
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
}

data class Card(val suit: Suit, val rank: Rank, val faceUp: Boolean) {
    fun flip(up: Boolean) = copy(faceUp = up)
}

/** Origem ou destino de uma seleção. */
sealed class Pile {
    data class Tableau(val index: Int) : Pile()        // 0..6
    data class Foundation(val index: Int) : Pile()     // 0..3
    data object Waste : Pile()
}

data class Selection(val pile: Pile, val fromIndex: Int)
