package com.pokerai.analysis

import com.pokerai.model.*

object BoardAnalyzer {

    fun analyze(communityCards: List<Card>, previousCommunityCount: Int = 0): BoardAnalysis {
        require(communityCards.size in 3..5) { "Need 3-5 community cards, got ${communityCards.size}" }

        val cardCount = communityCards.size

        // Suit analysis
        val suitCounts = communityCards.groupBy { it.suit }.mapValues { it.value.size }
        val maxSuitCount = suitCounts.values.max()

        val monotone = when (cardCount) {
            3 -> maxSuitCount == 3
            4 -> maxSuitCount == 4
            else -> maxSuitCount >= 4
        }
        val twoTone = maxSuitCount == 2
        val rainbow = maxSuitCount == 1
        val flushPossible = maxSuitCount >= 3
        val flushDrawPossible = maxSuitCount >= 2
        val dominantSuit = if (maxSuitCount > 1) suitCounts.maxByOrNull { it.value }?.key else null

        // Rank analysis
        val rankCounts = communityCards.groupBy { it.rank }.mapValues { it.value.size }
        val paired = rankCounts.values.any { it >= 2 }
        val doublePaired = rankCounts.values.count { it >= 2 } >= 2
        val trips = rankCounts.values.any { it >= 3 }
        val highCard = communityCards.maxBy { it.rank.value }.rank
        val lowCard = communityCards.minBy { it.rank.value }.rank

        val distinctValues = communityCards.map { it.rank.value }.distinct().sorted()
        val withLowAce = if (Rank.ACE.value in distinctValues) (distinctValues + 1).sorted() else distinctValues

        val connected = distinctValues.zipWithNext().any { (a, b) -> b - a in 1..2 }
        val highlyConnected = hasThreeInFiveRankSpan(withLowAce.toSet())
        val straightPossible = highlyConnected
        val straightDrawHeavy = checkStraightDrawHeavy(withLowAce.toSet())

        // Turn/river change detection
        val flushCompletedThisStreet: Boolean
        val straightCompletedThisStreet: Boolean
        val boardPairedThisStreet: Boolean

        if (previousCommunityCount == 0 || previousCommunityCount >= communityCards.size) {
            flushCompletedThisStreet = false
            straightCompletedThisStreet = false
            boardPairedThisStreet = false
        } else {
            val previousCards = communityCards.take(previousCommunityCount)
            val newCards = communityCards.drop(previousCommunityCount)

            flushCompletedThisStreet = newCards.any { newCard ->
                previousCards.count { it.suit == newCard.suit } >= 2
            }

            boardPairedThisStreet = newCards.any { newCard ->
                previousCards.any { it.rank == newCard.rank }
            }

            straightCompletedThisStreet = detectStraightCompleted(communityCards, previousCards, newCards)
        }

        // Wetness classification
        val wetness = classifyWetness(
            monotone, twoTone, flushPossible, flushDrawPossible,
            connected, highlyConnected, straightPossible, straightDrawHeavy, paired, rainbow
        )

        val description = buildDescription(
            wetness, monotone, twoTone, rainbow, flushPossible,
            connected, highlyConnected, paired, doublePaired, trips,
            straightPossible, flushDrawPossible
        )

        return BoardAnalysis(
            wetness = wetness,
            monotone = monotone, twoTone = twoTone, rainbow = rainbow,
            flushPossible = flushPossible, flushDrawPossible = flushDrawPossible,
            dominantSuit = dominantSuit,
            paired = paired, doublePaired = doublePaired, trips = trips,
            connected = connected, highlyConnected = highlyConnected,
            highCard = highCard, lowCard = lowCard,
            straightPossible = straightPossible, straightDrawHeavy = straightDrawHeavy,
            flushCompletedThisStreet = flushCompletedThisStreet,
            straightCompletedThisStreet = straightCompletedThisStreet,
            boardPairedThisStreet = boardPairedThisStreet,
            description = description
        )
    }

    private fun hasThreeInFiveRankSpan(ranksWithLowAce: Set<Int>): Boolean {
        for (low in 1..10) {
            val window = (low..low + 4).toSet()
            if (ranksWithLowAce.intersect(window).size >= 3) return true
        }
        return false
    }

    private fun checkStraightDrawHeavy(ranksWithLowAce: Set<Int>): Boolean {
        for (low in 1..10) {
            val window = (low..low + 4).toSet()
            val inWindow = ranksWithLowAce.intersect(window).sorted()
            if (inWindow.size >= 3) {
                if (inWindow.zipWithNext().any { (a, b) -> b - a == 1 }) return true
            }
        }
        return false
    }

    private fun detectStraightCompleted(
        allCards: List<Card>,
        previousCards: List<Card>,
        newCards: List<Card>
    ): Boolean {
        val allRanks = allCards.map { it.rank.value }.distinct().toMutableSet()
        if (Rank.ACE.value in allRanks) allRanks.add(1)

        val prevRanks = previousCards.map { it.rank.value }.distinct().toMutableSet()
        if (previousCards.any { it.rank == Rank.ACE }) prevRanks.add(1)

        val newRankValues = newCards.map { it.rank.value }.toMutableSet()
        if (newCards.any { it.rank == Rank.ACE }) newRankValues.add(1)

        for (low in 1..10) {
            val window = (low..low + 4).toSet()
            val allInWindow = allRanks.intersect(window)
            if (allInWindow.size < 3) continue
            if (newRankValues.intersect(window).isEmpty()) continue

            val prevInWindow = prevRanks.intersect(window)
            if (prevInWindow.size < 3 || allInWindow.size > prevInWindow.size) return true
        }
        return false
    }

    private fun classifyWetness(
        monotone: Boolean, twoTone: Boolean,
        flushPossible: Boolean, flushDrawPossible: Boolean,
        connected: Boolean, highlyConnected: Boolean,
        straightPossible: Boolean, straightDrawHeavy: Boolean,
        paired: Boolean, rainbow: Boolean
    ): BoardWetness {
        // VERY_WET
        if (monotone) return BoardWetness.VERY_WET
        if (straightPossible && flushDrawPossible) return BoardWetness.VERY_WET
        if (highlyConnected && flushDrawPossible) return BoardWetness.VERY_WET

        // WET
        if (twoTone && connected) return BoardWetness.WET
        if (flushPossible) return BoardWetness.WET
        if (straightDrawHeavy) return BoardWetness.WET
        if (highlyConnected && rainbow) return BoardWetness.WET

        // SEMI_WET
        if (twoTone && !connected) return BoardWetness.SEMI_WET
        if (connected && rainbow) return BoardWetness.SEMI_WET
        if (paired) return BoardWetness.SEMI_WET

        // DRY
        return BoardWetness.DRY
    }

    private fun buildDescription(
        wetness: BoardWetness,
        monotone: Boolean, twoTone: Boolean, rainbow: Boolean,
        flushPossible: Boolean,
        connected: Boolean, highlyConnected: Boolean,
        paired: Boolean, doublePaired: Boolean, trips: Boolean,
        straightPossible: Boolean, flushDrawPossible: Boolean
    ): String {
        val label = when (wetness) {
            BoardWetness.DRY -> "Dry"
            BoardWetness.SEMI_WET -> "Semi-wet"
            BoardWetness.WET -> "Wet"
            BoardWetness.VERY_WET -> "Very wet"
        }

        val qualifiers = mutableListOf<String>()

        if (wetness == BoardWetness.VERY_WET) {
            when {
                monotone -> qualifiers.add("monotone")
                straightPossible && flushDrawPossible -> qualifiers.add("flush and straight possible")
                highlyConnected && flushDrawPossible -> qualifiers.add("flush draw, highly connected")
            }
        } else {
            when {
                flushPossible -> qualifiers.add("flush possible")
                twoTone -> qualifiers.add("two-tone")
                rainbow -> qualifiers.add("rainbow")
            }

            when {
                highlyConnected -> qualifiers.add("highly connected")
                connected -> qualifiers.add("connected")
                else -> if (wetness == BoardWetness.DRY) qualifiers.add("unconnected")
            }

            when {
                trips -> qualifiers.add("trips on board")
                doublePaired -> qualifiers.add("double paired")
                paired -> qualifiers.add("paired")
            }
        }

        return "$label (${qualifiers.joinToString(", ")})"
    }
}
