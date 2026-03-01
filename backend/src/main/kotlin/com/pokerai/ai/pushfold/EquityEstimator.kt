package com.pokerai.ai.pushfold

import kotlin.math.abs

object EquityEstimator {

    fun estimate(
        callerHand: String,
        estimatedShoveRange: Int,
        callerHandRank: Int
    ): Double {
        // Base equity from position in shover's range
        val rangePosition = callerHandRank.toDouble() / estimatedShoveRange

        val baseEquity = when {
            rangePosition <= 0.1 -> 0.75
            rangePosition <= 0.2 -> 0.68
            rangePosition <= 0.3 -> 0.62
            rangePosition <= 0.4 -> 0.57
            rangePosition <= 0.5 -> 0.52
            rangePosition <= 0.6 -> 0.48
            rangePosition <= 0.7 -> 0.43
            rangePosition <= 0.8 -> 0.38
            rangePosition <= 0.9 -> 0.33
            rangePosition <= 1.0 -> 0.28
            else -> 0.22
        }

        // Hand type modifiers
        val handModifier = when {
            // Pairs: guaranteed equity, always coinflip or better
            isPair(callerHand) -> when (pairRank(callerHand)) {
                in 12..14 -> 0.08  // AA-QQ dominate wide ranges
                in 9..11 -> 0.06   // JJ-99 strong coinflip hands
                in 5..8 -> 0.04    // 88-55 still decent
                else -> 0.02       // small pairs, set mining equity
            }

            // Suited aces: good equity from flush potential + ace high
            isSuitedAce(callerHand) -> 0.04

            // Suited connectors: hard to dominate, straight/flush potential
            isSuitedConnector(callerHand) -> 0.03

            // Suited one-gappers: similar but slightly worse
            isSuitedOneGapper(callerHand) -> 0.02

            // Offsuit broadway: domination risk
            isOffsuitBroadway(callerHand) -> -0.02

            // Dominated aces: ATo, A9o etc against ranges with AK/AQ/AJ
            isDominatedAce(callerHand, estimatedShoveRange) -> -0.06

            // Dominated kings: KTo, K9o etc
            isDominatedKing(callerHand, estimatedShoveRange) -> -0.05

            else -> 0.0
        }

        // Range width modifier
        // Against very wide ranges, hands with blockers gain equity
        // Against very tight ranges, non-premium hands lose equity
        val rangeWidthModifier = when {
            estimatedShoveRange > 80 -> 0.03   // very wide, most hands do ok
            estimatedShoveRange > 50 -> 0.01   // wide
            estimatedShoveRange < 15 -> -0.04  // very tight, they have premiums
            estimatedShoveRange < 25 -> -0.02  // tight
            else -> 0.0
        }

        return (baseEquity + handModifier + rangeWidthModifier)
            .coerceIn(0.15, 0.85)
    }

    private fun isPair(hand: String): Boolean =
        hand.length >= 2 && hand[0] == hand[1]

    private fun pairRank(hand: String): Int = rankValue(hand[0])

    private fun isSuitedAce(hand: String): Boolean =
        hand.startsWith("A") && hand.endsWith("s") && !isPair(hand)

    private fun isSuitedConnector(hand: String): Boolean {
        if (!hand.endsWith("s")) return false
        val r1 = rankValue(hand[0])
        val r2 = rankValue(hand[1])
        return abs(r1 - r2) == 1
    }

    private fun isSuitedOneGapper(hand: String): Boolean {
        if (!hand.endsWith("s")) return false
        val r1 = rankValue(hand[0])
        val r2 = rankValue(hand[1])
        return abs(r1 - r2) == 2
    }

    private fun isOffsuitBroadway(hand: String): Boolean {
        if (!hand.endsWith("o")) return false
        val r1 = rankValue(hand[0])
        val r2 = rankValue(hand[1])
        return r1 >= 10 && r2 >= 10
    }

    private fun isDominatedAce(hand: String, shoveRange: Int): Boolean {
        if (!hand.startsWith("A")) return false
        if (isPair(hand)) return false
        if (hand.endsWith("s")) return false
        val kicker = rankValue(hand[1])
        // Against tight ranges, ace-rag is dominated by AK/AQ/AJ
        return kicker <= 10 && shoveRange <= 40
    }

    private fun isDominatedKing(hand: String, shoveRange: Int): Boolean {
        if (!hand.startsWith("K")) return false
        if (isPair(hand)) return false
        if (hand.endsWith("s")) return false
        val kicker = rankValue(hand[1])
        return kicker <= 10 && shoveRange <= 50
    }

    private fun rankValue(c: Char): Int = when (c) {
        'A' -> 14; 'K' -> 13; 'Q' -> 12; 'J' -> 11; 'T' -> 10
        else -> c.digitToInt()
    }
}
