package com.pokerai.ai

import com.pokerai.analysis.*
import com.pokerai.model.*
import kotlin.random.Random

/**
 * Builds a DecisionContext from a GameState and a Player.
 *
 * This is the single assembly point that calls all Phase 1-3 analyzers
 * and packages their results into a DecisionContext. The rest of the
 * decision system only sees the DecisionContext.
 */
object DecisionContextBuilder {

    /**
     * Build a DecisionContext for the given player in the given game state.
     *
     * @param player the AI player who needs to make a decision
     * @param state the current game state
     * @param instinctOverride optional instinct value (for testing). If null, a random 1-100 is generated.
     * @return a fully populated DecisionContext
     * @throws IllegalStateException if the player has no hole cards or no profile
     */
    fun build(
        player: Player,
        state: GameState,
        instinctOverride: Int? = null,
        sessionTracker: SessionTracker? = null,
        opponentModeler: OpponentModeler? = null
    ): DecisionContext {
        val holeCards = player.holeCards
            ?: error("Player ${player.name} has no hole cards")
        val profile = player.profile
            ?: error("Player ${player.name} has no profile")

        // Phase 1: Hand analysis (preflop has no community cards to evaluate)
        val hand = if (state.communityCards.size >= 3) {
            HandStrengthClassifier.analyze(holeCards, state.communityCards)
        } else {
            preflopHandAnalysis()
        }

        // Phase 2: Board analysis (requires 3-5 community cards)
        val board = if (state.communityCards.size >= 3) {
            val previousCommunityCount = when (state.phase) {
                GamePhase.FLOP -> 0
                GamePhase.TURN -> 3
                GamePhase.RIVER -> 4
                else -> 0
            }
            BoardAnalyzer.analyze(state.communityCards, previousCommunityCount)
        } else {
            preflopBoardAnalysis()
        }

        // Phase 3: Action analysis
        val actions = ActionAnalyzer.analyze(state, player.index)

        // Pot geometry
        val betToCall = maxOf(state.currentBetLevel - player.currentBet, 0)
        val opponents = state.players.filter {
            it.index != player.index && !it.isFolded && !it.isSittingOut
        }
        val shortestOpponentStack = opponents.minOfOrNull { it.chips } ?: player.chips
        val effectiveStack = minOf(player.chips, shortestOpponentStack)

        // Phase 7: Session and opponent context
        val sessionStats = sessionTracker?.getStats(player.index, player.chips)
        val opponentReads = opponentModeler?.getOpponentReads(state.players, player.index) ?: emptyList()

        val facingBet = betToCall > 0
        val bettorIndex = if (facingBet) {
            actions.currentStreetAggressor
        } else null
        val bettorRead = bettorIndex?.let { idx ->
            opponentReads.firstOrNull { it.playerIndex == idx }
        }

        return DecisionContext(
            hand = hand,
            board = board,
            actions = actions,
            potSize = state.pot,
            betToCall = betToCall,
            potOdds = if (betToCall > 0) betToCall.toDouble() / (state.pot + betToCall) else 0.0,
            betAsFractionOfPot = if (betToCall > 0 && state.pot > 0) betToCall.toDouble() / state.pot else 0.0,
            spr = if (state.pot > 0) effectiveStack.toDouble() / state.pot else 99.0,
            effectiveStack = effectiveStack,
            suggestedSizes = BetSizes(
                thirdPot = maxOf(state.pot / 3, 1),
                halfPot = maxOf(state.pot / 2, 1),
                twoThirdsPot = maxOf(state.pot * 2 / 3, 1),
                fullPot = maxOf(state.pot, 1),
                minRaise = state.currentBetLevel + state.minRaise
            ),
            street = state.phase.toStreet(),
            position = player.position,
            isAggressor = actions.currentStreetAggressor == player.index,
            isInitiator = actions.initiativeHolder == player.index,
            facingBet = facingBet,
            facingRaise = determineFacingRaise(actions, player.index, state.phase),
            numBetsThisStreet = actions.numBetsCurrentStreet,
            potType = actions.potType,
            instinct = instinctOverride ?: Random.nextInt(1, 101),
            profile = profile,
            sessionStats = sessionStats,
            opponents = opponentReads,
            bettorRead = bettorRead
        )
    }

    private fun GamePhase.toStreet(): Street = when (this) {
        GamePhase.PRE_FLOP -> Street.PREFLOP
        GamePhase.FLOP -> Street.FLOP
        GamePhase.TURN -> Street.TURN
        GamePhase.RIVER -> Street.RIVER
        else -> error("Cannot make decisions in phase: $this")
    }

    private fun preflopHandAnalysis() = HandAnalysis(
        tier = HandStrengthTier.NOTHING,
        madeHandDescription = "preflop",
        draws = emptyList(),
        totalOuts = 0,
        madeHand = false,
        hasNutAdvantage = false
    )

    private fun preflopBoardAnalysis() = BoardAnalysis(
        wetness = BoardWetness.DRY,
        monotone = false,
        twoTone = false,
        rainbow = false,
        flushPossible = false,
        flushDrawPossible = false,
        dominantSuit = null,
        paired = false,
        doublePaired = false,
        trips = false,
        connected = false,
        highlyConnected = false,
        highCard = Rank.TWO,
        lowCard = Rank.TWO,
        straightPossible = false,
        straightDrawHeavy = false,
        flushCompletedThisStreet = false,
        straightCompletedThisStreet = false,
        boardPairedThisStreet = false,
        description = "preflop"
    )

    private fun determineFacingRaise(
        actions: ActionAnalysis,
        playerIndex: Int,
        currentPhase: GamePhase
    ): Boolean {
        val currentStreetActions = when (currentPhase) {
            GamePhase.PRE_FLOP -> actions.preflopActions
            GamePhase.FLOP -> actions.flopActions
            GamePhase.TURN -> actions.turnActions
            GamePhase.RIVER -> actions.riverActions
            else -> emptyList()
        }

        if (currentStreetActions.isEmpty()) return false

        // Find if WE made an aggressive action on this street
        val ourLastAggressiveAction = currentStreetActions
            .lastOrNull { it.playerIndex == playerIndex && it.isAggressive }
            ?: return false

        // We DID bet/raise. Is there a subsequent aggressive action by someone else?
        val ourActionIndex = currentStreetActions.indexOf(ourLastAggressiveAction)
        return currentStreetActions
            .drop(ourActionIndex + 1)
            .any { it.isAggressive && it.playerIndex != playerIndex }
    }
}
