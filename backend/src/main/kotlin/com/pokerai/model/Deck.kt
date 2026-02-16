package com.pokerai.model

class Deck {
    private val cards: MutableList<Card> = mutableListOf()
    private var index = 0

    init {
        reset()
    }

    fun reset() {
        cards.clear()
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                cards.add(Card(rank, suit))
            }
        }
        index = 0
    }

    fun shuffle() {
        cards.shuffle()
        index = 0
    }

    fun deal(): Card {
        check(index < cards.size) { "No more cards in deck" }
        return cards[index++]
    }

    fun deal(count: Int): List<Card> = (1..count).map { deal() }

    val remaining: Int get() = cards.size - index
}
