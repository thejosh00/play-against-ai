package com.pokerai.engine

import com.pokerai.model.GameState
import com.pokerai.model.Player
import com.pokerai.model.SidePot
import kotlin.math.min
import kotlin.math.roundToInt

object PotManager {

    fun deductRake(state: GameState, rakePercent: Double, rakeCap: Int): Int {
        val rakeAmount = min((state.pot * rakePercent).roundToInt(), rakeCap)
        state.pot -= rakeAmount
        return rakeAmount
    }

    fun collectBets(state: GameState) {
        for (player in state.players) {
            state.pot += player.currentBet
            player.currentBet = 0
        }
    }

    fun calculateSidePots(state: GameState): List<SidePot> {
        val playersInHand = state.players.filter { !it.isFolded && !it.isSittingOut }
        if (playersInHand.none { it.isAllIn }) {
            return listOf(SidePot(state.pot, playersInHand.map { it.index }))
        }

        val allInAmounts = playersInHand
            .filter { it.isAllIn }
            .map { it.totalBetThisHand }
            .distinct()
            .sorted()

        val sidePots = mutableListOf<SidePot>()
        var previousLevel = 0

        for (level in allInAmounts) {
            val contribution = level - previousLevel
            val eligible = playersInHand.filter { it.totalBetThisHand >= level }
            val contributors = state.players.filter { !it.isSittingOut && it.totalBetThisHand > previousLevel }
            val potAmount = contributors.sumOf { minOf(it.totalBetThisHand - previousLevel, contribution) }

            if (potAmount > 0) {
                sidePots.add(SidePot(potAmount, eligible.map { it.index }))
            }
            previousLevel = level
        }

        val maxAllIn = allInAmounts.lastOrNull() ?: 0
        val remainingContributors = playersInHand.filter { it.totalBetThisHand > maxAllIn }
        if (remainingContributors.size > 1) {
            val remainingAmount = remainingContributors.sumOf { it.totalBetThisHand - maxAllIn }
            if (remainingAmount > 0) {
                sidePots.add(SidePot(remainingAmount, remainingContributors.map { it.index }))
            }
        } else if (remainingContributors.size == 1) {
            // Refund excess to the single remaining player
            val excess = remainingContributors[0].totalBetThisHand - maxAllIn
            if (excess > 0) {
                remainingContributors[0].chips += excess
                state.pot -= excess
            }
        }

        return sidePots
    }

    fun awardPot(
        state: GameState,
        evaluations: Map<Int, HandEvaluation>
    ): List<Triple<Int, Int, String>> {
        val winners = mutableListOf<Triple<Int, Int, String>>() // playerIndex, amount, handDescription

        val sidePots = calculateSidePots(state)

        if (sidePots.isEmpty()) {
            // Single winner (everyone else folded)
            val winner = state.activePlayers.firstOrNull()
                ?: state.players.first { !it.isFolded && !it.isSittingOut }
            winner.chips += state.pot
            winners.add(Triple(winner.index, state.pot, ""))
            state.pot = 0
            return winners
        }

        for (sidePot in sidePots) {
            val eligibleEvals = evaluations.filter { it.key in sidePot.eligiblePlayerIndices }
            if (eligibleEvals.isEmpty()) continue

            val bestEval = eligibleEvals.values.max()
            val potWinners = eligibleEvals.filter { it.value.compareTo(bestEval) == 0 }

            val share = sidePot.amount / potWinners.size
            val remainder = sidePot.amount % potWinners.size

            potWinners.entries.forEachIndexed { idx, (playerIndex, eval) ->
                val amount = share + if (idx == 0) remainder else 0
                state.players[playerIndex].chips += amount
                winners.add(Triple(playerIndex, amount, eval.description))
            }
        }

        state.pot = 0
        return winners
    }
}
