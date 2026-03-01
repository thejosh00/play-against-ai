package com.pokerai.ai.pushfold

import com.pokerai.ai.*
import com.pokerai.model.*
import com.pokerai.model.archetype.*
import kotlin.random.Random

class PushFoldStrategy(private val random: Random = Random) {

    /**
     * Returns an [AiDecision] when push/fold logic applies (short-stacked play),
     * or `null` to fall through to standard preflop logic.
     */
    fun decide(
        player: Player,
        state: GameState,
        gameContext: GameContext,
        tournamentState: TournamentState?,
        archetype: PlayerArchetype,
        profile: PlayerProfile,
        opponentModeler: OpponentModeler?,
        cutoff: Int,
        scenario: Scenario
    ): AiDecision? {
        val holeCards = player.holeCards ?: error("AI player ${player.name} has no hole cards")
        val hand = holeCards.notation
        val effectiveBBs = (player.chips + player.currentBet).toDouble() / state.bigBlind
        val bbCount = effectiveBBs.toInt()

        fun reasoning(detail: String) = "$hand, ${scenario.name.lowercase()}, cutoff=$cutoff, ${player.position.label}, $detail"

        // Archetype-aware push/fold: shove or fold when short-stacked in unopened pots
        // BB defends rather than open-shoving
        if (bbCount <= archetype.shoveThreshold() && scenario == Scenario.OPEN && player.position != Position.BB) {
            val optimalRange = if (gameContext.antesActive)
                PushFoldChart.getRangeWithAnte(player.position, bbCount.coerceIn(1, 15))
            else
                PushFoldChart.getRange(player.position, bbCount.coerceIn(1, 15))
            var adjustedRange = (optimalRange * archetype.shoveRangeWidth()).toInt()

            // ICM tightening (tournaments only)
            if (gameContext.isTournament && gameContext.tournamentStage != null) {
                val pressure = icmPressure(gameContext.tournamentStage, player, state, tournamentState)
                adjustedRange = (adjustedRange * (1.0 - pressure * archetype.icmAwareness() * 0.4)).toInt()
            }

            // Shark reads the BB via observed opponent type
            if (archetype is SharkArchetype && opponentModeler != null) {
                val bbPlayer = state.players.firstOrNull { it.position == Position.BB && !it.isFolded }
                val bbRead = bbPlayer?.let { opponentModeler.getRead(it) }
                val blindsAdj = when (bbRead?.playerType) {
                    OpponentType.LOOSE_PASSIVE -> -0.15
                    OpponentType.TIGHT_PASSIVE -> +0.15
                    OpponentType.TIGHT_AGGRESSIVE -> +0.05
                    else -> 0.0
                }
                adjustedRange = (adjustedRange * (1.0 + blindsAdj)).toInt()
            }
            // Limper adjustment: dead money widens range, but callers tighten it
            val limpers = findLimpers(state)
            if (limpers.isNotEmpty()) {
                val limperAdj = limperShoveAdjustment(limpers, bbCount, opponentModeler)
                adjustedRange += (limperAdj * archetype.limperAwareness()).toInt()
            }

            if (archetype is LagArchetype) adjustedRange = (adjustedRange * 1.1).toInt()
            if (archetype is NitArchetype) adjustedRange = (adjustedRange * 0.9).toInt()

            adjustedRange = adjustedRange.coerceIn(1, HandRankings.PUSH_FOLD_RANKED_HANDS.size)
            val pushFoldIndex = HandRankings.pushFoldIndexOf(hand)
            val pfInRange = pushFoldIndex < adjustedRange

            val fuzzIn = !pfInRange && pushFoldIndex < adjustedRange + PreFlopStrategy.FUZZ_BOUND && random.nextDouble() < profile.rangeFuzzProb
            val action = if (pfInRange || fuzzIn) Action.allIn(player.chips) else Action.fold()
            return AiDecision(action, reasoning("push/fold ${bbCount}bb, range=$adjustedRange"), "coded")
        }

        // Short-stack fallback: BB or any position facing action at ≤10 BBs still shoves or folds
        if (bbCount <= 10) {
            val pushFoldIndex = HandRankings.pushFoldIndexOf(hand)
            val pfInRange = pushFoldIndex < cutoff
            val fuzzIn = !pfInRange && pushFoldIndex < cutoff + PreFlopStrategy.FUZZ_BOUND && random.nextDouble() < profile.rangeFuzzProb
            val action = if (pfInRange || fuzzIn) Action.allIn(player.chips) else Action.fold()
            return AiDecision(action, reasoning("push/fold ${bbCount}bb"), "coded")
        }

        // Short-stack 10-20 BBs: 3-bets become shoves
        // Includes range/fuzz check since this runs before PreFlopStrategy's standard fuzz
        if (effectiveBBs <= 20 && scenario == Scenario.FACING_RAISE) {
            val isHeadsUp = state.activePlayers.size <= 2
            val handIndex = if (isHeadsUp) HandRankings.huIndexOf(hand) else HandRankings.indexOf(hand)
            val inRange = handIndex < cutoff
            val fuzzedInRange = if (!inRange) {
                handIndex < cutoff + PreFlopStrategy.FUZZ_BOUND && random.nextDouble() < profile.rangeFuzzProb
            } else {
                random.nextDouble() >= profile.rangeFuzzProb
            }
            if (!fuzzedInRange) {
                return AiDecision(Action.fold(), reasoning("out of range"), "coded")
            }

            val pushFoldIndex = HandRankings.pushFoldIndexOf(hand)
            val action = if (isTopOfRange(pushFoldIndex, cutoff, profile.threeBetProb)) {
                Action.allIn(player.chips)
            } else {
                val callAmount = state.currentBetLevel - player.currentBet
                if (callAmount >= player.chips) Action.allIn(player.chips)
                else Action.call(callAmount)
            }
            return AiDecision(action, reasoning("short-stack ${effectiveBBs.toInt()}bb"), "coded")
        }

        return null
    }

    private fun isTopOfRange(handIndex: Int, cutoff: Int, prob: Double): Boolean {
        if (cutoff <= 0) return false
        val position = handIndex.toDouble() / cutoff
        return position < prob
    }

    private fun findLimpers(state: GameState): List<Player> {
        val limperIndices = state.actionHistory
            .filter { it.action.type == ActionType.CALL && it.phase == GamePhase.PRE_FLOP }
            .map { it.playerIndex }
            .toSet()
        return state.players.filter { it.index in limperIndices && !it.isFolded }
    }

    private fun limperShoveAdjustment(
        limpers: List<Player>,
        bbCount: Int,
        opponentModeler: OpponentModeler?
    ): Int {
        // Dead money: each limper added ~1 BB, worth more at shorter stacks
        val deadMoneyPerLimper = when {
            bbCount <= 6 -> 5
            bbCount <= 10 -> 3
            bbCount <= 15 -> 2
            else -> 1
        }
        var adjustment = deadMoneyPerLimper * limpers.size

        // Call danger: limpers who'll call a shove tighten the range
        for (limper in limpers) {
            val callDanger = if (opponentModeler != null) {
                val read = opponentModeler.getRead(limper)
                when (read.playerType) {
                    OpponentType.LOOSE_PASSIVE -> -8
                    OpponentType.LOOSE_AGGRESSIVE -> -5
                    OpponentType.TIGHT_PASSIVE -> -1
                    OpponentType.TIGHT_AGGRESSIVE -> -3
                    OpponentType.UNKNOWN -> -4
                }
            } else {
                -4
            }
            adjustment += callDanger
        }

        return adjustment.coerceAtLeast(-adjustmentFloor(limpers.size))
    }

    private fun adjustmentFloor(limperCount: Int): Int = limperCount * 8

    private fun icmPressure(
        stage: TournamentStage,
        player: Player,
        state: GameState,
        tournamentState: TournamentState?
    ): Double {
        val bubbleFactor = when (stage) {
            TournamentStage.HEADS_UP -> 0.1
            TournamentStage.FINAL_TABLE -> 0.3
            TournamentStage.BUBBLE -> 1.0
            TournamentStage.MIDDLE -> 0.4
            TournamentStage.EARLY -> 0.1
        }
        val avgStack = if (tournamentState != null && tournamentState.remainingPlayers > 0) {
            (tournamentState.totalPlayers * state.bigBlind * 100.0) / tournamentState.remainingPlayers
        } else player.chips.toDouble()
        val stackRatio = player.chips.toDouble() / avgStack
        val stackFactor = when {
            stackRatio > 2.0 -> 0.5
            stackRatio > 1.0 -> 0.8
            stackRatio > 0.5 -> 1.0
            stackRatio > 0.25 -> 0.7
            else -> 0.3
        }
        return (bubbleFactor * stackFactor).coerceIn(0.0, 1.0)
    }
}
