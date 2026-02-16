package com.pokerai.engine

import com.pokerai.model.Card
import com.pokerai.model.Rank

enum class HandRank(val value: Int, val displayName: String) {
    HIGH_CARD(0, "High Card"),
    ONE_PAIR(1, "One Pair"),
    TWO_PAIR(2, "Two Pair"),
    THREE_OF_A_KIND(3, "Three of a Kind"),
    STRAIGHT(4, "Straight"),
    FLUSH(5, "Flush"),
    FULL_HOUSE(6, "Full House"),
    FOUR_OF_A_KIND(7, "Four of a Kind"),
    STRAIGHT_FLUSH(8, "Straight Flush"),
    ROYAL_FLUSH(9, "Royal Flush");
}

data class HandEvaluation(
    val rank: HandRank,
    val kickers: List<Int>,
    val description: String
) : Comparable<HandEvaluation> {
    override fun compareTo(other: HandEvaluation): Int {
        if (rank != other.rank) return rank.value.compareTo(other.rank.value)
        for (i in kickers.indices) {
            if (i >= other.kickers.size) return 1
            val cmp = kickers[i].compareTo(other.kickers[i])
            if (cmp != 0) return cmp
        }
        return 0
    }
}

object HandEvaluator {

    fun evaluateBest(sevenCards: List<Card>): HandEvaluation {
        require(sevenCards.size in 5..7) { "Need 5-7 cards, got ${sevenCards.size}" }
        if (sevenCards.size == 5) return evaluate5(sevenCards)

        return combinations(sevenCards, 5)
            .map { evaluate5(it) }
            .max()
    }

    fun evaluate5(cards: List<Card>): HandEvaluation {
        require(cards.size == 5) { "Need exactly 5 cards" }

        val sorted = cards.sortedByDescending { it.rank.value }
        val values = sorted.map { it.rank.value }
        val suits = sorted.map { it.suit }

        val isFlush = suits.all { it == suits[0] }
        val isStraight = isStraight(values)
        val isWheel = isWheel(values)

        val groups = values.groupBy { it }
            .map { (value, cards) -> cards.size to value }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })

        val counts = groups.map { it.first }
        val groupValues = groups.map { it.second }

        return when {
            isFlush && isStraight && values[0] == Rank.ACE.value ->
                HandEvaluation(HandRank.ROYAL_FLUSH, listOf(14), "Royal Flush")

            isFlush && (isStraight || isWheel) -> {
                val high = if (isWheel) 5 else values[0]
                HandEvaluation(HandRank.STRAIGHT_FLUSH, listOf(high), "Straight Flush, ${rankName(high)} high")
            }

            counts[0] == 4 ->
                HandEvaluation(
                    HandRank.FOUR_OF_A_KIND,
                    listOf(groupValues[0], groupValues[1]),
                    "Four of a Kind, ${rankName(groupValues[0])}s"
                )

            counts[0] == 3 && counts[1] == 2 ->
                HandEvaluation(
                    HandRank.FULL_HOUSE,
                    listOf(groupValues[0], groupValues[1]),
                    "Full House, ${rankName(groupValues[0])}s full of ${rankName(groupValues[1])}s"
                )

            isFlush ->
                HandEvaluation(HandRank.FLUSH, values, "Flush, ${rankName(values[0])} high")

            isStraight || isWheel -> {
                val high = if (isWheel) 5 else values[0]
                HandEvaluation(HandRank.STRAIGHT, listOf(high), "Straight, ${rankName(high)} high")
            }

            counts[0] == 3 ->
                HandEvaluation(
                    HandRank.THREE_OF_A_KIND,
                    listOf(groupValues[0]) + groupValues.drop(1),
                    "Three of a Kind, ${rankName(groupValues[0])}s"
                )

            counts[0] == 2 && counts[1] == 2 ->
                HandEvaluation(
                    HandRank.TWO_PAIR,
                    listOf(groupValues[0], groupValues[1], groupValues[2]),
                    "Two Pair, ${rankName(groupValues[0])}s and ${rankName(groupValues[1])}s"
                )

            counts[0] == 2 ->
                HandEvaluation(
                    HandRank.ONE_PAIR,
                    listOf(groupValues[0]) + groupValues.drop(1),
                    "Pair of ${rankName(groupValues[0])}s"
                )

            else ->
                HandEvaluation(HandRank.HIGH_CARD, values, "${rankName(values[0])} high")
        }
    }

    private fun isStraight(sortedValues: List<Int>): Boolean {
        for (i in 0 until sortedValues.size - 1) {
            if (sortedValues[i] - sortedValues[i + 1] != 1) return false
        }
        return true
    }

    private fun isWheel(sortedValues: List<Int>): Boolean {
        return sortedValues == listOf(14, 5, 4, 3, 2)
    }

    private fun rankName(value: Int): String = when (value) {
        14 -> "Ace"
        13 -> "King"
        12 -> "Queen"
        11 -> "Jack"
        10 -> "Ten"
        9 -> "Nine"
        8 -> "Eight"
        7 -> "Seven"
        6 -> "Six"
        5 -> "Five"
        4 -> "Four"
        3 -> "Three"
        2 -> "Two"
        else -> value.toString()
    }

    private fun <T> combinations(list: List<T>, k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (list.isEmpty()) return emptyList()
        val head = list.first()
        val tail = list.drop(1)
        val withHead = combinations(tail, k - 1).map { listOf(head) + it }
        val withoutHead = combinations(tail, k)
        return withHead + withoutHead
    }
}
