package com.pokerai.ai

import com.pokerai.analysis.*
import com.pokerai.model.*

/**
 * Represents the current street for strategy purposes.
 * Separate from GamePhase because strategies only care about the four betting streets.
 */
enum class Street {
    PREFLOP, FLOP, TURN, RIVER
}

/**
 * Pre-calculated bet sizes relative to the current pot.
 */
data class BetSizes(
    val thirdPot: Int,
    val halfPot: Int,
    val twoThirdsPot: Int,
    val fullPot: Int,
    val minRaise: Int
)

/**
 * Everything a strategy needs to make a decision.
 *
 * Strategies should ONLY use this object — never reach back into GameState.
 * This decouples decision logic from engine internals and makes strategies
 * easy to test (just construct a DecisionContext and assert the output).
 */
data class DecisionContext(
    // ── Hand evaluation (from Phase 1) ──────────────────────
    val hand: HandAnalysis,

    // ── Board texture (from Phase 2) ────────────────────────
    val board: BoardAnalysis,

    // ── Action history (from Phase 3) ───────────────────────
    val actions: ActionAnalysis,

    // ── Pot geometry ────────────────────────────────────────
    val potSize: Int,
    val betToCall: Int,
    val potOdds: Double,
    val betAsFractionOfPot: Double,
    val spr: Double,
    val effectiveStack: Int,
    val suggestedSizes: BetSizes,

    // ── Situation ───────────────────────────────────────────
    val street: Street,
    val position: Position,
    val isAggressor: Boolean,
    val isInitiator: Boolean,
    val facingBet: Boolean,
    val facingRaise: Boolean,
    val numBetsThisStreet: Int,
    val potType: PotType,

    // ── Randomizer ──────────────────────────────────────────
    val instinct: Int,

    // ── Profile (archetype tuning knobs) ────────────────────
    val profile: PlayerProfile,

    // ── Session context (Phase 7) ───────────────────────────
    val sessionStats: SessionStats? = null,
    val opponents: List<OpponentRead> = emptyList(),

    // ── Opponent making the bet we're facing ────────────────
    val bettorRead: OpponentRead? = null
)
