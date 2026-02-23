package com.pokerai.ai.strategy

import com.pokerai.ai.BetSizes
import com.pokerai.ai.DecisionContext
import com.pokerai.ai.Street
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.NitArchetype
import kotlin.test.*

class NitStrategyTest {

    private val strategy = NitStrategy()

    // ── Test helper ─────────────────────────────────────────────────

    private val defaultProfile = PlayerProfile(
        archetype = NitArchetype,
        openRaiseProb = 0.40,
        threeBetProb = 0.15,
        fourBetProb = 0.10,
        rangeFuzzProb = 0.02,
        openRaiseSizeMin = 2.8,
        openRaiseSizeMax = 3.2,
        threeBetSizeMin = 2.8,
        threeBetSizeMax = 3.2,
        fourBetSizeMin = 2.2,
        fourBetSizeMax = 2.4,
        postFlopFoldProb = 0.50,
        postFlopCallCeiling = 0.85,
        postFlopCheckProb = 0.70,
        betSizePotFraction = 0.50,
        raiseMultiplier = 2.2
    )

    private val defaultActions = ActionAnalysis(
        preflopActions = emptyList(),
        flopActions = emptyList(),
        turnActions = emptyList(),
        riverActions = emptyList(),
        preflopAggressor = null,
        flopAggressor = null,
        turnAggressor = null,
        riverAggressor = null,
        currentStreetAggressor = null,
        initiativeHolder = null,
        potType = PotType.HEADS_UP,
        numPlayersInPot = 2,
        numBetsCurrentStreet = 0,
        preflopNarrative = "",
        flopNarrative = "",
        turnNarrative = "",
        riverNarrative = "",
        currentStreetNarrative = ""
    )

    private fun ctx(
        tier: HandStrengthTier = HandStrengthTier.MEDIUM,
        madeHand: Boolean = true,
        totalOuts: Int = 0,
        hasNutAdvantage: Boolean = false,
        draws: List<DrawInfo> = emptyList(),
        wetness: BoardWetness = BoardWetness.DRY,
        flushCompletedThisStreet: Boolean = false,
        straightCompletedThisStreet: Boolean = false,
        boardPairedThisStreet: Boolean = false,
        potSize: Int = 100,
        betToCall: Int = 0,
        spr: Double = 10.0,
        effectiveStack: Int = 1000,
        street: Street = Street.FLOP,
        position: Position = Position.CO,
        isAggressor: Boolean = false,
        isInitiator: Boolean = true,
        facingBet: Boolean = false,
        facingRaise: Boolean = false,
        numBetsThisStreet: Int = 0,
        potType: PotType = PotType.HEADS_UP,
        instinct: Int = 50,
        postFlopFoldProb: Double = 0.50,
        postFlopCallCeiling: Double = 0.85,
        postFlopCheckProb: Double = 0.70,
        betSizePotFraction: Double = 0.50,
        raiseMultiplier: Double = 2.2
    ): DecisionContext {
        val hand = HandAnalysis(
            tier = tier,
            madeHandDescription = "test hand",
            draws = draws,
            totalOuts = totalOuts,
            madeHand = madeHand,
            hasNutAdvantage = hasNutAdvantage
        )
        val board = BoardAnalysis(
            wetness = wetness,
            monotone = false,
            twoTone = wetness >= BoardWetness.WET,
            rainbow = wetness <= BoardWetness.SEMI_WET,
            flushPossible = false,
            flushDrawPossible = wetness >= BoardWetness.WET,
            dominantSuit = null,
            paired = boardPairedThisStreet,
            doublePaired = false,
            trips = false,
            connected = wetness >= BoardWetness.WET,
            highlyConnected = false,
            highCard = Rank.KING,
            lowCard = Rank.TWO,
            straightPossible = false,
            straightDrawHeavy = false,
            flushCompletedThisStreet = flushCompletedThisStreet,
            straightCompletedThisStreet = straightCompletedThisStreet,
            boardPairedThisStreet = boardPairedThisStreet,
            description = "test board"
        )
        val potOdds = if (betToCall > 0) betToCall.toDouble() / (potSize + betToCall) else 0.0
        val betAsFractionOfPot = if (betToCall > 0 && potSize > 0) betToCall.toDouble() / potSize else 0.0
        val profile = defaultProfile.copy(
            postFlopFoldProb = postFlopFoldProb,
            postFlopCallCeiling = postFlopCallCeiling,
            postFlopCheckProb = postFlopCheckProb,
            betSizePotFraction = betSizePotFraction,
            raiseMultiplier = raiseMultiplier
        )

        return DecisionContext(
            hand = hand,
            board = board,
            actions = defaultActions.copy(potType = potType),
            potSize = potSize,
            betToCall = betToCall,
            potOdds = potOdds,
            betAsFractionOfPot = betAsFractionOfPot,
            spr = spr,
            effectiveStack = effectiveStack,
            suggestedSizes = BetSizes(
                thirdPot = maxOf(potSize / 3, 1),
                halfPot = maxOf(potSize / 2, 1),
                twoThirdsPot = maxOf(potSize * 2 / 3, 1),
                fullPot = maxOf(potSize, 1),
                minRaise = 20
            ),
            street = street,
            position = position,
            isAggressor = isAggressor,
            isInitiator = isInitiator,
            facingBet = facingBet,
            facingRaise = facingRaise,
            numBetsThisStreet = numBetsThisStreet,
            potType = potType,
            instinct = instinct,
            profile = profile
        )
    }

    // ── Flop — facing a bet ─────────────────────────────────────────

    @Test
    fun `flop MONSTER facing bet with low instinct calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 40
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.confidence >= 0.85)
    }

    @Test
    fun `flop MONSTER facing bet with high instinct raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 75
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence >= 0.8)
    }

    @Test
    fun `flop STRONG facing bet within call ceiling calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            postFlopCallCeiling = 0.85
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.confidence >= 0.7)
    }

    @Test
    fun `flop STRONG facing bet above call ceiling folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 95,
            potSize = 100,
            postFlopCallCeiling = 0.85
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `flop MEDIUM facing small bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 33,
            potSize = 100
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `flop MEDIUM facing large bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 75,
            potSize = 100
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `flop WEAK with draw and decent odds calls`() {
        // 9 outs on the flop, pot = 100, betToCall = 25
        // potOdds = 25/125 = 0.20
        // drawProb = 9/47 = 0.191, * 1.3 comfort = 0.249
        // 0.20 <= 0.249 → decent odds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            facingBet = true,
            totalOuts = 9,
            instinct = 70,
            betToCall = 25,
            potSize = 100
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `flop WEAK with no draw folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            facingBet = true,
            totalOuts = 0,
            betToCall = 50,
            potSize = 100
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `flop NOTHING facing bet folds with high confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            potSize = 100
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
        assertTrue(decision.confidence >= 0.95)
    }

    // ── Flop — facing a raise ───────────────────────────────────────

    @Test
    fun `flop MONSTER facing raise never folds`() {
        for (instinct in listOf(20, 50, 80)) {
            val decision = strategy.decide(ctx(
                tier = HandStrengthTier.MONSTER,
                facingRaise = true,
                facingBet = true,
                betToCall = 100,
                instinct = instinct
            ))
            assertNotEquals(ActionType.FOLD, decision.action.type,
                "Monster should never fold to flop raise (instinct=$instinct)")
        }
    }

    @Test
    fun `flop STRONG facing raise usually folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `flop MEDIUM facing raise always folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingRaise = true,
            facingBet = true,
            betToCall = 100
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
        assertTrue(decision.confidence >= 0.95)
    }

    // ── Flop — checked to (c-bet decisions) ─────────────────────────

    @Test
    fun `flop STRONG as initiator cbets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false,
            betSizePotFraction = 0.50,
            potSize = 100
        ))
        assertEquals(ActionType.RAISE, decision.action.type) // bet = raise from 0
        // Amount should be ~50 (0.50 * 100)
        assertTrue(decision.action.amount >= 20) // at least minRaise
    }

    @Test
    fun `flop NOTHING as initiator on dry board with very high instinct bluffs`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 95
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `flop NOTHING as initiator on dry board with normal instinct checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `flop STRONG not initiator usually checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `flop MEDIUM as initiator cbets sometimes`() {
        // With postFlopCheckProb = 0.70, cbetFreq = 0.30
        // Run many times and verify distribution
        var betCount = 0
        val iterations = 200
        for (i in 0 until iterations) {
            val decision = strategy.decide(ctx(
                tier = HandStrengthTier.MEDIUM,
                isInitiator = true,
                facingBet = false,
                instinct = 50,
                postFlopCheckProb = 0.70
            ))
            if (decision.action.type == ActionType.RAISE) betCount++
        }
        // Should bet roughly 30% of the time (±15% for randomness)
        assertTrue(betCount > iterations * 0.10, "Should bet at least 10% of the time, got ${betCount}/$iterations")
        assertTrue(betCount < iterations * 0.55, "Should bet less than 55% of the time, got ${betCount}/$iterations")
    }

    // ── Turn — facing a bet ─────────────────────────────────────────

    @Test
    fun `turn MONSTER facing bet calls or raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50
        ))
        assertNotEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `turn STRONG facing bet within ceiling calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            facingBet = true,
            betToCall = 60,
            potSize = 100,
            postFlopCallCeiling = 0.85
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `turn STRONG facing large bet on wet board folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            facingBet = true,
            betToCall = 90,
            potSize = 100,
            wetness = BoardWetness.WET,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `turn MEDIUM facing bet usually folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `turn MEDIUM facing small bet on dry board with high instinct calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingBet = true,
            betToCall = 33,
            potSize = 100,
            wetness = BoardWetness.DRY,
            instinct = 75
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── Turn — checked to ───────────────────────────────────────────

    @Test
    fun `turn STRONG as initiator double barrels`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `turn MEDIUM as initiator gives up - the big nit leak`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `turn MEDIUM as initiator with very high instinct and no draws completed barrels`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            instinct = 90,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── River — facing a bet ────────────────────────────────────────

    @Test
    fun `river MONSTER facing bet calls with high confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.confidence >= 0.9)
    }

    @Test
    fun `river STRONG facing small bet on safe board calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            postFlopCallCeiling = 0.85
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `river STRONG facing large bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 90,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `river MEDIUM facing bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `river MEDIUM facing tiny bet with high instinct calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 25,
            potSize = 100,
            instinct = 80
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── River — facing a raise ──────────────────────────────────────

    @Test
    fun `river MONSTER facing raise calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.confidence >= 0.9)
    }

    @Test
    fun `river STRONG facing raise folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
        assertTrue(decision.confidence >= 0.95)
    }

    // ── River — checked to ──────────────────────────────────────────

    @Test
    fun `river MONSTER checked to value bets undersized`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            facingBet = false,
            potSize = 100,
            betSizePotFraction = 0.50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // Expected: pot * 0.50 * 0.8 = 40, but coerced to minRaise(20) if below
        assertTrue(decision.action.amount in 20..50,
            "Monster river bet should be ~40, got ${decision.action.amount}")
        assertTrue(decision.confidence >= 0.8)
    }

    @Test
    fun `river STRONG on dry safe board with instinct above 55 thin value bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 60,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            potSize = 100,
            betSizePotFraction = 0.50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // Expected: pot * 0.50 * 0.65 = 32.5 → 32, coerced to minRaise(20)
        assertTrue(decision.action.amount in 20..40,
            "Thin value bet should be ~32, got ${decision.action.amount}")
    }

    @Test
    fun `river STRONG on wet board checks back - missing value leak`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = false,
            wetness = BoardWetness.WET
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `river STRONG with flush completed checks back`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = false,
            wetness = BoardWetness.DRY,
            flushCompletedThisStreet = true,
            instinct = 80
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `river MEDIUM always checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = false
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
        assertTrue(decision.confidence >= 0.9)
    }

    @Test
    fun `river NOTHING with very high instinct and nut advantage and dry board bluffs`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = false,
            instinct = 95,
            hasNutAdvantage = true,
            wetness = BoardWetness.DRY
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `river NOTHING with normal instinct checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── Multiway tier downgrade ─────────────────────────────────────

    @Test
    fun `STRONG hand in multiway pot plays like MEDIUM`() {
        // STRONG in multiway → downgrades to MEDIUM
        // MEDIUM facing a 60% pot bet → folds
        val multiway = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            potType = PotType.MULTIWAY,
            street = Street.FLOP,
            facingBet = true,
            betToCall = 60,
            potSize = 100
        ))
        // In heads-up, STRONG at 0.60 fraction (below 0.85 ceiling) → CALL
        val headsUp = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            potType = PotType.HEADS_UP,
            street = Street.FLOP,
            facingBet = true,
            betToCall = 60,
            potSize = 100
        ))
        assertEquals(ActionType.CALL, headsUp.action.type, "STRONG in heads-up should call")
        assertEquals(ActionType.FOLD, multiway.action.type, "STRONG in multiway should fold (downgraded to MEDIUM facing >50%)")
    }

    @Test
    fun `MONSTER in multiway stays MONSTER`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            potType = PotType.MULTIWAY,
            facingBet = true,
            betToCall = 50
        ))
        assertNotEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `three-way pot also downgrades tier`() {
        // STRONG in three-way → downgrades to MEDIUM
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            potType = PotType.THREE_WAY,
            street = Street.FLOP,
            facingBet = true,
            betToCall = 75,
            potSize = 100
        ))
        // Downgraded to MEDIUM, facing 75% pot → folds
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Instinct effects ────────────────────────────────────────────

    @Test
    fun `low instinct makes nit tighter on medium cbet`() {
        var checkCount = 0
        val iterations = 100
        for (i in 0 until iterations) {
            val decision = strategy.decide(ctx(
                tier = HandStrengthTier.MEDIUM,
                isInitiator = true,
                facingBet = false,
                instinct = 15,
                postFlopCheckProb = 0.70
            ))
            if (decision.action.type == ActionType.CHECK) checkCount++
        }
        // With low instinct, should check almost every time
        assertTrue(checkCount > iterations * 0.70, "Should check >70% with low instinct, got $checkCount/$iterations")
    }

    @Test
    fun `high instinct makes nit bet more with medium hand`() {
        var betCount = 0
        val iterations = 100
        for (i in 0 until iterations) {
            val decision = strategy.decide(ctx(
                tier = HandStrengthTier.MEDIUM,
                isInitiator = true,
                facingBet = false,
                instinct = 80,
                postFlopCheckProb = 0.70
            ))
            if (decision.action.type == ActionType.RAISE) betCount++
        }
        // With high instinct, should bet noticeably more
        assertTrue(betCount > iterations * 0.20, "Should bet >20% with high instinct, got $betCount/$iterations")
    }

    // ── Confidence levels ───────────────────────────────────────────

    @Test
    fun `clear decisions have high confidence`() {
        val monsterCall = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 40
        ))
        assertTrue(monsterCall.confidence >= 0.8, "Monster call confidence: ${monsterCall.confidence}")

        val nothingFold = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50
        ))
        assertTrue(nothingFold.confidence >= 0.9, "Nothing fold confidence: ${nothingFold.confidence}")

        val strongCbet = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false
        ))
        assertTrue(strongCbet.confidence >= 0.7, "Strong c-bet confidence: ${strongCbet.confidence}")
    }

    @Test
    fun `marginal decisions have low confidence`() {
        val mediumCall = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 33,
            potSize = 100
        ))
        assertTrue(mediumCall.confidence <= 0.6, "Medium call confidence: ${mediumCall.confidence}")

        val rareBluff = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = false,
            instinct = 95,
            hasNutAdvantage = true,
            wetness = BoardWetness.DRY
        ))
        assertTrue(rareBluff.confidence <= 0.2, "Rare bluff confidence: ${rareBluff.confidence}")
    }

    // ── Sizing tells ────────────────────────────────────────────────

    @Test
    fun `strong hand bets at full sizing`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false,
            betSizePotFraction = 0.50,
            potSize = 100
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertEquals(50, decision.action.amount) // pot * 0.50
    }

    @Test
    fun `medium hand bets smaller on wet board`() {
        // Need to set instinct so shouldAct always returns true
        // Use very high instinct to ensure the bet happens
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            betSizePotFraction = 0.50,
            potSize = 100,
            wetness = BoardWetness.WET,
            instinct = 95,
            postFlopCheckProb = 0.01 // almost always bets
        ))
        if (decision.action.type == ActionType.RAISE) {
            // On wet board: 0.50 * 0.6 = 0.30 → 30
            assertTrue(decision.action.amount <= 35,
                "Medium hand on wet board should bet small, got ${decision.action.amount}")
        }
    }

    @Test
    fun `river monster bets slightly undersized`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            facingBet = false,
            betSizePotFraction = 0.50,
            potSize = 100
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // pot * 0.50 * 0.8 = 40
        assertEquals(40, decision.action.amount)
    }

    @Test
    fun `river thin value bet significantly undersized`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = false,
            betSizePotFraction = 0.50,
            potSize = 100,
            wetness = BoardWetness.DRY,
            instinct = 60,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // pot * 0.50 * 0.65 = 32
        assertEquals(32, decision.action.amount)
    }

    // ── Draw odds ───────────────────────────────────────────────────

    @Test
    fun `weak hand with strong draw and decent odds calls on flop`() {
        // 12 outs on flop: drawProb = 12/47 = 0.255, * 1.3 = 0.332
        // potOdds = 20/120 = 0.167, which is <= 0.332 → decent odds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            facingBet = true,
            totalOuts = 12,
            instinct = 70,
            betToCall = 20,
            potSize = 100,
            street = Street.FLOP
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `weak hand with draw but bad odds folds`() {
        // 4 outs: drawProb = 4/47 = 0.085, * 1.3 = 0.111
        // potOdds = 50/150 = 0.333, which is > 0.111 → bad odds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            facingBet = true,
            totalOuts = 4,
            instinct = 70,
            betToCall = 50,
            potSize = 100,
            street = Street.FLOP
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Instinct adjustment ─────────────────────────────────────────

    @Test
    fun `low SPR with marginal hand makes nit more cautious`() {
        // MEDIUM hand at low SPR (< 3.0) → instinct reduced by 10
        // This makes the nit more likely to fold/check
        // Use the turn checked-to scenario where instinct threshold is 80
        // raw instinct 85 → adjusted to 75 (< 80) → checks
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            spr = 2.0,
            instinct = 85,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `low SPR with monster does not reduce instinct`() {
        // MONSTER at low SPR → no instinct reduction (only affects tier > STRONG)
        // instinct 65 stays 65 → on turn facing bet, instinct > 50 → raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50,
            spr = 2.0,
            instinct = 65
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── Key nit leaks ───────────────────────────────────────────────

    @Test
    fun `nit folds strong hand to big turn bet - major leak`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            facingBet = true,
            betToCall = 90,
            potSize = 100,
            wetness = BoardWetness.WET,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type,
            "Nit should fold even strong hands to big turn bets on wet boards")
    }

    @Test
    fun `nit stops barreling medium hands on turn - key leak`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type,
            "Nit should give up with medium hands on the turn")
    }

    @Test
    fun `nit checks back strong hand on scary river - missing value`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = false,
            flushCompletedThisStreet = true,
            instinct = 60
        ))
        assertEquals(ActionType.CHECK, decision.action.type,
            "Nit should check back strong hands on scary rivers")
    }

    @Test
    fun `nit rarely bluffs river`() {
        // Normal instinct = no bluff
        val noBluff = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = false,
            instinct = 80,
            hasNutAdvantage = true,
            wetness = BoardWetness.DRY
        ))
        assertEquals(ActionType.CHECK, noBluff.action.type, "Nit shouldn't bluff at instinct 80")

        // Very high instinct with blockers = rare bluff
        val rareBluff = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = false,
            instinct = 95,
            hasNutAdvantage = true,
            wetness = BoardWetness.DRY
        ))
        assertEquals(ActionType.RAISE, rareBluff.action.type, "Nit should bluff at instinct 95 with blockers")
    }

    @Test
    fun `nit rarely raises postflop except with monsters`() {
        // STRONG facing a bet → calls, doesn't raise
        val strongCall = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, strongCall.action.type,
            "Nit should call, not raise, with strong hand")
    }

    // ── Preflop fallback ────────────────────────────────────────────

    @Test
    fun `preflop MONSTER facing bet raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.PREFLOP,
            facingBet = true,
            betToCall = 30,
            potSize = 45
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `preflop NOTHING facing bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.PREFLOP,
            facingBet = true,
            betToCall = 30,
            potSize = 45
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Call amounts ────────────────────────────────────────────────

    @Test
    fun `call action uses correct betToCall amount`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 73,
            potSize = 150
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertEquals(73, decision.action.amount)
    }
}
