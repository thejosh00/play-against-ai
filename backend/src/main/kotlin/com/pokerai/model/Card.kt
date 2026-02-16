package com.pokerai.model

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val symbol: String) {
    HEARTS("h"),
    DIAMONDS("d"),
    CLUBS("c"),
    SPADES("s");

    companion object {
        fun fromSymbol(s: String): Suit = entries.first { it.symbol == s }
    }
}

@Serializable
enum class Rank(val value: Int, val symbol: String) {
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "T"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A");

    companion object {
        fun fromSymbol(s: String): Rank = entries.first { it.symbol == s }
    }
}

@Serializable
data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {
    override fun compareTo(other: Card): Int = rank.value.compareTo(other.rank.value)

    val notation: String get() = "${rank.symbol}${suit.symbol}"

    override fun toString(): String = notation

    companion object {
        fun fromNotation(notation: String): Card {
            require(notation.length == 2) { "Card notation must be 2 characters: $notation" }
            return Card(
                Rank.fromSymbol(notation[0].toString()),
                Suit.fromSymbol(notation[1].toString())
            )
        }
    }
}

data class HoleCards(val card1: Card, val card2: Card) {
    val high: Rank get() = maxOf(card1.rank, card2.rank, compareBy { it.value })
    val low: Rank get() = minOf(card1.rank, card2.rank, compareBy { it.value })
    val isPair: Boolean get() = card1.rank == card2.rank
    val isSuited: Boolean get() = card1.suit == card2.suit

    val notation: String
        get() {
            val suffix = when {
                isPair -> ""
                isSuited -> "s"
                else -> "o"
            }
            return "${high.symbol}${low.symbol}$suffix"
        }

    fun toList(): List<Card> = listOf(card1, card2)
}
