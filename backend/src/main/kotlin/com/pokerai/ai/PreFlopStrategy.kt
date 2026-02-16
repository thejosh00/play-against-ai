package com.pokerai.ai

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

class PreFlopStrategy {

    fun decide(player: Player, state: GameState): Action {
        val holeCards = player.holeCards ?: error("AI player ${player.name} has no hole cards")
        val profile = player.profile ?: error("AI player ${player.name} has no profile")
        val archetype = profile.archetype
        val hand = holeCards.notation
        val scenario = classifyScenario(player, state)

        val effectiveBBs = (player.chips + player.currentBet).toDouble() / state.bigBlind

        val baseRange = when (scenario) {
            Scenario.OPEN -> archetype.getOpenRange(player.position)
            Scenario.FACING_RAISE -> archetype.getFacingRaiseRange(player.position)
            Scenario.FACING_3BET -> archetype.getFacing3BetRange()
        }
        val adjustment = computeRangeAdjustment(player, state, scenario)
        val range = if (adjustment != 0) {
            HandRankings.adjustRange(baseRange, adjustment)
        } else {
            baseRange
        }

        // Short-stack push/fold: ≤10 BBs means shove or fold
        if (effectiveBBs <= 10) {
            val inRange = hand in range || Random.nextDouble() < profile.rangeFuzzProb
            return if (inRange) Action.allIn(player.chips) else Action.fold()
        }

        val inRange = hand in range

        // Range fuzz: occasionally play outside range or fold inside range
        val fuzzedInRange = if (!inRange) {
            Random.nextDouble() < profile.rangeFuzzProb
        } else {
            !shouldFuzz(profile.rangeFuzzProb)
        }

        if (!fuzzedInRange) {
            return Action.fold()
        }

        // Short-stack 10-20 BBs: 3-bets become shoves
        if (effectiveBBs <= 20 && scenario == Scenario.FACING_RAISE) {
            return if (Random.nextDouble() < profile.threeBetProb) {
                Action.allIn(player.chips)
            } else {
                val callAmount = state.currentBetLevel - player.currentBet
                if (callAmount >= player.chips) Action.allIn(player.chips)
                else Action.call(callAmount)
            }
        }

        return when (scenario) {
            Scenario.OPEN -> decideOpen(player, state, profile)
            Scenario.FACING_RAISE -> decideFacingRaise(player, state, profile)
            Scenario.FACING_3BET -> decideFacing3Bet(player, state, profile)
        }
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

    private fun decideOpen(player: Player, state: GameState, profile: PlayerProfile): Action {
        return if (Random.nextDouble() < profile.openRaiseProb) {
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

    private fun decideFacingRaise(player: Player, state: GameState, profile: PlayerProfile): Action {
        return if (Random.nextDouble() < profile.threeBetProb) {
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

    private fun decideFacing3Bet(player: Player, state: GameState, profile: PlayerProfile): Action {
        return if (Random.nextDouble() < profile.fourBetProb) {
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

    private fun shouldFuzz(probability: Double): Boolean = Random.nextDouble() < probability

    private fun randomBetween(min: Double, max: Double): Double =
        min + Random.nextDouble() * (max - min)
}
