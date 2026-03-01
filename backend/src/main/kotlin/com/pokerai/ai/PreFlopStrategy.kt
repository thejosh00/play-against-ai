package com.pokerai.ai

import com.pokerai.ai.pushfold.PushFoldChart
import com.pokerai.model.*
import com.pokerai.model.archetype.*
import kotlin.random.Random


enum class Scenario { OPEN, FACING_RAISE, FACING_3BET }

/**
 * Round a bet amount to clean chip values:
 * - Bets < 50 → round to nearest 5
 * - Bets 50-200 → round to nearest 25
 * - Bets > 200 → round to nearest 50
 */
fun Int.roundBet(): Int {
    if (this <= 0) return this
    val increment = when {
        this < 50 -> 5
        this <= 200 -> 25
        else -> 50
    }
    return ((this + increment / 2) / increment) * increment
}

class PreFlopStrategy(private val random: Random = Random) {

    companion object {
        /** Fuzz can only extend this many hands beyond the cutoff */
        const val FUZZ_BOUND = 10
    }

    fun decide(player: Player, state: GameState, tournamentState: TournamentState? = null, config: GameConfig? = null, opponentModeler: OpponentModeler? = null): AiDecision {
        val holeCards = player.holeCards ?: error("AI player ${player.name} has no hole cards")
        val profile = player.profile ?: error("AI player ${player.name} has no profile")
        val archetype = profile.archetype
        val hand = holeCards.notation
        val scenario = classifyScenario(player, state)

        val effectiveBBs = (player.chips + player.currentBet).toDouble() / state.bigBlind

        val baseCutoff = when (scenario) {
            Scenario.OPEN -> archetype.getOpenCutoff(player.position)
            Scenario.FACING_RAISE -> archetype.getFacingRaiseCutoff(player.position)
            Scenario.FACING_3BET -> archetype.getFacing3BetCutoff()
        }
        val gameContext = GameContext.from(config, state, tournamentState)
        val contextAdjustment = archetype.getGameContextAdjustment(gameContext, scenario)
        val situationalAdjustment = computeRangeAdjustment(player, state, scenario)
        val adjustment = situationalAdjustment + contextAdjustment
        val cutoff = (baseCutoff + adjustment).coerceIn(1, HandRankings.RANKED_HANDS.size)
        val isHeadsUp = state.activePlayers.size <= 2
        val handIndex = if (isHeadsUp) HandRankings.huIndexOf(hand) else HandRankings.indexOf(hand)
        val inRange = handIndex < cutoff

        fun reasoning(detail: String) = "$hand, ${scenario.name.lowercase()}, cutoff=$cutoff, ${player.position.label}, $detail"

        // Archetype-aware push/fold: shove or fold when short-stacked in unopened pots
        // BB defends rather than open-shoving
        val bbCount = effectiveBBs.toInt()
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
            if (archetype is LagArchetype) adjustedRange = (adjustedRange * 1.1).toInt()
            if (archetype is NitArchetype) adjustedRange = (adjustedRange * 0.9).toInt()

            adjustedRange = adjustedRange.coerceIn(1, HandRankings.PUSH_FOLD_RANKED_HANDS.size)
            val pushFoldIndex = HandRankings.pushFoldIndexOf(hand)
            val pfInRange = pushFoldIndex < adjustedRange

            val fuzzIn = !pfInRange && pushFoldIndex < adjustedRange + FUZZ_BOUND && random.nextDouble() < profile.rangeFuzzProb
            val action = if (pfInRange || fuzzIn) Action.allIn(player.chips) else Action.fold()
            return AiDecision(action, reasoning("push/fold ${bbCount}bb, range=$adjustedRange"), "coded")
        }

        // Short-stack fallback: BB or any position facing action at ≤10 BBs still shoves or folds
        if (bbCount <= 10) {
            val pushFoldIndex = HandRankings.pushFoldIndexOf(hand)
            val pfInRange = pushFoldIndex < cutoff
            val fuzzIn = !pfInRange && pushFoldIndex < cutoff + FUZZ_BOUND && random.nextDouble() < profile.rangeFuzzProb
            val action = if (pfInRange || fuzzIn) Action.allIn(player.chips) else Action.fold()
            return AiDecision(action, reasoning("push/fold ${bbCount}bb"), "coded")
        }

        // Range fuzz: occasionally play slightly outside range or fold inside range
        val fuzzedInRange = if (!inRange) {
            handIndex < cutoff + FUZZ_BOUND && random.nextDouble() < profile.rangeFuzzProb
        } else {
            !shouldFuzz(profile.rangeFuzzProb)
        }

        if (!fuzzedInRange) {
            return AiDecision(Action.fold(), reasoning("out of range"), "coded")
        }

        // Short-stack 10-20 BBs: 3-bets become shoves
        if (effectiveBBs <= 20 && scenario == Scenario.FACING_RAISE) {
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

        val action = when (scenario) {
            Scenario.OPEN -> decideOpen(player, state, profile, handIndex, cutoff)
            Scenario.FACING_RAISE -> decideFacingRaise(player, state, profile, handIndex, cutoff)
            Scenario.FACING_3BET -> decideFacing3Bet(player, state, profile, handIndex, cutoff)
        }
        return AiDecision(action, reasoning("rank=$handIndex"), "coded")
    }

    internal fun computeRangeAdjustment(player: Player, state: GameState, scenario: Scenario): Int {
        if (scenario == Scenario.OPEN) return 0
        return raiserArchetypeAdjustment(state) +
            raiserPositionAdjustment(state) +
            raiseSizingAdjustment(state) +
            inPositionAdjustment(player, state)
    }

    internal fun raiserArchetypeAdjustment(state: GameState): Int {
        val raiser = findLastRaiser(state) ?: return 0
        val archetype = raiser.profile?.archetype ?: return 0
        return when (archetype) {
            is NitArchetype -> -4
            is TagArchetype -> -2
            is SharkArchetype -> 0
            is LagArchetype -> +3
            is CallingStationArchetype -> +4
        }
    }

    internal fun raiserPositionAdjustment(state: GameState): Int {
        val raiser = findLastRaiser(state) ?: return 0
        return when (raiser.position) {
            Position.UTG, Position.UTG1 -> -3
            Position.LJ -> -2
            Position.MP -> -1
            Position.HJ -> 0
            Position.CO -> 0
            Position.BTN -> +2
            Position.SB -> +3
            Position.BB -> 0
        }
    }

    internal fun raiseSizingAdjustment(state: GameState): Int {
        val lastRaise = state.actionHistory
            .lastOrNull { it.action.type == ActionType.RAISE && it.phase == GamePhase.PRE_FLOP }
            ?: return 0
        val raiseInBBs = lastRaise.action.amount.toDouble() / state.bigBlind
        return when {
            raiseInBBs <= 2.0 -> +2
            raiseInBBs <= 2.5 -> +1
            raiseInBBs <= 3.5 -> 0
            raiseInBBs <= 5.0 -> -1
            raiseInBBs <= 7.0 -> -2
            else -> -3
        }
    }

    internal fun inPositionAdjustment(player: Player, state: GameState): Int {
        val raiser = findLastRaiser(state) ?: return 0
        return when {
            player.position.ordinal > raiser.position.ordinal -> +2
            player.position.ordinal < raiser.position.ordinal -> -1
            else -> 0
        }
    }

    private fun findLastRaiser(state: GameState): Player? {
        val lastRaise = state.actionHistory
            .lastOrNull { it.action.type == ActionType.RAISE && it.phase == GamePhase.PRE_FLOP }
            ?: return null
        return state.players[lastRaise.playerIndex]
    }

    private fun classifyScenario(player: Player, state: GameState): Scenario {
        val raises = state.actionHistory.count { it.action.type == ActionType.RAISE && it.phase == GamePhase.PRE_FLOP }
        return when {
            raises >= 2 -> Scenario.FACING_3BET
            raises >= 1 -> Scenario.FACING_RAISE
            else -> Scenario.OPEN
        }
    }

    /**
     * Whether the hand is in the top [prob] fraction of the continuing range.
     * Hands with a lower index are stronger, so top-of-range means handIndex
     * sits in the first [prob] portion of the 0..cutoff range.
     */
    internal fun isTopOfRange(handIndex: Int, cutoff: Int, prob: Double): Boolean {
        if (cutoff <= 0) return false
        val position = handIndex.toDouble() / cutoff
        return position < prob
    }

    private fun decideOpen(player: Player, state: GameState, profile: PlayerProfile, handIndex: Int, cutoff: Int): Action {
        val hasLimpers = state.actionHistory.any { it.action.type == ActionType.CALL && it.phase == GamePhase.PRE_FLOP }
        val isNit = profile.archetype is NitArchetype
        val isCallingStation = profile.archetype is CallingStationArchetype

        val positionBonus = when {
            (isCallingStation || isNit) && player.position == Position.BTN -> 0.20
            (isCallingStation || isNit) && player.position == Position.CO -> 0.10
            else -> 0.0
        }
        // nits hate multi-way pots
        val limperBonus = if (isNit && hasLimpers) 0.15 else 0.0
        return if (isTopOfRange(handIndex, cutoff, profile.openRaiseProb + positionBonus + limperBonus)) {
            val sizing = openRaiseSize(player, profile, state)
            if (sizing >= player.chips + player.currentBet) {
                Action.allIn(player.chips)
            } else {
                Action.raise(sizing)
            }
        } else {
            val callAmount = state.currentBetLevel - player.currentBet
            if (callAmount >= player.chips) {
                Action.allIn(player.chips)
            } else {
                Action.call(callAmount)
            }
        }
    }

    private fun decideFacingRaise(player: Player, state: GameState, profile: PlayerProfile, handIndex: Int, cutoff: Int): Action {
        return if (isTopOfRange(handIndex, cutoff, profile.threeBetProb)) {
            val raiseSize = threeBetSize(profile, state)
            if (raiseSize >= player.chips + player.currentBet) {
                Action.allIn(player.chips)
            } else {
                Action.raise(raiseSize)
            }
        } else {
            val callAmount = state.currentBetLevel - player.currentBet
            if (callAmount >= player.chips) {
                Action.allIn(player.chips)
            } else {
                Action.call(callAmount)
            }
        }
    }

    private fun decideFacing3Bet(player: Player, state: GameState, profile: PlayerProfile, handIndex: Int, cutoff: Int): Action {
        return if (isTopOfRange(handIndex, cutoff, profile.fourBetProb)) {
            val raiseSize = fourBetSize(profile, state)
            if (raiseSize >= player.chips + player.currentBet) {
                Action.allIn(player.chips)
            } else {
                Action.raise(raiseSize)
            }
        } else {
            val callAmount = state.currentBetLevel - player.currentBet
            if (callAmount >= player.chips) {
                Action.allIn(player.chips)
            } else {
                Action.call(callAmount)
            }
        }
    }

    private fun openRaiseSize(player: Player, profile: PlayerProfile, state: GameState): Int {
        val bb = state.bigBlind
        val multiplier = randomBetween(profile.openRaiseSizeMin, profile.openRaiseSizeMax)
        val posAdj = positionSizeAdjustment(player.position)
        val effectiveBBs = (player.chips + player.currentBet).toDouble() / state.bigBlind
        // Short-stack 10-20 BBs: use smaller open sizing
        val shortStackAdj = if (effectiveBBs <= 20) {
            randomBetween(2.0, 2.3) / multiplier
        } else 1.0
        return (bb * multiplier * posAdj * shortStackAdj).toInt().roundBet()
    }

    private fun threeBetSize(profile: PlayerProfile, state: GameState): Int {
        val currentBet = state.currentBetLevel
        val multiplier = randomBetween(profile.threeBetSizeMin, profile.threeBetSizeMax)
        return (currentBet * multiplier).toInt().roundBet()
    }

    private fun fourBetSize(profile: PlayerProfile, state: GameState): Int {
        val multiplier = randomBetween(profile.fourBetSizeMin, profile.fourBetSizeMax)
        return (state.currentBetLevel * multiplier).toInt().roundBet()
    }

    private fun positionSizeAdjustment(position: Position): Double = when (position) {
        Position.UTG, Position.UTG1 -> 1.10
        Position.LJ -> 1.08
        Position.MP -> 1.05
        Position.HJ -> 1.02
        Position.CO -> 1.00
        Position.BTN -> 0.95
        Position.SB -> 1.05
        Position.BB -> 1.00
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

    private fun shouldFuzz(probability: Double): Boolean = random.nextDouble() < probability

    private fun randomBetween(min: Double, max: Double): Double =
        min + random.nextDouble() * (max - min)
}
