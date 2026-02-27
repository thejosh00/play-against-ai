package com.pokerai.analysis

import com.pokerai.engine.HandEvaluator
import com.pokerai.engine.HandEvaluation
import com.pokerai.engine.HandRank
import com.pokerai.model.*

object HandStrengthClassifier {

    fun analyze(holeCards: HoleCards, communityCards: List<Card>): HandAnalysis {
        val allCards = holeCards.toList() + communityCards
        val evaluation = HandEvaluator.evaluateBest(allCards)
        val isRiver = communityCards.size == 5

        val baseTier = classifyMadeHand(evaluation, holeCards, communityCards)
        val draws = if (isRiver) emptyList() else DrawDetector.detectDraws(holeCards, communityCards)
        val totalOuts = calculateTotalOuts(draws)
        val finalTier = if (isRiver) baseTier else applyDrawPromotion(baseTier, totalOuts)
        val madeHand = evaluation.rank >= HandRank.ONE_PAIR &&
            !isBoardOnlyPair(evaluation, holeCards, communityCards)
        val description = buildDescription(evaluation, holeCards, communityCards, draws, totalOuts, baseTier)
        val hasNutAdvantage = checkNutAdvantage(holeCards, communityCards)

        return HandAnalysis(
            tier = finalTier,
            madeHandDescription = description,
            draws = draws,
            totalOuts = totalOuts,
            madeHand = madeHand,
            hasNutAdvantage = hasNutAdvantage
        )
    }

    private fun classifyMadeHand(
        evaluation: HandEvaluation,
        holeCards: HoleCards,
        communityCards: List<Card>
    ): HandStrengthTier {
        // Player has nothing
        if (evaluation.rank == HandRank.HIGH_CARD) return HandStrengthTier.NOTHING
        if (evaluation.rank == HandRank.ONE_PAIR && isBoardOnlyPair(evaluation, holeCards, communityCards)) {
            return HandStrengthTier.NOTHING
        }

        // 4-flush on board: anything that doesn't beat a flush is weak
        val fourFlushSuit = communityCards.groupBy { it.suit }.entries.find { it.value.size >= 4 }?.key
        if (fourFlushSuit != null && evaluation.rank < HandRank.FLUSH) {
            return HandStrengthTier.WEAK
        }

        // 4-straight on board: anything that doesn't beat a straight is weak
        if (hasFourToStraight(communityCards) && evaluation.rank < HandRank.STRAIGHT) {
            return HandStrengthTier.WEAK
        }

        return when (evaluation.rank) {
            HandRank.ROYAL_FLUSH, HandRank.STRAIGHT_FLUSH, HandRank.FOUR_OF_A_KIND,
            HandRank.FULL_HOUSE -> HandStrengthTier.MONSTER

            HandRank.FLUSH -> classifyFlush(holeCards, fourFlushSuit)

            HandRank.STRAIGHT -> HandStrengthTier.MONSTER

            HandRank.THREE_OF_A_KIND -> classifyThreeOfAKind(evaluation, holeCards, communityCards)
            HandRank.TWO_PAIR -> classifyTwoPair(evaluation, holeCards, communityCards)
            HandRank.ONE_PAIR -> classifyOnePair(evaluation, holeCards, communityCards)
            HandRank.HIGH_CARD -> HandStrengthTier.NOTHING
        }
    }

    private fun classifyFlush(
        holeCards: HoleCards,
        fourFlushSuit: Suit?
    ): HandStrengthTier {
        // No 4-flush on board — player made a flush using 2+ hole cards, always strong
        if (fourFlushSuit == null) return HandStrengthTier.MONSTER

        // 4-flush on board: strength depends on the player's highest hole card of that suit
        val holeFlushCards = holeCards.toList().filter { it.suit == fourFlushSuit }

        if (holeFlushCards.isEmpty()) {
            // Board-only flush — anyone with a single suited card beats this
            return HandStrengthTier.WEAK
        }

        val highestHoleFlush = holeFlushCards.maxOf { it.rank.value }
        return when {
            highestHoleFlush >= Rank.ACE.value -> HandStrengthTier.MONSTER   // Nut flush
            highestHoleFlush >= Rank.KING.value -> HandStrengthTier.STRONG   // Second-nut flush
            highestHoleFlush >= Rank.TEN.value -> HandStrengthTier.MEDIUM    // Decent flush
            else -> HandStrengthTier.WEAK                                     // Low flush, easily dominated
        }
    }

    private fun classifyThreeOfAKind(
        evaluation: HandEvaluation,
        holeCards: HoleCards,
        communityCards: List<Card>
    ): HandStrengthTier {
        val tripRank = evaluation.kickers[0]
        val holeRanks = holeCards.toList().map { it.rank.value }
        val boardRankCounts = communityCards.groupBy { it.rank.value }

        // If the board has 3 of the same rank, it's a board set - not the player's
        if (boardRankCounts[tripRank]?.size == 3) {
            // Player's trips are entirely on the board, classify by kicker
            return if (holeRanks.max() >= Rank.ACE.value) HandStrengthTier.MEDIUM
            else HandStrengthTier.WEAK
        }

        // Player has a set (pocket pair + one on board) or trips (one in hand + two on board)
        return HandStrengthTier.MONSTER
    }

    private fun classifyTwoPair(
        evaluation: HandEvaluation,
        holeCards: HoleCards,
        communityCards: List<Card>
    ): HandStrengthTier {
        val boardRankCounts = communityCards.groupBy { it.rank.value }
        val holeRanks = holeCards.toList().map { it.rank.value }.toSet()
        val twoPairRanks = evaluation.kickers.take(2).toSet()

        val boardPairs = boardRankCounts.filter { it.value.size >= 2 }.keys

        // If both pairs are on the board, the player doesn't really have two pair
        if (boardPairs.containsAll(twoPairRanks)) {
            return if (holeRanks.max() >= Rank.ACE.value) HandStrengthTier.MEDIUM
            else HandStrengthTier.WEAK
        }

        // If one pair is on the board, the player's real contribution is a single pair
        if (boardPairs.any { it in twoPairRanks }) {
            val playerPairRank = twoPairRanks.first { it !in boardPairs }
            if (holeCards.isPair) {
                // Pocket pair + board pair = two pair; classify by the pocket pair strength
                val boardRanks = communityCards.map { it.rank.value }.sortedDescending()
                return classifyPocketPair(playerPairRank, boardRanks)
            }
            val holeRanksList = holeCards.toList().map { it.rank.value }.sortedDescending()
            val boardRanks = communityCards.map { it.rank.value }.sortedDescending()
            return classifyBoardPair(playerPairRank, holeRanksList, boardRanks)
        }

        // Both hole cards contribute to the two pair
        return HandStrengthTier.MONSTER
    }

    private fun classifyOnePair(
        evaluation: HandEvaluation,
        holeCards: HoleCards,
        communityCards: List<Card>
    ): HandStrengthTier {
        val pairRank = evaluation.kickers[0]
        val holeRanks = holeCards.toList().map { it.rank.value }.sortedDescending()
        val boardRanks = communityCards.map { it.rank.value }.sortedDescending()

        // Check if this is a board-only pair (pair exists entirely on the board)
        if (isBoardOnlyPair(evaluation, holeCards, communityCards)) {
            return HandStrengthTier.NOTHING
        }

        // Pocket pair
        if (holeCards.isPair) {
            return classifyPocketPair(pairRank, boardRanks)
        }

        // Player pairs one of their hole cards with the board
        return classifyBoardPair(pairRank, holeRanks, boardRanks)
    }

    private fun isBoardOnlyPair(
        evaluation: HandEvaluation,
        holeCards: HoleCards,
        communityCards: List<Card>
    ): Boolean {
        if (evaluation.rank != HandRank.ONE_PAIR) return false
        val pairRank = evaluation.kickers[0]
        val holeRanks = holeCards.toList().map { it.rank.value }
        val boardRankCounts = communityCards.groupBy { it.rank.value }
        return boardRankCounts[pairRank]?.size == 2 && pairRank !in holeRanks
    }

    private fun classifyPocketPair(pairRank: Int, boardRanks: List<Int>): HandStrengthTier {
        if (boardRanks.isEmpty()) return HandStrengthTier.STRONG

        val highestBoard = boardRanks.max()
        val boardCardsAbove = boardRanks.count { it > pairRank }

        return when {
            pairRank > highestBoard -> HandStrengthTier.STRONG  // Overpair
            boardCardsAbove == 1 -> HandStrengthTier.MEDIUM     // Below one board card
            else -> HandStrengthTier.WEAK                        // Below two or more board cards
        }
    }

    private fun classifyBoardPair(pairRank: Int, holeRanks: List<Int>, boardRanks: List<Int>): HandStrengthTier {
        if (boardRanks.isEmpty()) return HandStrengthTier.MEDIUM

        val highestBoard = boardRanks.max()
        val sortedBoardUnique = boardRanks.distinct().sortedDescending()
        val kicker = holeRanks.first { it != pairRank }

        return when {
            pairRank == highestBoard -> {
                // Top pair - classify by kicker strength
                when {
                    kicker >= Rank.ACE.value -> HandStrengthTier.STRONG          // TPTK (ace kicker)
                    kicker >= Rank.KING.value -> HandStrengthTier.STRONG         // Top pair king kicker
                    else -> HandStrengthTier.MEDIUM                               // Top pair weak kicker
                }
            }
            sortedBoardUnique.size >= 2 && pairRank == sortedBoardUnique[1] -> {
                HandStrengthTier.MEDIUM  // Second pair
            }
            else -> HandStrengthTier.WEAK  // Bottom pair, third pair, etc.
        }
    }

    private fun applyDrawPromotion(baseTier: HandStrengthTier, totalOuts: Int): HandStrengthTier {
        return when {
            baseTier == HandStrengthTier.MEDIUM && totalOuts >= 9 -> HandStrengthTier.STRONG
            (baseTier == HandStrengthTier.NOTHING || baseTier == HandStrengthTier.WEAK) && totalOuts >= 12 -> HandStrengthTier.STRONG
            (baseTier == HandStrengthTier.NOTHING || baseTier == HandStrengthTier.WEAK) && totalOuts >= 8 -> HandStrengthTier.MEDIUM
            baseTier == HandStrengthTier.NOTHING && totalOuts >= 4 -> HandStrengthTier.WEAK
            else -> baseTier
        }
    }

    private fun calculateTotalOuts(draws: List<DrawInfo>): Int {
        if (draws.isEmpty()) return 0

        val majorDrawTypes = setOf(DrawType.NUT_FLUSH_DRAW, DrawType.FLUSH_DRAW, DrawType.OESD, DrawType.GUTSHOT)
        val majorDraws = draws.filter { it.type in majorDrawTypes }
        val backdoorDraws = draws.filter { it.type == DrawType.BACKDOOR_FLUSH || it.type == DrawType.BACKDOOR_STRAIGHT }
        val overcardDraw = draws.find { it.type == DrawType.OVERCARDS }

        var total = majorDraws.sumOf { it.outs }

        // Deduplication for overlapping major draws
        val hasFlushDraw = majorDraws.any { it.type == DrawType.NUT_FLUSH_DRAW || it.type == DrawType.FLUSH_DRAW }
        val hasOesd = majorDraws.any { it.type == DrawType.OESD }
        val hasGutshot = majorDraws.any { it.type == DrawType.GUTSHOT }

        if (hasFlushDraw && hasOesd) total -= 2
        if (hasFlushDraw && hasGutshot) total -= 1

        // Add backdoor outs
        total += backdoorDraws.sumOf { it.outs }

        // Add overcard outs
        if (overcardDraw != null) total += overcardDraw.outs

        return maxOf(total, 0)
    }

    private fun buildDescription(
        evaluation: HandEvaluation,
        holeCards: HoleCards,
        communityCards: List<Card>,
        draws: List<DrawInfo>,
        totalOuts: Int,
        baseTier: HandStrengthTier
    ): String {
        val madeHandDesc = buildMadeHandDescription(evaluation, holeCards, communityCards)
        val drawDesc = buildDrawDescription(draws, totalOuts)

        val hasMajorDraw = draws.any {
            it.type !in setOf(DrawType.BACKDOOR_FLUSH, DrawType.BACKDOOR_STRAIGHT)
        }

        return when {
            hasMajorDraw && baseTier >= HandStrengthTier.WEAK && evaluation.rank < HandRank.ONE_PAIR ->
                drawDesc
            drawDesc.isNotEmpty() && evaluation.rank >= HandRank.ONE_PAIR ->
                "$madeHandDesc with $drawDesc"
            drawDesc.isNotEmpty() && evaluation.rank < HandRank.ONE_PAIR && !hasMajorDraw ->
                "$madeHandDesc, $drawDesc"
            else -> madeHandDesc
        }
    }

    private fun buildMadeHandDescription(
        evaluation: HandEvaluation,
        holeCards: HoleCards,
        communityCards: List<Card>
    ): String {
        val holeRanks = holeCards.toList().map { it.rank.value }.sortedDescending()
        val boardRanks = communityCards.map { it.rank.value }.sortedDescending()

        return when (evaluation.rank) {
            HandRank.ROYAL_FLUSH -> "royal flush"
            HandRank.STRAIGHT_FLUSH -> "straight flush, ${rankName(evaluation.kickers[0])} high"
            HandRank.FOUR_OF_A_KIND -> "four of a kind, ${rankName(evaluation.kickers[0])}s"
            HandRank.FULL_HOUSE -> "full house, ${rankName(evaluation.kickers[0])}s full of ${rankName(evaluation.kickers[1])}s"
            HandRank.FLUSH -> "${rankName(evaluation.kickers[0]).lowercase()}-high flush"
            HandRank.STRAIGHT -> "straight, ${rankName(evaluation.kickers[0]).lowercase()} high"

            HandRank.THREE_OF_A_KIND -> {
                val tripRank = evaluation.kickers[0]
                if (holeCards.isPair && holeRanks[0] == tripRank) {
                    "set of ${rankName(tripRank).lowercase()}s"
                } else {
                    "three of a kind, ${rankName(tripRank).lowercase()}s"
                }
            }

            HandRank.TWO_PAIR -> {
                val high = evaluation.kickers[0]
                val low = evaluation.kickers[1]
                val boardRankCounts = communityCards.groupBy { it.rank.value }
                val boardPairsDesc = boardRankCounts.filter { it.value.size >= 2 }.keys
                val twoPairRanksDesc = setOf(high, low)

                if (boardPairsDesc.any { it in twoPairRanksDesc }
                    && !boardPairsDesc.containsAll(twoPairRanksDesc)
                ) {
                    val playerPairRank = twoPairRanksDesc.first { it !in boardPairsDesc }
                    if (holeCards.isPair) {
                        // Pocket pair + board pair
                        "two pair, pocket ${rankName(playerPairRank).lowercase()}s"
                    } else {
                        val kicker = holeRanks.first { it != playerPairRank }
                        val sortedBoardUnique = boardRanks.distinct().sortedDescending()
                        when {
                            playerPairRank == boardRanks.max() ->
                                "top pair, ${rankName(kicker).lowercase()} kicker"
                            sortedBoardUnique.size >= 2 && playerPairRank == sortedBoardUnique[1] ->
                                "second pair, ${rankName(playerPairRank).lowercase()}s"
                            else ->
                                "bottom pair, ${rankName(playerPairRank).lowercase()}s"
                        }
                    }
                } else {
                    "two pair, ${rankName(high).lowercase()}s and ${rankName(low).lowercase()}s"
                }
            }

            HandRank.ONE_PAIR -> {
                val pairRank = evaluation.kickers[0]
                if (isBoardOnlyPair(evaluation, holeCards, communityCards)) {
                    "${rankName(holeRanks.max()).lowercase()} high"
                } else if (holeCards.isPair) {
                    val highestBoard = if (boardRanks.isEmpty()) 0 else boardRanks.max()
                    when {
                        pairRank > highestBoard -> "overpair, pocket ${rankName(pairRank).lowercase()}s"
                        else -> "pocket ${rankName(pairRank).lowercase()}s (underpair)"
                    }
                } else {
                    val sortedBoard = boardRanks.distinct().sortedDescending()
                    val kicker = holeRanks.first { it != pairRank }
                    when {
                        sortedBoard.isNotEmpty() && pairRank == sortedBoard[0] ->
                            "top pair, ${rankName(kicker).lowercase()} kicker"
                        sortedBoard.size >= 2 && pairRank == sortedBoard[1] ->
                            "second pair, ${rankName(pairRank).lowercase()}s"
                        else ->
                            "bottom pair, ${rankName(pairRank).lowercase()}s"
                    }
                }
            }

            HandRank.HIGH_CARD -> {
                "${rankName(holeRanks.max()).lowercase()} high"
            }
        }
    }

    private fun buildDrawDescription(draws: List<DrawInfo>, totalOuts: Int): String {
        if (draws.isEmpty()) return ""

        val parts = mutableListOf<String>()
        for (draw in draws) {
            when (draw.type) {
                DrawType.NUT_FLUSH_DRAW -> parts.add("nut flush draw")
                DrawType.FLUSH_DRAW -> parts.add("flush draw")
                DrawType.OESD -> parts.add("open-ended straight draw")
                DrawType.GUTSHOT -> parts.add("gutshot straight draw")
                DrawType.BACKDOOR_FLUSH -> parts.add("backdoor flush draw")
                DrawType.BACKDOOR_STRAIGHT -> parts.add("backdoor straight draw")
                DrawType.OVERCARDS -> parts.add("overcards")
            }
        }

        val majorOuts = draws.filter {
            it.type !in setOf(DrawType.BACKDOOR_FLUSH, DrawType.BACKDOOR_STRAIGHT)
        }.sumOf { it.outs }

        val desc = parts.joinToString(" + ")
        return if (majorOuts > 0) "$desc ($totalOuts outs)" else desc
    }

    private fun hasFourToStraight(communityCards: List<Card>): Boolean {
        val uniqueRanks = communityCards.map { it.rank.value }.distinct().toMutableList()
        if (14 in uniqueRanks) uniqueRanks.add(1) // ace can play low
        uniqueRanks.sort()
        if (uniqueRanks.size < 4) return false
        for (i in 0..uniqueRanks.size - 4) {
            if (uniqueRanks[i + 3] - uniqueRanks[i] <= 4) return true
        }
        return false
    }

    private fun checkNutAdvantage(holeCards: HoleCards, communityCards: List<Card>): Boolean {
        if (communityCards.isEmpty()) return false

        // Check flush nut advantage
        val boardSuitCounts = communityCards.groupBy { it.suit }
        for ((suit, boardCards) in boardSuitCounts) {
            if (boardCards.size < 3) continue

            val holeCardsOfSuit = holeCards.toList().filter { it.suit == suit }
            if (holeCardsOfSuit.isEmpty()) continue

            val highestHoleOfSuit = holeCardsOfSuit.maxOf { it.rank.value }
            val highestBoardOfSuit = boardCards.maxOf { it.rank.value }

            // Has the ace of the flush suit, or highest card of that suit not on board
            if (highestHoleOfSuit == Rank.ACE.value) return true
            if (highestHoleOfSuit > highestBoardOfSuit) return true
        }

        return false
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
}
