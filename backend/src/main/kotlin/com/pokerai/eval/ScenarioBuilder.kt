package com.pokerai.eval

import com.pokerai.ai.*
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.*

/**
 * Convenience builder for creating EvalScenarios without manually constructing
 * every field of DecisionContext.
 *
 * Usage:
 *   ScenarioBuilder.create("easy_fold") {
 *       name = "Fold trash to a bet"
 *       difficulty = EvalDifficulty.EASY
 *       category = ScenarioCategory.STRATEGIC_CORRECTNESS
 *
 *       hand(HandStrengthTier.NOTHING, "seven high, no draw")
 *       board(Street.RIVER, BoardWetness.DRY)
 *       pot(size = 100, betToCall = 100, position = Position.CO)
 *       headsUp()
 *
 *       correct(ActionType.FOLD, weight = 1.0)
 *       wrong(ActionType.CALL, ActionType.RAISE)
 *   }
 */
object ScenarioBuilder {

    fun create(id: String, block: ScenarioDsl.() -> Unit): EvalScenario {
        val dsl = ScenarioDsl(id)
        dsl.block()
        return dsl.build()
    }

    class ScenarioDsl(private val id: String) {
        var name: String = ""
        var description: String = ""
        var difficulty: EvalDifficulty = EvalDifficulty.MEDIUM
        var category: ScenarioCategory = ScenarioCategory.STRATEGIC_CORRECTNESS
        var tags: Set<String> = emptySet()

        // Hand
        private var tier: HandStrengthTier = HandStrengthTier.NOTHING
        private var handDescription: String = "no pair, no draw"
        private var draws: List<DrawInfo> = emptyList()
        private var totalOuts: Int = 0
        private var madeHand: Boolean = false
        private var hasNutAdvantage: Boolean = false

        // Board
        private var street: Street = Street.FLOP
        private var wetness: BoardWetness = BoardWetness.DRY
        private var boardDescription: String = "dry, rainbow"
        private var paired: Boolean = false
        private var monotone: Boolean = false
        private var flushPossible: Boolean = false
        private var straightPossible: Boolean = false
        private var flushCompletedThisStreet: Boolean = false
        private var straightCompletedThisStreet: Boolean = false

        // Pot geometry
        private var potSize: Int = 100
        private var betToCall: Int = 0
        private var effectiveStack: Int = 1000
        private var position: Position = Position.BTN

        // Situation
        private var isInitiator: Boolean = false
        private var potType: PotType = PotType.HEADS_UP
        private var numBetsThisStreet: Int = 0
        private var instinct: Int = 50

        // Archetype (default to Nit for correctness scenarios)
        private var archetype: PlayerArchetype = NitArchetype

        // Expected outcomes
        private val correctActions = mutableListOf<WeightedAction>()
        private val wrongActions = mutableSetOf<ActionType>()
        private val expectedKeywords = mutableSetOf<String>()
        private var distributions: MutableMap<String, ActionDistribution>? = null

        // ── DSL Methods ──

        fun hand(tier: HandStrengthTier, description: String, madeHand: Boolean = tier <= HandStrengthTier.WEAK) {
            this.tier = tier
            this.handDescription = description
            this.madeHand = madeHand
        }

        fun draws(vararg drawInfos: DrawInfo) {
            this.draws = drawInfos.toList()
            this.totalOuts = drawInfos.sumOf { it.outs }
        }

        fun board(street: Street, wetness: BoardWetness, description: String = "${wetness.name.lowercase()}, rainbow") {
            this.street = street
            this.wetness = wetness
            this.boardDescription = description
        }

        fun boardFlags(
            paired: Boolean = false,
            monotone: Boolean = false,
            flushPossible: Boolean = false,
            straightPossible: Boolean = false,
            flushCompletedThisStreet: Boolean = false,
            straightCompletedThisStreet: Boolean = false
        ) {
            this.paired = paired
            this.monotone = monotone
            this.flushPossible = flushPossible
            this.straightPossible = straightPossible
            this.flushCompletedThisStreet = flushCompletedThisStreet
            this.straightCompletedThisStreet = straightCompletedThisStreet
        }

        fun pot(
            size: Int,
            betToCall: Int = 0,
            effectiveStack: Int = 1000,
            position: Position = Position.BTN
        ) {
            this.potSize = size
            this.betToCall = betToCall
            this.effectiveStack = effectiveStack
            this.position = position
        }

        fun situation(
            isInitiator: Boolean = false,
            numBetsThisStreet: Int = 0,
            instinct: Int = 50
        ) {
            this.isInitiator = isInitiator
            this.numBetsThisStreet = numBetsThisStreet
            this.instinct = instinct
        }

        fun headsUp() { potType = PotType.HEADS_UP }
        fun threeWay() { potType = PotType.THREE_WAY }
        fun multiway() { potType = PotType.MULTIWAY }

        fun archetype(archetype: PlayerArchetype) { this.archetype = archetype }

        fun correct(actionType: ActionType, weight: Double = 1.0, minAmount: Int? = null, maxAmount: Int? = null) {
            correctActions.add(WeightedAction(actionType, weight, minAmount, maxAmount))
        }

        fun wrong(vararg actions: ActionType) {
            wrongActions.addAll(actions)
        }

        fun keywords(vararg words: String) {
            expectedKeywords.addAll(words)
        }

        fun distribution(archetypeName: String, dist: ActionDistribution) {
            if (distributions == null) distributions = mutableMapOf()
            distributions!![archetypeName] = dist
        }

        // ── Build ──

        fun build(): EvalScenario {
            val profile = archetype.createProfile()

            val spr = if (potSize > 0) effectiveStack.toDouble() / potSize else 99.0
            val potOdds = if (betToCall > 0) betToCall.toDouble() / (potSize + betToCall) else 0.0
            val betFraction = if (potSize > 0 && betToCall > 0) betToCall.toDouble() / potSize else 0.0

            val numPlayersInPot = when (potType) {
                PotType.HEADS_UP -> 2
                PotType.THREE_WAY -> 3
                PotType.MULTIWAY -> 5
            }

            val context = DecisionContext(
                hand = HandAnalysis(
                    tier = tier,
                    madeHandDescription = handDescription,
                    draws = draws,
                    totalOuts = totalOuts,
                    madeHand = madeHand,
                    hasNutAdvantage = hasNutAdvantage
                ),
                board = BoardAnalysis(
                    wetness = wetness,
                    monotone = monotone,
                    twoTone = !monotone && flushPossible,
                    rainbow = !monotone && !flushPossible,
                    flushPossible = flushPossible,
                    flushDrawPossible = flushPossible && !flushCompletedThisStreet,
                    dominantSuit = null,
                    paired = paired,
                    doublePaired = false,
                    trips = false,
                    connected = straightPossible,
                    highlyConnected = false,
                    highCard = Rank.ACE,
                    lowCard = Rank.TWO,
                    straightPossible = straightPossible,
                    straightDrawHeavy = false,
                    flushCompletedThisStreet = flushCompletedThisStreet,
                    straightCompletedThisStreet = straightCompletedThisStreet,
                    boardPairedThisStreet = false,
                    description = boardDescription
                ),
                actions = ActionAnalysis(
                    preflopActions = emptyList(),
                    flopActions = emptyList(),
                    turnActions = emptyList(),
                    riverActions = emptyList(),
                    preflopAggressor = if (isInitiator) 0 else 1,
                    flopAggressor = null,
                    turnAggressor = null,
                    riverAggressor = null,
                    currentStreetAggressor = if (betToCall > 0) 1 else if (isInitiator) 0 else null,
                    initiativeHolder = if (isInitiator) 0 else 1,
                    potType = potType,
                    numPlayersInPot = numPlayersInPot,
                    numBetsCurrentStreet = numBetsThisStreet,
                    preflopNarrative = "UTG raises to 3BB, you call",
                    flopNarrative = if (street >= Street.FLOP) "Opponent bets" else "",
                    turnNarrative = if (street >= Street.TURN) "Opponent bets" else "",
                    riverNarrative = if (street >= Street.RIVER) "Opponent bets" else "",
                    currentStreetNarrative = if (betToCall > 0) "Opponent bets $betToCall" else "Checked to you"
                ),
                potSize = potSize,
                betToCall = betToCall,
                potOdds = potOdds,
                betAsFractionOfPot = betFraction,
                spr = spr,
                effectiveStack = effectiveStack,
                suggestedSizes = BetSizes(
                    thirdPot = potSize / 3,
                    halfPot = potSize / 2,
                    twoThirdsPot = potSize * 2 / 3,
                    fullPot = potSize,
                    minRaise = if (betToCall > 0) betToCall * 2 else potSize / 3
                ),
                street = street,
                position = position,
                isAggressor = isInitiator && betToCall == 0,
                isInitiator = isInitiator,
                facingBet = betToCall > 0,
                facingRaise = betToCall > 0 && numBetsThisStreet > 1,
                numBetsThisStreet = numBetsThisStreet,
                potType = potType,
                instinct = instinct,
                profile = profile
            )

            return EvalScenario(
                id = id,
                name = name.ifEmpty { id },
                description = description,
                category = category,
                difficulty = difficulty,
                tags = tags,
                context = context,
                correctActions = correctActions.toList(),
                wrongActions = wrongActions.toSet(),
                expectedReasoningKeywords = expectedKeywords.toSet(),
                archetypeDistributions = distributions
            )
        }
    }
}
