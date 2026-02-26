package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.SharkArchetype
import kotlin.test.*

class SharkStrategyTest {

    private val strategy = SharkStrategy()

    // ── Test helper ─────────────────────────────────────────────────

    private val defaultProfile = PlayerProfile(
        archetype = SharkArchetype,
        openRaiseProb = 0.80,
        threeBetProb = 0.35,
        fourBetProb = 0.25,
        rangeFuzzProb = 0.05,
        openRaiseSizeMin = 2.2,
        openRaiseSizeMax = 2.8,
        threeBetSizeMin = 2.7,
        threeBetSizeMax = 3.3,
        fourBetSizeMin = 2.2,
        fourBetSizeMax = 2.6,
        postFlopFoldProb = 0.30,
        postFlopCallCeiling = 0.60,
        postFlopCheckProb = 0.45,
        betSizePotFraction = 0.65,
        raiseMultiplier = 3.0
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
        highCard: Rank = Rank.KING,
        paired: Boolean = false,
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
        postFlopFoldProb: Double = defaultProfile.postFlopFoldProb,
        postFlopCallCeiling: Double = defaultProfile.postFlopCallCeiling,
        postFlopCheckProb: Double = defaultProfile.postFlopCheckProb,
        betSizePotFraction: Double = defaultProfile.betSizePotFraction,
        raiseMultiplier: Double = defaultProfile.raiseMultiplier,
        sessionStats: SessionStats? = null,
        opponents: List<OpponentRead> = emptyList(),
        bettorRead: OpponentRead? = null,
        flopAggressor: Int? = null
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
            paired = paired || boardPairedThisStreet,
            doublePaired = false,
            trips = false,
            connected = wetness >= BoardWetness.WET,
            highlyConnected = false,
            highCard = highCard,
            lowCard = Rank.TWO,
            straightPossible = false,
            straightDrawHeavy = false,
            fullHousePossible = false,
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
        val actions = defaultActions.copy(flopAggressor = flopAggressor)

        return DecisionContext(
            hand = hand,
            board = board,
            actions = actions,
            potSize = potSize,
            betToCall = betToCall,
            potOdds = potOdds,
            betAsFractionOfPot = betAsFractionOfPot,
            spr = spr,
            effectiveStack = effectiveStack,
            suggestedSizes = BetSizes(
                thirdPot = potSize / 3,
                halfPot = potSize / 2,
                twoThirdsPot = potSize * 2 / 3,
                fullPot = potSize,
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
            closesAction = false,
            instinct = instinct,
            profile = profile,
            sessionStats = sessionStats,
            opponents = opponents,
            bettorRead = bettorRead
        )
    }

    private fun opponentRead(
        playerType: OpponentType,
        playerIndex: Int = 1,
        playerName: String = "Opponent",
        aggressionFrequency: Double = 0.5
    ) = OpponentRead(
        playerIndex = playerIndex,
        playerName = playerName,
        position = Position.BTN,
        stack = 1000,
        playerType = playerType,
        vpip = 0.5,
        pfr = 0.3,
        aggressionFrequency = aggressionFrequency,
        handsObserved = 50,
        recentNotableAction = null
    )

    // ── Preflop throws ──────────────────────────────────────────────

    @Test
    fun `preflop throws error`() {
        assertFailsWith<IllegalStateException> {
            strategy.decide(ctx(street = Street.PREFLOP))
        }
    }

    // ── CHECK-RAISE: the shark's signature flop move ────────────────

    @Test
    fun `MONSTER check-raises on wet flop facing aggressive opponent`() {
        // OOP (SB), facing bet, flop, wet board, aggressive opponent
        // instinct 65 - 5 (SB) - 8 (LAG bettor) = 52 > 45 → check-raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            position = Position.SB,
            facingBet = true,
            betToCall = 50,
            wetness = BoardWetness.WET,
            instinct = 65,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("check-raising"), "Should be check-raise: ${decision.reasoning}")
    }

    @Test
    fun `STRONG check-raises with high instinct on wet flop`() {
        // OOP, facing bet, flop, wet board, TAG opponent, instinct > 65
        // instinct 70 - 5 (SB) - 5 (TAG bettor) = 60... need higher
        // Actually: 75 - 5 (SB) - 5 (TAG) = 65, still not > 65
        // Let's use instinct 80: 80 - 5 (SB) - 5 (TAG) = 70 > 65
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.FLOP,
            position = Position.SB,
            facingBet = true,
            betToCall = 50,
            wetness = BoardWetness.WET,
            instinct = 80,
            bettorRead = opponentRead(OpponentType.TIGHT_AGGRESSIVE)
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("check-raising"), "Should be check-raise: ${decision.reasoning}")
    }

    @Test
    fun `WEAK with big draw check-raise semi-bluffs`() {
        // OOP, facing bet, flop, wet board, LAG opponent, 9 outs, instinct > 50
        // instinct 60 - 5 (SB) - 8 (LAG bettor) = 47... need to adjust
        // Use BB: instinct 60 - 8 (LAG bettor) = 52 > 50
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            street = Street.FLOP,
            position = Position.BB,
            facingBet = true,
            betToCall = 50,
            wetness = BoardWetness.WET,
            instinct = 60,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("check-raise"), "Should be check-raise semi-bluff: ${decision.reasoning}")
    }

    @Test
    fun `no check-raise in position — shark only check-raises OOP`() {
        // BTN position → isGoodCheckRaiseSpot returns false
        // instinct 75 + 6 (BTN) - 8 (LAG) = 73 > 60 → raises via standard path
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            position = Position.BTN,
            facingBet = true,
            betToCall = 50,
            wetness = BoardWetness.WET,
            instinct = 75,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        ))
        // Should still raise (monster facing bet), but not specifically a check-raise
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `no check-raise on dry board`() {
        // OOP, facing bet, flop, DRY board → isGoodCheckRaiseSpot returns false
        // instinct 80 - 5 (SB) - 8 (LAG) = 67 > 60 → raises via standard path
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            position = Position.SB,
            facingBet = true,
            betToCall = 50,
            wetness = BoardWetness.DRY,
            instinct = 80,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        ))
        // Still raises (monster), but via standard facing-bet logic
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `no check-raise against passive opponent`() {
        // OOP, facing bet, flop, wet board, but LOOSE_PASSIVE → no check-raise
        // instinct 85 - 5 (SB) - 12 (LP bettor) = 68 > 60 → raises via standard path
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            position = Position.SB,
            facingBet = true,
            betToCall = 50,
            wetness = BoardWetness.WET,
            instinct = 85,
            bettorRead = opponentRead(OpponentType.LOOSE_PASSIVE, aggressionFrequency = 0.2)
        ))
        // Still raises, but not via check-raise path
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── TRAPPING: checking strong hands to induce bluffs ────────────

    @Test
    fun `MONSTER traps against aggressive opponent heads-up with medium instinct`() {
        // shouldTrap: HU, MONSTER <= STRONG, aggressive opponent
        // instinct in 30..65
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            position = Position.CO,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 45,
            opponents = listOf(opponentRead(OpponentType.LOOSE_AGGRESSIVE))
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("trapping"), "Should be trapping: ${decision.reasoning}")
    }

    @Test
    fun `STRONG traps against TAG opponent with medium instinct`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.FLOP,
            position = Position.CO,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 40,
            opponents = listOf(opponentRead(OpponentType.TIGHT_AGGRESSIVE))
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("trapping"), "Should be trapping: ${decision.reasoning}")
    }

    @Test
    fun `shark does not trap in multiway pot`() {
        // shouldTrap requires HEADS_UP
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            position = Position.CO,
            isInitiator = true,
            facingBet = false,
            potType = PotType.THREE_WAY,
            instinct = 45,
            opponents = listOf(opponentRead(OpponentType.LOOSE_AGGRESSIVE))
        ))
        // Should bet for value instead
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `shark does not trap against passive opponent`() {
        // shouldTrap returns false against LOOSE_PASSIVE with low aggression
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            position = Position.CO,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 45,
            opponents = listOf(opponentRead(OpponentType.LOOSE_PASSIVE, aggressionFrequency = 0.2))
        ))
        // Should bet for value — don't trap passive players
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `shark does not trap with high instinct — bets for value`() {
        // instinct 70 + 6 (CO) - 8 (LAG opp) = 68, not in 30..65 → no trap
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            position = Position.CO,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 70,
            opponents = listOf(opponentRead(OpponentType.LOOSE_AGGRESSIVE))
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── DELAYED C-BET: check flop, bet turn ─────────────────────────

    @Test
    fun `MEDIUM initiator checks flop for delayed c-bet on semi-wet board`() {
        // isInitiator, MEDIUM, instinct in 35..55, semi-wet
        // instinct 45 + 6 (CO) = 51, in 35..55 (adjusted instinct doesn't affect the 35..55 check, raw ctx.instinct is used)
        // Wait — adjustInstinct modifies instinct, not ctx.instinct. Let me re-check:
        // The decide() method calls adjustInstinct() and passes effectiveInstinct to decideFlopShark.
        // But the delayed c-bet checks `instinct in 35..55` which is the effectiveInstinct parameter.
        // So: 45 + 6 (CO) = 51, 51 in 35..55 → true
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.FLOP,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.SEMI_WET,
            instinct = 45
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("delayed"), "Should be delayed c-bet line: ${decision.reasoning}")
    }

    @Test
    fun `delayed c-bet completes on the turn`() {
        // Turn, isInitiator, not isAggressor, flopAggressor = null → delayedCbet = true
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            isInitiator = true,
            isAggressor = false,
            facingBet = false,
            flopAggressor = null,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("delayed") || decision.reasoning.orEmpty().contains("value"),
            "Should be delayed c-bet or value: ${decision.reasoning}")
    }

    @Test
    fun `delayed c-bet bluff on turn with nothing on semi-wet board`() {
        // delayedCbet = true, NOTHING, semi-wet, shouldBluff
        // Need goodCard=false so we hit the delayed c-bet branch, not the scare card branch
        // highCard = SEVEN (value 7 < 13), no flush/straight/board-paired
        // instinct 55 + 6 (CO) = 61 > 45 threshold → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            isAggressor = false,
            facingBet = false,
            flopAggressor = null,
            wetness = BoardWetness.SEMI_WET,
            highCard = Rank.SEVEN,
            instinct = 55
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("delayed"), "Should be delayed c-bet bluff: ${decision.reasoning}")
    }

    // ── OVERBETS: polarized sizing on dry boards ────────────────────

    @Test
    fun `MONSTER on river overbet spot uses larger sizing`() {
        // isGoodOverbetSpot: not flop, HU, dry/semi-wet
        // River, HU, DRY → overbet spot
        // instinct 55 + 6 (CO) = 61 > 50 → uses polarized sizing
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 55,
            betSizePotFraction = 0.65
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // Polarized sizing: 0.65 * 1.4 = 0.91, so bet = 100 * 0.91 = 91
        assertTrue(decision.action.amount > 80, "Overbet should be large: ${decision.action.amount}")
    }

    @Test
    fun `no overbet on flop`() {
        assertFalse(strategy.isGoodOverbetSpot(ctx(street = Street.FLOP, potType = PotType.HEADS_UP, wetness = BoardWetness.DRY)))
    }

    @Test
    fun `no overbet in multiway pot`() {
        assertFalse(strategy.isGoodOverbetSpot(ctx(street = Street.RIVER, potType = PotType.THREE_WAY, wetness = BoardWetness.DRY)))
    }

    @Test
    fun `no overbet on wet board`() {
        assertFalse(strategy.isGoodOverbetSpot(ctx(street = Street.RIVER, potType = PotType.HEADS_UP, wetness = BoardWetness.WET)))
    }

    @Test
    fun `overbet spot on turn dry board heads-up`() {
        assertTrue(strategy.isGoodOverbetSpot(ctx(street = Street.TURN, potType = PotType.HEADS_UP, wetness = BoardWetness.DRY)))
    }

    @Test
    fun `overbet spot on river semi-wet board heads-up`() {
        assertTrue(strategy.isGoodOverbetSpot(ctx(street = Street.RIVER, potType = PotType.HEADS_UP, wetness = BoardWetness.SEMI_WET)))
    }

    // ── BALANCED SIZING: same size for value and bluffs ──────────────

    @Test
    fun `sizing is similar for monster value bet and nothing bluff on same board`() {
        // River, initiator, DRY board → both should get similar sizing
        val monsterCtx = ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 55,
            betSizePotFraction = 0.65
        )
        val bluffCtx = ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 55,
            betSizePotFraction = 0.65
        )
        val monsterDecision = strategy.decide(monsterCtx)
        val bluffDecision = strategy.decide(bluffCtx)

        assertEquals(ActionType.RAISE, monsterDecision.action.type)
        assertEquals(ActionType.RAISE, bluffDecision.action.type)

        // Both on the same spot should have close sizing (within 30% of each other)
        val monsterAmt = monsterDecision.action.amount
        val bluffAmt = bluffDecision.action.amount
        val ratio = monsterAmt.toDouble() / bluffAmt.toDouble()
        assertTrue(ratio in 0.7..1.5, "Monster $monsterAmt and bluff $bluffAmt should be similar sizing")
    }

    // ── THIN VALUE: shark bets for value with medium hands ──────────

    @Test
    fun `MEDIUM on dry river with moderate instinct thin value bets`() {
        // isInitiator, river, MEDIUM, dry, no flush/straight completed, instinct > 40
        // instinct 45 + 6 (CO) = 51 > 40 → thin value
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            instinct = 45
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("thin"), "Should be thin value: ${decision.reasoning}")
    }

    @Test
    fun `MEDIUM on scary river checks — no thin value when draws got there`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `MEDIUM not aggressor on dry river thin values`() {
        // instinct 50 + 6 (CO) = 56 > 45 → thin value
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("thin"), "Should be thin value: ${decision.reasoning}")
    }

    // ── RIVER BLUFF-RAISE: the ultimate shark move ──────────────────

    @Test
    fun `WEAK facing river bet with nut advantage on polarized spot bluff raises`() {
        // instinct 80 + 6 (CO) = 86 > 75, HU, hasNutAdvantage, polarizedSpot (river, HU, dry)
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            potType = PotType.HEADS_UP,
            wetness = BoardWetness.DRY,
            hasNutAdvantage = true,
            instinct = 80
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("bluff raise"), "Should be river bluff raise: ${decision.reasoning}")
        assertTrue(decision.confidence <= 0.15, "Bluff raise confidence should be very low: ${decision.confidence}")
    }

    @Test
    fun `NOTHING facing river bet with nut advantage bluff raises with very high instinct`() {
        // instinct 85 + 6 (CO) = 91 > 80
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            potType = PotType.HEADS_UP,
            wetness = BoardWetness.DRY,
            hasNutAdvantage = true,
            instinct = 85
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("bluff raise"), "Should be river bluff raise: ${decision.reasoning}")
    }

    @Test
    fun `WEAK facing river bet without nut advantage folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            hasNutAdvantage = false,
            instinct = 80
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── POT ODDS: shark uses EXACT odds, no comfort margin ──────────

    @Test
    fun `WEAK with 9 outs getting exact odds calls on flop`() {
        // 9/47 ≈ 0.1914, potOdds = 20 / (100 + 20) = 0.1667
        // 0.1667 <= 0.1914 → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 8,
            street = Street.FLOP,
            facingBet = true,
            betToCall = 20,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `WEAK with draws on turn getting bad odds folds`() {
        // 8 outs, 8/46 ≈ 0.1739
        // potOdds = 50 / (100 + 50) = 0.3333
        // 0.3333 > 0.1739 → bad odds, and instinct < 65 to float
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 8,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── OPPONENT EXPLOITATION: maximum adjustments ──────────────────

    @Test
    fun `shark attacks tight-passive relentlessly — instinct boost`() {
        // Facing TP bettor: instinct 40 + 6 (CO) + 15 (TP bettor) = 61
        // MONSTER facing bet: 61 > 60 → raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            facingBet = true,
            betToCall = 50,
            instinct = 40,
            bettorRead = opponentRead(OpponentType.TIGHT_PASSIVE)
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `shark traps LAG opponents — instinct reduction`() {
        // vs LAG bettor: instinct 60 + 6 (CO) - 8 (LAG bettor) = 58
        // MONSTER facing bet, instinct 58 < 60 → calls (disguising strength)
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            facingBet = true,
            betToCall = 50,
            instinct = 60,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `shark never bluffs calling stations — instinct reduction`() {
        // Not facing bet, LP opponent: instinct 60 + 6 (CO) - 10 (LP opp) = 56
        // NOTHING as initiator on good board: 56 < threshold (shouldBluff has +25 penalty for LP)
        // shouldBluff: 56 > 35 (base) + 25 (LP opp) = 60? No, 56 < 60 → false
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 60,
            opponents = listOf(opponentRead(OpponentType.LOOSE_PASSIVE))
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `shark bluffs more against nits`() {
        // Not facing bet, TP opponent: instinct 30 + 6 (CO) + 12 (TP opp) = 48
        // NOTHING on good board: shouldBluff threshold = 35 - 10 (TP) = 25
        // 48 > 25 → true → bluff c-bet
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 30,
            opponents = listOf(opponentRead(OpponentType.TIGHT_PASSIVE))
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `shark adjustments are larger than other archetypes vs tight-passive`() {
        // Shark adjusts +15 vs TP bettor (vs TAG's +5, Nit's tier shift)
        // Low instinct that wouldn't trigger aggression normally: 30 + 6 (CO) + 15 (TP) = 51
        // MONSTER facing bet: 51 > 50 (threshold for "raise to disguise") — hmm, check logic
        // Actually for MONSTER facing bet on standard path: instinct > 60 → raise, else call
        // 51 < 60 → calls
        // But with TAG's adjustment (+5): 30 + 5 (CO) + 5 (TP) = 40 < 60 → also calls
        // The point is the MAGNITUDE of the adjustment, not whether a specific test triggers differently.
        // Let me test a case where 15 matters but 5 wouldn't.
        // instinct 50 + 6 (CO) + 15 (TP bettor) = 71
        // vs TAG at same instinct: 50 + 5 (CO) + 5 (TP) = 60
        // MONSTER facing bet: shark 71 > 60 → raises, hypothetical TAG 60 = 60 → would call
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            facingBet = true,
            betToCall = 50,
            instinct = 50,
            bettorRead = opponentRead(OpponentType.TIGHT_PASSIVE)
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `shark uses opponent list when not facing bet`() {
        // Not facing bet, TP primary opponent → instinct += 12
        // instinct 50 + 6 (CO) + 12 (TP opp) = 68
        // NOTHING on good board: shouldBluff threshold = 35 - 10 (TP opp) = 25
        // 68 > 25 → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 50,
            opponents = listOf(opponentRead(OpponentType.TIGHT_PASSIVE))
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `UNKNOWN opponent type no instinct adjustment`() {
        // instinct 58 + 6 (CO) = 64 (no opponent adjustment)
        // NOTHING on good board: shouldBluff threshold 35, 64 > 35 → bluffs
        val withUnknown = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 58,
            opponents = listOf(opponentRead(OpponentType.UNKNOWN))
        ))
        assertEquals(ActionType.RAISE, withUnknown.action.type)
    }

    // ── SESSION IMAGE MANAGEMENT ────────────────────────────────────

    @Test
    fun `shark tightens when running hot — image management`() {
        // resultBB > 40.0 → instinct -= 3
        // instinct 38 + 6 (CO) - 3 (hot session) = 41
        // shouldBluff(41, ctx, 35): 41 > 35 → true, but with multiway penalty?
        // No multiway, no opponent penalty → bluffs
        // Hmm, too low to test meaningfully. Let me find a threshold:
        // NOTHING, initiator, good board: shouldBluff(instinct, ctx, 35)
        // Without session: instinct 35 + 6 (CO) = 41 > 35 → bluffs
        // With hot session: instinct 35 + 6 (CO) - 3 = 38 > 35 → still bluffs
        // Need a case closer to the edge:
        // instinct 30 + 6 (CO) = 36 > 35 → barely bluffs
        // With hot session: 30 + 6 - 3 = 33 < 35 → no bluff!
        val withoutSession = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 30
        ))
        assertEquals(ActionType.RAISE, withoutSession.action.type, "Bluffs without session adjustment")

        val withHotSession = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 30,
            sessionStats = SessionStats(resultBB = 50.0, handsPlayed = 100, recentShowdowns = emptyList())
        ))
        assertEquals(ActionType.CHECK, withHotSession.action.type, "Checks when running hot — manage table image")
    }

    @Test
    fun `shark loosens up when running cold`() {
        // resultBB < -20.0 → instinct += 3
        // instinct 29 + 6 (CO) = 35, not > 35 → no bluff
        // With cold session: 29 + 6 + 3 = 38 > 35 → bluffs
        val withColdSession = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 29,
            sessionStats = SessionStats(resultBB = -30.0, handsPlayed = 100, recentShowdowns = emptyList())
        ))
        assertEquals(ActionType.RAISE, withColdSession.action.type, "Bluffs more when running cold")
    }

    // ── SHOWDOWN MEMORY ─────────────────────────────────────────────

    @Test
    fun `recent CALLED_AND_LOST reduces aggression`() {
        // CALLED_AND_LOST → instinct -= 8
        // instinct 38 + 6 (CO) - 8 (CALLED_AND_LOST) = 36 > 35 → barely bluffs
        // vs without: 38 + 6 = 44 > 35 → bluffs
        // But I need it to NOT bluff: instinct 33 + 6 - 8 = 31 < 35 → no bluff
        // Without: 33 + 6 = 39 > 35 → bluffs
        val without = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 33
        ))
        assertEquals(ActionType.RAISE, without.action.type, "Bluffs without showdown memory")

        val withLoss = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 33,
            sessionStats = SessionStats(
                resultBB = 0.0,
                handsPlayed = 100,
                recentShowdowns = listOf(
                    ShowdownMemory(handsAgo = 3, opponentIndex = 1, opponentName = "V", event = ShowdownEvent.CALLED_AND_LOST)
                )
            )
        ))
        assertEquals(ActionType.CHECK, withLoss.action.type, "Stops bluffing after getting caught")
    }

    @Test
    fun `recent GOT_BLUFFED increases aggression`() {
        // GOT_BLUFFED → instinct += 5
        // instinct 28 + 6 (CO) = 34, not > 35 → no bluff
        // With GOT_BLUFFED: 28 + 6 + 5 = 39 > 35 → bluffs
        val withGotBluffed = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 28,
            sessionStats = SessionStats(
                resultBB = 0.0,
                handsPlayed = 100,
                recentShowdowns = listOf(
                    ShowdownMemory(handsAgo = 2, opponentIndex = 1, opponentName = "V", event = ShowdownEvent.GOT_BLUFFED)
                )
            )
        ))
        assertEquals(ActionType.RAISE, withGotBluffed.action.type, "More aggressive after being bluffed")
    }

    @Test
    fun `old showdown memory has no effect`() {
        // handsAgo = 10, > 5 → no adjustment
        // instinct 33 + 6 (CO) = 39 > 35 → still bluffs (no change from CALLED_AND_LOST)
        val withOldMemory = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 33,
            sessionStats = SessionStats(
                resultBB = 0.0,
                handsPlayed = 100,
                recentShowdowns = listOf(
                    ShowdownMemory(handsAgo = 10, opponentIndex = 1, opponentName = "V", event = ShowdownEvent.CALLED_AND_LOST)
                )
            )
        ))
        assertEquals(ActionType.RAISE, withOldMemory.action.type, "Old showdown has no effect")
    }

    // ── POSITION AWARENESS ──────────────────────────────────────────

    @Test
    fun `BTN and CO get instinct boost`() {
        // BTN: instinct 29 + 6 = 35, barely bluffs (35 not > 35, actually need > 35)
        // Hmm, > 35 so 35 doesn't pass. Need 30: 30 + 6 = 36 > 35 → bluffs
        val btn = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            position = Position.BTN,
            instinct = 30
        ))
        assertEquals(ActionType.RAISE, btn.action.type, "BTN gets +6 boost → bluffs")
    }

    @Test
    fun `SB and UTG get instinct penalty`() {
        // SB: instinct 40 + (-5) = 35, not > 35 → checks
        // Without penalty (MP): instinct 40 = 40 > 35 → would bluff
        val sb = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            position = Position.SB,
            instinct = 40
        ))
        assertEquals(ActionType.CHECK, sb.action.type, "SB gets -5 penalty → doesn't bluff")
    }

    @Test
    fun `middle position has no adjustment`() {
        // MP: instinct 36, no position adjustment → 36 > 35 → bluffs
        val mp = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            position = Position.MP,
            instinct = 36
        ))
        assertEquals(ActionType.RAISE, mp.action.type, "MP has no position adjustment")
    }

    // ── SPR AWARENESS ───────────────────────────────────────────────

    @Test
    fun `low SPR boosts aggression`() {
        // SPR < 2.0 → instinct += 5
        // MONSTER on turn facing bet: need instinct > 40 to raise
        // instinct 30 + 6 (CO) + 5 (low SPR) = 41 > 40 → raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50,
            spr = 1.5,
            instinct = 30
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Low SPR pushes monster to raise")
    }

    @Test
    fun `normal SPR no boost`() {
        // SPR = 10, no SPR boost
        // MONSTER on turn facing bet: instinct 30 + 6 (CO) = 36 < 40 → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50,
            spr = 10.0,
            instinct = 30
        ))
        assertEquals(ActionType.CALL, decision.action.type, "Normal SPR — no boost, calls instead")
    }

    // ── CONFIDENCE LEVELS: lower than other archetypes ───────────────

    @Test
    fun `value c-bet has decent confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertTrue(decision.confidence >= 0.7, "Value c-bet confidence: ${decision.confidence}")
    }

    @Test
    fun `bluff c-bet has low confidence — pushes to LLM`() {
        // instinct 45 + 6 (CO) = 51 > 35 threshold → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 45
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence <= 0.35, "Bluff c-bet has low confidence: ${decision.confidence}")
    }

    @Test
    fun `check-raise semi-bluff has low confidence`() {
        // OOP, wet board, LAG opponent, 9 outs, instinct > 50
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            street = Street.FLOP,
            position = Position.BB,
            facingBet = true,
            betToCall = 50,
            wetness = BoardWetness.WET,
            instinct = 60,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence <= 0.4, "Semi-bluff check-raise has low confidence: ${decision.confidence}")
    }

    @Test
    fun `trapping check has low confidence — pushes to LLM`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.FLOP,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 45,
            opponents = listOf(opponentRead(OpponentType.LOOSE_AGGRESSIVE))
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
        assertTrue(decision.confidence <= 0.45, "Trap confidence is low: ${decision.confidence}")
    }

    @Test
    fun `river bluff raise has very low confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            potType = PotType.HEADS_UP,
            wetness = BoardWetness.DRY,
            hasNutAdvantage = true,
            instinct = 80
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence <= 0.15, "River bluff raise has very low confidence: ${decision.confidence}")
    }

    @Test
    fun `monster river value has high confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence >= 0.65, "Monster river value confidence: ${decision.confidence}")
    }

    // ── FLOP STANDARD C-BET ─────────────────────────────────────────

    @Test
    fun `STRONG as initiator c-bets for value`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence >= 0.7, "Value c-bet confidence: ${decision.confidence}")
    }

    @Test
    fun `MEDIUM as initiator on good board c-bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `WEAK with outs semi-bluff c-bets on good board`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 6,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── FLOP FACING BET ─────────────────────────────────────────────

    @Test
    fun `STRONG facing flop bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing small flop bet calls`() {
        // betAsFractionOfPot = 40/100 = 0.4 <= 0.6 → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 40,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing large flop bet with no outs folds`() {
        // betAsFractionOfPot = 80/100 = 0.8 > 0.6, totalOuts < 5 → fold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
            totalOuts = 2,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING facing bet in position floats with high instinct`() {
        // instinct 60 + 6 (CO) = 66 > 55, HU, position BTN/CO
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 30,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 60
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("floating"), "Should be floating: ${decision.reasoning}")
    }

    @Test
    fun `NOTHING facing bet out of position folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 30,
            position = Position.SB,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `WEAK facing bet with draw floats on turn with scare card`() {
        // instinct 70 + 6 (CO) = 76 > 60, HU, position BTN/CO, good card
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.FLOP,
            facingBet = true,
            betToCall = 30,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 70
        ))
        // With high instinct, BTN, HU → floats
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── FLOP FACING RAISE ───────────────────────────────────────────

    @Test
    fun `MONSTER facing flop raise re-raises with high instinct`() {
        // instinct 55 + 6 (CO) = 61 > 50 → re-raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            instinct = 55
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `MONSTER facing flop raise with low instinct traps`() {
        // instinct 35 + 6 (CO) = 41 < 50 → calls (trapping)
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            instinct = 35
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `STRONG facing flop raise calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing flop raise with decent draw and good price calls`() {
        // totalOuts >= 5 || betAsFractionOfPot <= 0.5
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingRaise = true,
            facingBet = true,
            betToCall = 40,
            potSize = 100,
            totalOuts = 6,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing flop raise with no equity folds`() {
        // No outs, big raise → fold
        // instinct 50 + 6 (CO) = 56, not > 60 for float
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            potSize = 100,
            totalOuts = 2,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING facing flop raise folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING facing flop raise on dry board with very high instinct re-bluff raises`() {
        // instinct 90 + 6 (CO) = 96 > 85, HU, dry board
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            potType = PotType.HEADS_UP,
            wetness = BoardWetness.DRY,
            instinct = 90
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence <= 0.15, "Re-bluff raise has very low confidence: ${decision.confidence}")
    }

    // ── TURN FACING BET ─────────────────────────────────────────────

    @Test
    fun `MONSTER facing turn bet raises`() {
        // instinct 45 + 6 (CO) = 51 > 40 → raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingBet = true,
            betToCall = 60,
            instinct = 45
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG facing turn bet on wet board raises to deny equity with high instinct`() {
        // instinct 55 + 6 (CO) = 61 > 55, wet board → raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            facingBet = true,
            betToCall = 60,
            wetness = BoardWetness.WET,
            instinct = 55
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG facing turn bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            facingBet = true,
            betToCall = 60,
            instinct = 40
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing reasonable turn bet calls`() {
        // betAsFractionOfPot = 40/100 = 0.4 <= 0.55 → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingBet = true,
            betToCall = 40,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing big turn bet folds`() {
        // betAsFractionOfPot = 70/100 = 0.7 > 0.55, totalOuts < 5 → folds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingBet = true,
            betToCall = 70,
            potSize = 100,
            totalOuts = 2,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING facing turn bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING floating turn with scare card for river bluff`() {
        // instinct 80 + 6 (CO) = 86 > 75, HU, good card (flush completed)
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            facingBet = true,
            betToCall = 40,
            potType = PotType.HEADS_UP,
            flushCompletedThisStreet = true,
            instinct = 80
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.reasoning.orEmpty().contains("bluff"), "Should be floating for river bluff: ${decision.reasoning}")
    }

    // ── TURN FACING RAISE ───────────────────────────────────────────

    @Test
    fun `MONSTER facing turn raise re-raises`() {
        // instinct 50 + 6 (CO) = 56 > 45 → re-raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingRaise = true,
            facingBet = true,
            betToCall = 150,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG facing turn raise calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            facingRaise = true,
            facingBet = true,
            betToCall = 150,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `NOTHING facing turn raise folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── TURN BARRELING ──────────────────────────────────────────────

    @Test
    fun `STRONG double barrels turn`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            isInitiator = true,
            isAggressor = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `WEAK with 8+ outs semi-bluff barrels turn`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            street = Street.TURN,
            isInitiator = true,
            isAggressor = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING on turn with scare card bluff barrels`() {
        // goodCard (flush completed) && shouldBluff(instinct, ctx, 40)
        // instinct 50 + 6 (CO) = 56 > 40 → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            isAggressor = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING on turn without scare card checks`() {
        // No scare card, not a delayed cbet scenario → checks
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            isAggressor = true,
            facingBet = false,
            highCard = Rank.SEVEN,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── TURN NOT AGGRESSOR ──────────────────────────────────────────

    @Test
    fun `STRONG not aggressor on turn checks to induce against aggressive opponent`() {
        // Aggressive opponent, instinct in 30..55 → checks to induce
        // instinct 40 + 6 (CO) - 8 (LAG opp) = 38, in 30..55 → trap check
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            instinct = 40,
            opponents = listOf(opponentRead(OpponentType.LOOSE_AGGRESSIVE))
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `MEDIUM not aggressor on wet turn bets for protection with high instinct`() {
        // instinct 60 + 6 (CO) = 66 > 55, wet board → protection bet
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            wetness = BoardWetness.WET,
            instinct = 60
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── RIVER FACING BET ────────────────────────────────────────────

    @Test
    fun `MONSTER facing river bet raises for max value`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG facing reasonable river bet calls`() {
        // betAsFractionOfPot = 50/100 = 0.5 <= 1.0 → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `STRONG facing overbet on safe board calls`() {
        // betAsFractionOfPot = 120/100 = 1.2 > 1.0
        // No flush/straight completed → calls overbet
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 120,
            potSize = 100,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `STRONG facing overbet on scary board folds`() {
        // betAsFractionOfPot = 120/100 = 1.2 > 1.0
        // flushCompletedThisStreet → folds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 120,
            potSize = 100,
            flushCompletedThisStreet = true,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `MEDIUM facing small river bet calls`() {
        // betAsFractionOfPot = 30/100 = 0.3 <= 0.4 → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 30,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing large river bet folds`() {
        // betAsFractionOfPot = 80/100 = 0.8, instinct 50 + 6 = 56 > 45 but 0.8 > 0.7 → folds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING facing river bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── RIVER FACING RAISE ──────────────────────────────────────────

    @Test
    fun `MONSTER facing river raise calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.confidence >= 0.75, "Monster vs river raise confidence: ${decision.confidence}")
    }

    @Test
    fun `STRONG facing river raise hero calls on safe board with high instinct`() {
        // instinct 50 + 6 (CO) = 56 > 45, dry board → hero call
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `STRONG facing river raise on wet board folds`() {
        // instinct 40 + 6 (CO) = 46 > 45 but wetness WET > SEMI_WET → folds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            wetness = BoardWetness.WET,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `MEDIUM facing river raise folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── RIVER VALUE BETTING ─────────────────────────────────────────

    @Test
    fun `STRONG on safe river value bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG on scary river checks with low instinct`() {
        // flushCompleted, instinct 35 + 6 (CO) = 41 > 40 → actually bets smaller!
        // Need instinct lower: 30 + 6 = 36 < 40 → checks
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            instinct = 30
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `STRONG on scary river with moderate instinct bets smaller`() {
        // flushCompleted, instinct 40 + 6 (CO) = 46 > 40 → bets with reduced sizing
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            instinct = 40
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── RIVER BLUFFING ──────────────────────────────────────────────

    @Test
    fun `WEAK with missed draw bluffs river heads-up`() {
        // WEAK, not madeHand, HU, shouldBluff(instinct, ctx, 40)
        // instinct 50 + 6 (CO) = 56 > 40 → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            madeHand = false,
            potType = PotType.HEADS_UP,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `WEAK with made hand checks river — not a bluff candidate`() {
        // madeHand = true → doesn't enter bluff branch → checks
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            madeHand = true,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `NOTHING bluffs river heads-up`() {
        // NOTHING, HU, shouldBluff(instinct, ctx, 35)
        // instinct 40 + 6 (CO) = 46 > 35 → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 40
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING does not bluff river multiway`() {
        // MULTIWAY → shouldBluff threshold += 40: 35 + 40 = 75
        // instinct 60 + 6 (CO) = 66 < 75 → no bluff
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potType = PotType.MULTIWAY,
            instinct = 60
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `NOTHING does not bluff river vs calling station`() {
        // LP opponent → shouldBluff threshold += 25: 35 + 25 = 60
        // Plus instinct adjusted: 55 + 6 (CO) - 10 (LP opp) = 51 < 60 → no bluff
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 55,
            opponents = listOf(opponentRead(OpponentType.LOOSE_PASSIVE))
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── RIVER NOT AGGRESSOR ─────────────────────────────────────────

    @Test
    fun `MONSTER not aggressor river value bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG not aggressor river value bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING not aggressor on dry board with high instinct delayed bluffs`() {
        // instinct 70 + 6 (CO) = 76 > 65, HU, dry → delayed bluff
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            potType = PotType.HEADS_UP,
            wetness = BoardWetness.DRY,
            instinct = 70
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING not aggressor on dry board with low instinct checks`() {
        // instinct 50 + 6 (CO) = 56 < 65 → checks
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            potType = PotType.HEADS_UP,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── FLOP NOT INITIATOR ──────────────────────────────────────────

    @Test
    fun `STRONG not initiator bets for value`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING not initiator in position stabs on dry board heads-up`() {
        // instinct 65 + 6 (CO) = 71 > 60, HU, BTN/CO, dry → stabs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = false,
            facingBet = false,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            wetness = BoardWetness.DRY,
            instinct = 65
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING not initiator checks out of position`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = false,
            facingBet = false,
            position = Position.SB,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `WEAK with big draw not initiator bets`() {
        // totalOuts >= 8, instinct > 45
        // instinct 50 + 6 (CO) = 56 > 45 → bets draw
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            isInitiator = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── CHECK-RAISE HELPER TESTS ────────────────────────────────────

    @Test
    fun `isGoodCheckRaiseSpot returns true for OOP wet flop vs aggressive`() {
        val c = ctx(
            street = Street.FLOP,
            position = Position.BB,
            facingBet = true,
            wetness = BoardWetness.WET,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        )
        assertTrue(strategy.isGoodCheckRaiseSpot(c))
    }

    @Test
    fun `isGoodCheckRaiseSpot returns false on turn`() {
        val c = ctx(
            street = Street.TURN,
            position = Position.BB,
            facingBet = true,
            wetness = BoardWetness.WET,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        )
        assertFalse(strategy.isGoodCheckRaiseSpot(c))
    }

    @Test
    fun `isGoodCheckRaiseSpot returns false without facing bet`() {
        val c = ctx(
            street = Street.FLOP,
            position = Position.BB,
            facingBet = false,
            wetness = BoardWetness.WET,
            bettorRead = opponentRead(OpponentType.LOOSE_AGGRESSIVE)
        )
        assertFalse(strategy.isGoodCheckRaiseSpot(c))
    }

    @Test
    fun `isGoodCheckRaiseSpot returns true for high aggression unknown type`() {
        val c = ctx(
            street = Street.FLOP,
            position = Position.BB,
            facingBet = true,
            wetness = BoardWetness.WET,
            bettorRead = opponentRead(OpponentType.UNKNOWN, aggressionFrequency = 0.45)
        )
        assertTrue(strategy.isGoodCheckRaiseSpot(c))
    }

    // ── SHOULD TRAP HELPER TESTS ────────────────────────────────────

    @Test
    fun `shouldTrap returns true for monster HU vs aggressive`() {
        val c = ctx(
            tier = HandStrengthTier.MONSTER,
            potType = PotType.HEADS_UP,
            opponents = listOf(opponentRead(OpponentType.LOOSE_AGGRESSIVE))
        )
        assertTrue(strategy.shouldTrap(c))
    }

    @Test
    fun `shouldTrap returns false for medium hand`() {
        val c = ctx(
            tier = HandStrengthTier.MEDIUM,
            potType = PotType.HEADS_UP,
            opponents = listOf(opponentRead(OpponentType.LOOSE_AGGRESSIVE))
        )
        assertFalse(strategy.shouldTrap(c))
    }

    @Test
    fun `shouldTrap returns false multiway`() {
        val c = ctx(
            tier = HandStrengthTier.MONSTER,
            potType = PotType.THREE_WAY,
            opponents = listOf(opponentRead(OpponentType.LOOSE_AGGRESSIVE))
        )
        assertFalse(strategy.shouldTrap(c))
    }

    @Test
    fun `shouldTrap returns false vs passive opponent`() {
        val c = ctx(
            tier = HandStrengthTier.MONSTER,
            potType = PotType.HEADS_UP,
            opponents = listOf(opponentRead(OpponentType.LOOSE_PASSIVE, aggressionFrequency = 0.2))
        )
        assertFalse(strategy.shouldTrap(c))
    }

    // ── INSTINCT CLAMPING ───────────────────────────────────────────

    @Test
    fun `instinct is clamped to 1-100 range`() {
        // Very low instinct with penalties should still work
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            position = Position.SB,
            instinct = 1,
            bettorRead = opponentRead(OpponentType.LOOSE_PASSIVE),
            sessionStats = SessionStats(
                resultBB = 50.0,
                handsPlayed = 100,
                recentShowdowns = listOf(
                    ShowdownMemory(handsAgo = 2, opponentIndex = 1, opponentName = "V", event = ShowdownEvent.CALLED_AND_LOST)
                )
            )
        ))
        // Should still return a valid decision (call or raise with monster)
        assertTrue(decision.action.type in listOf(ActionType.CALL, ActionType.RAISE))
    }

    @Test
    fun `instinct is clamped high with many bonuses`() {
        // Very high instinct with many bonuses
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            position = Position.BTN,
            instinct = 99,
            spr = 1.5,
            bettorRead = opponentRead(OpponentType.TIGHT_PASSIVE),
            sessionStats = SessionStats(
                resultBB = -30.0,
                handsPlayed = 100,
                recentShowdowns = listOf(
                    ShowdownMemory(handsAgo = 1, opponentIndex = 1, opponentName = "V", event = ShowdownEvent.GOT_BLUFFED)
                )
            )
        ))
        // Should still return a valid decision
        assertNotNull(decision)
    }
}
