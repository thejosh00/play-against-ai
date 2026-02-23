package com.pokerai.analysis

import com.pokerai.engine.HandEvaluator
import com.pokerai.engine.HandRank
import com.pokerai.model.*

object DrawDetector {

    fun detectDraws(holeCards: HoleCards, communityCards: List<Card>): List<DrawInfo> {
        if (communityCards.size < 3 || communityCards.size > 4) return emptyList()

        val allCards = holeCards.toList() + communityCards
        val holeCardList = holeCards.toList()
        val isFlop = communityCards.size == 3

        // Check if we already have a flush (5+ cards of same suit) - if so, skip flush draw detection
        val hasMadeFlush = hasMadeFlush(allCards)

        val draws = mutableListOf<DrawInfo>()

        if (!hasMadeFlush) {
            detectFlushDraw(holeCardList, communityCards, allCards)?.let { draws.add(it) }
        }

        draws.addAll(detectStraightDraws(holeCardList, communityCards, allCards))

        if (isFlop) {
            if (!hasMadeFlush) {
                detectBackdoorFlush(holeCardList, allCards)?.let { draws.add(it) }
            }
            detectBackdoorStraight(holeCardList, allCards)?.let { draws.add(it) }
        }

        detectOvercards(holeCardList, communityCards, allCards)?.let { draws.add(it) }

        return draws
    }

    private fun hasMadeFlush(allCards: List<Card>): Boolean {
        return allCards.groupBy { it.suit }.any { (_, cards) -> cards.size >= 5 }
    }

    private fun detectFlushDraw(
        holeCards: List<Card>,
        communityCards: List<Card>,
        allCards: List<Card>
    ): DrawInfo? {
        val suitCounts = allCards.groupBy { it.suit }

        for ((suit, cardsOfSuit) in suitCounts) {
            if (cardsOfSuit.size != 4) continue

            val holeCardsOfSuit = holeCards.filter { it.suit == suit }
            if (holeCardsOfSuit.isEmpty()) continue

            val communityCardsOfSuit = communityCards.filter { it.suit == suit }
            val highestHoleCardOfSuit = holeCardsOfSuit.maxOf { it.rank.value }
            val highestCommunityCardOfSuit = if (communityCardsOfSuit.isEmpty()) 0
                else communityCardsOfSuit.maxOf { it.rank.value }

            val isNut = highestHoleCardOfSuit > highestCommunityCardOfSuit

            return DrawInfo(
                type = if (isNut) DrawType.NUT_FLUSH_DRAW else DrawType.FLUSH_DRAW,
                outs = 9,
                isNut = isNut
            )
        }
        return null
    }

    private fun detectStraightDraws(
        holeCards: List<Card>,
        communityCards: List<Card>,
        allCards: List<Card>
    ): List<DrawInfo> {
        val draws = mutableListOf<DrawInfo>()
        val uniqueRanks = allCards.map { it.rank.value }.toSortedSet()
        val holeRanks = holeCards.map { it.rank.value }.toSet()

        // Include ace as low (1) for wheel detection
        val ranksWithLowAce = uniqueRanks.toMutableSet()
        if (Rank.ACE.value in uniqueRanks) {
            ranksWithLowAce.add(1)
        }
        val holeRanksWithLowAce = holeRanks.toMutableSet()
        if (Rank.ACE.value in holeRanks) {
            holeRanksWithLowAce.add(1)
        }

        var foundOesd = false
        var foundGutshot = false

        // Check all possible 5-card straight windows (1-5 through 10-14)
        for (low in 1..10) {
            val high = low + 4
            val window = (low..high).toSet()
            val heldInWindow = ranksWithLowAce.intersect(window)

            if (heldInWindow.size != 4) continue

            // At least one hole card must contribute to the window
            val holeContribution = holeRanksWithLowAce.intersect(window)
            if (holeContribution.isEmpty()) continue

            val missingRank = (window - heldInWindow).single()

            // Determine if OESD or gutshot
            if (missingRank == low || missingRank == high) {
                // Missing card is at an end - could be OESD if the OTHER end is also open
                // Check for open-endedness: need 4 consecutive ranks with both ends completable
                if (isOpenEnded(heldInWindow)) {
                    if (!foundOesd) {
                        draws.add(DrawInfo(type = DrawType.OESD, outs = 8, isNut = false))
                        foundOesd = true
                    }
                } else {
                    if (!foundGutshot) {
                        draws.add(DrawInfo(type = DrawType.GUTSHOT, outs = 4, isNut = false))
                        foundGutshot = true
                    }
                }
            } else {
                // Missing card is internal → gutshot
                if (!foundGutshot) {
                    draws.add(DrawInfo(type = DrawType.GUTSHOT, outs = 4, isNut = false))
                    foundGutshot = true
                }
            }

            if (foundOesd && foundGutshot) break
        }

        return draws
    }

    private fun isOpenEnded(
        heldInWindow: Set<Int>
    ): Boolean {
        // We have 4 of 5 ranks in a straight window, missing one end.
        // It's open-ended if the 4 consecutive ranks we hold can also complete
        // a straight by filling the OTHER side.
        //
        // The 4 ranks we hold are the window minus the missing rank.
        val fourRanks = heldInWindow.sorted()

        // For OESD, the 4 held ranks must be consecutive
        for (i in 0 until fourRanks.size - 1) {
            if (fourRanks[i + 1] - fourRanks[i] != 1) return false
        }

        val lowestHeld = fourRanks.first()
        val highestHeld = fourRanks.last()

        // Can't go above ace (14) or below 1
        val canCompleteBelow = lowestHeld - 1 >= 1
        val canCompleteAbove = highestHeld + 1 <= 14

        return canCompleteBelow && canCompleteAbove
    }

    private fun detectBackdoorFlush(
        holeCards: List<Card>,
        allCards: List<Card>
    ): DrawInfo? {
        val suitCounts = allCards.groupBy { it.suit }

        for ((suit, cardsOfSuit) in suitCounts) {
            if (cardsOfSuit.size != 3) continue

            val holeCardsOfSuit = holeCards.filter { it.suit == suit }
            if (holeCardsOfSuit.isEmpty()) continue

            return DrawInfo(type = DrawType.BACKDOOR_FLUSH, outs = 1, isNut = false)
        }
        return null
    }

    private fun detectBackdoorStraight(
        holeCards: List<Card>,
        allCards: List<Card>
    ): DrawInfo? {
        val uniqueRanks = allCards.map { it.rank.value }.toSortedSet()
        val holeRanks = holeCards.map { it.rank.value }.toSet()

        val ranksWithLowAce = uniqueRanks.toMutableSet()
        if (Rank.ACE.value in uniqueRanks) {
            ranksWithLowAce.add(1)
        }
        val holeRanksWithLowAce = holeRanks.toMutableSet()
        if (Rank.ACE.value in holeRanks) {
            holeRanksWithLowAce.add(1)
        }

        // Check all 5-card straight windows for 3 cards present
        for (low in 1..10) {
            val high = low + 4
            val window = (low..high).toSet()
            val heldInWindow = ranksWithLowAce.intersect(window)

            if (heldInWindow.size != 3) continue

            val holeContribution = holeRanksWithLowAce.intersect(window)
            if (holeContribution.isEmpty()) continue

            return DrawInfo(type = DrawType.BACKDOOR_STRAIGHT, outs = 1, isNut = false)
        }
        return null
    }

    private fun detectOvercards(
        holeCards: List<Card>,
        communityCards: List<Card>,
        allCards: List<Card>
    ): DrawInfo? {
        if (holeCards.size != 2) return null

        val highestBoardRank = communityCards.maxOf { it.rank.value }
        val bothAbove = holeCards.all { it.rank.value > highestBoardRank }

        if (!bothAbove) return null

        // Only count overcards as a draw if no made hand (no pair or better)
        val evaluation = HandEvaluator.evaluateBest(allCards)
        if (evaluation.rank >= HandRank.ONE_PAIR) return null

        return DrawInfo(type = DrawType.OVERCARDS, outs = 6, isNut = false)
    }
}
