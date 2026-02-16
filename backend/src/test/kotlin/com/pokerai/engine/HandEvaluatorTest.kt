package com.pokerai.engine

import com.pokerai.model.Card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandEvaluatorTest {

    private fun cards(vararg notations: String): List<Card> =
        notations.map { Card.fromNotation(it) }

    @Test
    fun `royal flush beats straight flush`() {
        val royal = HandEvaluator.evaluate5(cards("As", "Ks", "Qs", "Js", "Ts"))
        val straightFlush = HandEvaluator.evaluate5(cards("9h", "8h", "7h", "6h", "5h"))
        assertEquals(HandRank.ROYAL_FLUSH, royal.rank)
        assertEquals(HandRank.STRAIGHT_FLUSH, straightFlush.rank)
        assertTrue(royal > straightFlush)
    }

    @Test
    fun `four of a kind`() {
        val quads = HandEvaluator.evaluate5(cards("Ah", "Ad", "Ac", "As", "Kh"))
        assertEquals(HandRank.FOUR_OF_A_KIND, quads.rank)
    }

    @Test
    fun `full house`() {
        val fullHouse = HandEvaluator.evaluate5(cards("Kh", "Kd", "Kc", "Qs", "Qh"))
        assertEquals(HandRank.FULL_HOUSE, fullHouse.rank)
    }

    @Test
    fun `flush`() {
        val flush = HandEvaluator.evaluate5(cards("Ah", "Jh", "9h", "5h", "3h"))
        assertEquals(HandRank.FLUSH, flush.rank)
    }

    @Test
    fun `straight`() {
        val straight = HandEvaluator.evaluate5(cards("9h", "8d", "7c", "6s", "5h"))
        assertEquals(HandRank.STRAIGHT, straight.rank)
    }

    @Test
    fun `wheel straight (A-5)`() {
        val wheel = HandEvaluator.evaluate5(cards("Ah", "2d", "3c", "4s", "5h"))
        assertEquals(HandRank.STRAIGHT, wheel.rank)
        assertEquals(listOf(5), wheel.kickers)
    }

    @Test
    fun `three of a kind`() {
        val trips = HandEvaluator.evaluate5(cards("7h", "7d", "7c", "Ks", "2h"))
        assertEquals(HandRank.THREE_OF_A_KIND, trips.rank)
    }

    @Test
    fun `two pair`() {
        val twoPair = HandEvaluator.evaluate5(cards("Ah", "Ad", "Kc", "Ks", "Qh"))
        assertEquals(HandRank.TWO_PAIR, twoPair.rank)
    }

    @Test
    fun `one pair`() {
        val pair = HandEvaluator.evaluate5(cards("Jh", "Jd", "9c", "5s", "2h"))
        assertEquals(HandRank.ONE_PAIR, pair.rank)
    }

    @Test
    fun `high card`() {
        val highCard = HandEvaluator.evaluate5(cards("Ah", "Kd", "9c", "5s", "2h"))
        assertEquals(HandRank.HIGH_CARD, highCard.rank)
    }

    @Test
    fun `hand ranking order is correct`() {
        val royalFlush = HandEvaluator.evaluate5(cards("As", "Ks", "Qs", "Js", "Ts"))
        val straightFlush = HandEvaluator.evaluate5(cards("9h", "8h", "7h", "6h", "5h"))
        val quads = HandEvaluator.evaluate5(cards("Ah", "Ad", "Ac", "As", "Kh"))
        val fullHouse = HandEvaluator.evaluate5(cards("Kh", "Kd", "Kc", "Qs", "Qh"))
        val flush = HandEvaluator.evaluate5(cards("Ah", "Jh", "9h", "5h", "3h"))
        val straight = HandEvaluator.evaluate5(cards("Td", "9c", "8h", "7s", "6d"))
        val trips = HandEvaluator.evaluate5(cards("7h", "7d", "7c", "Ks", "2h"))
        val twoPair = HandEvaluator.evaluate5(cards("Ad", "Ac", "Kd", "Ks", "Qh"))
        val pair = HandEvaluator.evaluate5(cards("Jh", "Jd", "9c", "5s", "2h"))
        val highCard = HandEvaluator.evaluate5(cards("Ah", "Kd", "9c", "5s", "2h"))

        assertTrue(royalFlush > straightFlush)
        assertTrue(straightFlush > quads)
        assertTrue(quads > fullHouse)
        assertTrue(fullHouse > flush)
        assertTrue(flush > straight)
        assertTrue(straight > trips)
        assertTrue(trips > twoPair)
        assertTrue(twoPair > pair)
        assertTrue(pair > highCard)
    }

    @Test
    fun `best 5 from 7 cards`() {
        // Has a flush hidden in 7 cards
        val sevenCards = cards("Ah", "Kh", "Qh", "Jh", "2h", "3d", "4c")
        val best = HandEvaluator.evaluateBest(sevenCards)
        assertEquals(HandRank.FLUSH, best.rank)
    }

    @Test
    fun `pair kicker comparison`() {
        val pairAceHighKicker = HandEvaluator.evaluate5(cards("Jh", "Jd", "Ac", "5s", "2h"))
        val pairKingHighKicker = HandEvaluator.evaluate5(cards("Jh", "Jd", "Kc", "5s", "2h"))
        assertTrue(pairAceHighKicker > pairKingHighKicker)
    }

    @Test
    fun `same hand ties`() {
        val hand1 = HandEvaluator.evaluate5(cards("Ah", "Kd", "Qc", "Js", "9h"))
        val hand2 = HandEvaluator.evaluate5(cards("As", "Kc", "Qh", "Jd", "9c"))
        assertEquals(0, hand1.compareTo(hand2))
    }
}
