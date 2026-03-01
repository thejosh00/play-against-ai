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

        // Shove-call: if someone has already shoved preflop, decide whether to call
        val shoveRecord = state.actionHistory.lastOrNull {
            it.action.type == ActionType.ALL_IN && it.phase == GamePhase.PRE_FLOP
        }
        if (shoveRecord != null) {
            return callShoveDecision(player, state, gameContext, tournamentState, archetype, profile, opponentModeler, shoveRecord)
        }

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

    private fun callShoveDecision(
        player: Player,
        state: GameState,
        gameContext: GameContext,
        tournamentState: TournamentState?,
        archetype: PlayerArchetype,
        profile: PlayerProfile,
        opponentModeler: OpponentModeler?,
        shoveRecord: ActionRecord
    ): AiDecision {
        val holeCards = player.holeCards ?: error("AI player ${player.name} has no hole cards")
        val hand = holeCards.notation
        val shoverPlayer = state.players[shoveRecord.playerIndex]
        val shoverType = opponentModeler?.getRead(shoverPlayer)?.playerType ?: OpponentType.UNKNOWN

        val estimatedRange = estimateShoveRange(shoverPlayer, state, gameContext, shoverType)

        val callAmount = state.currentBetLevel - player.currentBet
        val allCurrentBets = state.players.sumOf { it.currentBet }
        val potOdds = callAmount.toDouble() / (state.pot + allCurrentBets + callAmount)

        val correctCallRange = (estimatedRange * potOddsToCallWidth(potOdds)).toInt()

        // Archetype distortion
        var adjustedRange = when (archetype) {
            is CallingStationArchetype -> {
                val flatRange = when {
                    potOdds <= 0.25 -> 55
                    potOdds <= 0.30 -> 45
                    potOdds <= 0.35 -> 35
                    else -> 25
                }
                val opponentAdj = if (shoverType == OpponentType.TIGHT_PASSIVE) -3 else 0
                flatRange + opponentAdj
            }
            is NitArchetype -> {
                val flatRange = when {
                    potOdds <= 0.25 -> 18
                    potOdds <= 0.30 -> 12
                    potOdds <= 0.35 -> 8
                    else -> 5
                }
                val opponentAdj = when (shoverType) {
                    OpponentType.LOOSE_AGGRESSIVE -> 4
                    OpponentType.LOOSE_PASSIVE -> 2
                    OpponentType.TIGHT_PASSIVE -> -3
                    OpponentType.TIGHT_AGGRESSIVE -> -2
                    OpponentType.UNKNOWN -> 0
                }
                flatRange + opponentAdj
            }
            is TagArchetype -> {
                val base = (correctCallRange * 0.85).toInt()
                val opponentAdj = when (shoverType) {
                    OpponentType.LOOSE_AGGRESSIVE -> 8
                    OpponentType.LOOSE_PASSIVE -> 5
                    OpponentType.TIGHT_PASSIVE -> -8
                    OpponentType.TIGHT_AGGRESSIVE -> -5
                    OpponentType.UNKNOWN -> 0
                }
                base + opponentAdj
            }
            is LagArchetype -> {
                val base = (correctCallRange * 1.2).toInt()
                val opponentAdj = when (shoverType) {
                    OpponentType.LOOSE_AGGRESSIVE -> 12
                    OpponentType.LOOSE_PASSIVE -> 6
                    OpponentType.TIGHT_PASSIVE -> -8
                    OpponentType.TIGHT_AGGRESSIVE -> -5
                    OpponentType.UNKNOWN -> 0
                }
                base + opponentAdj
            }
            is SharkArchetype -> {
                var base = correctCallRange
                val opponentAdj = when (shoverType) {
                    OpponentType.LOOSE_AGGRESSIVE -> 6
                    OpponentType.LOOSE_PASSIVE -> 3
                    OpponentType.TIGHT_PASSIVE -> -6
                    OpponentType.TIGHT_AGGRESSIVE -> -4
                    OpponentType.UNKNOWN -> 0
                }
                base += opponentAdj
                if (gameContext.isTournament && gameContext.tournamentStage != null) {
                    val pressure = icmPressure(gameContext.tournamentStage, player, state, tournamentState)
                    base = (base * (1.0 - pressure * archetype.icmAwareness() * 0.4)).toInt()
                }
                base
            }
            else -> correctCallRange
        }

        adjustedRange += positionAdjustment(player, state)
        adjustedRange = adjustedRange.coerceIn(1, HandRankings.PUSH_FOLD_RANKED_HANDS.size)

        val pushFoldIndex = HandRankings.pushFoldIndexOf(hand)
        val inRange = pushFoldIndex < adjustedRange
        val fuzzIn = !inRange && pushFoldIndex < adjustedRange + PreFlopStrategy.FUZZ_BOUND && random.nextDouble() < profile.rangeFuzzProb

        val reasoning = "$hand, shove-call, ${player.position.label}, range=$adjustedRange, potOdds=${String.format("%.0f%%", potOdds * 100)}, vs ${shoverPlayer.position.label}"

        return if (inRange || fuzzIn) {
            val action = if (callAmount >= player.chips || callAmount >= player.chips / 3) {
                Action.allIn(player.chips)
            } else {
                Action.call(callAmount)
            }
            AiDecision(action, reasoning, "coded")
        } else {
            AiDecision(Action.fold(), reasoning, "coded")
        }
    }

    private fun estimateShoveRange(
        shoverPlayer: Player,
        state: GameState,
        gameContext: GameContext,
        shoverType: OpponentType
    ): Int {
        val shoverBBs = (shoverPlayer.totalBetThisHand.toDouble() / state.bigBlind).toInt().coerceIn(1, 15)
        val baseRange = if (gameContext.antesActive) {
            PushFoldChart.getRangeWithAnte(shoverPlayer.position, shoverBBs)
        } else {
            PushFoldChart.getRange(shoverPlayer.position, shoverBBs)
        }
        val typeMultiplier = when (shoverType) {
            OpponentType.LOOSE_AGGRESSIVE -> 1.3
            OpponentType.TIGHT_AGGRESSIVE -> 0.8
            OpponentType.LOOSE_PASSIVE -> 0.7
            OpponentType.TIGHT_PASSIVE -> 0.5
            OpponentType.UNKNOWN -> 1.0
        }
        return (baseRange * typeMultiplier).toInt().coerceIn(1, 169)
    }

    private fun potOddsToCallWidth(potOdds: Double): Double = when {
        potOdds <= 0.20 -> 0.85
        potOdds <= 0.25 -> 0.75
        potOdds <= 0.30 -> 0.65
        potOdds <= 0.35 -> 0.55
        potOdds <= 0.40 -> 0.45
        else -> 0.35
    }

    private fun positionAdjustment(player: Player, state: GameState): Int {
        val playersLeftToAct = state.players.count {
            it.index != player.index && it.isActive && !it.isAllIn
        }
        return when {
            playersLeftToAct == 0 -> 5
            playersLeftToAct == 1 -> 0
            else -> -5
        }
    }

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
