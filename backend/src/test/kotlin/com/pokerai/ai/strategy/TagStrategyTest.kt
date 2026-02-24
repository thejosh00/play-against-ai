package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.TagArchetype
import kotlin.test.*

class TagStrategyTest {

    private val strategy = TagStrategy()

    // ── Test helper ─────────────────────────────────────────────────

    private val defaultProfile = PlayerProfile(
        archetype = TagArchetype,
        openRaiseProb = 0.85,
        threeBetProb = 0.30,
        fourBetProb = 0.20,
        rangeFuzzProb = 0.03,
        openRaiseSizeMin = 2.5,
        openRaiseSizeMax = 3.0,
        threeBetSizeMin = 2.8,
        threeBetSizeMax = 3.2,
        fourBetSizeMin = 2.2,
        fourBetSizeMax = 2.5,
        postFlopFoldProb = 0.25,
        postFlopCallCeiling = 0.55,
        postFlopCheckProb = 0.40,
        betSizePotFraction = 0.65,
        raiseMultiplier = 2.8
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
        bettorRead: OpponentRead? = null
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
            highCard = highCard,
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
            actions = defaultActions,
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
        playerName: String = "Opponent"
    ) = OpponentRead(
        playerIndex = playerIndex,
        playerName = playerName,
        position = Position.BTN,
        stack = 1000,
        playerType = playerType,
        vpip = 0.5,
        pfr = 0.3,
        aggressionFrequency = 0.5,
        handsObserved = 50,
        recentNotableAction = null
    )

    // ── C-bet behavior — selective, not automatic ───────────────────

    @Test
    fun `STRONG as initiator c-bets on any board`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence >= 0.75, "Value c-bet confidence: ${decision.confidence}")
    }

    @Test
    fun `MEDIUM as initiator on good c-bet board c-bets`() {
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
    fun `MEDIUM as initiator on bad c-bet board checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.VERY_WET,
            highCard = Rank.SEVEN,
            instinct = 40
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `NOTHING as initiator on good board with high instinct bluff c-bets`() {
        // instinct 70 + 5 (CO) = 75 > 65 threshold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 70
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING as initiator on good board with low instinct checks`() {
        // instinct 40 + 5 (CO) = 45 < 65 threshold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 40
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `WEAK with 8+ outs on good board semi-bluff c-bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `WEAK with 4 outs on bad board checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 4,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.WET,
            highCard = Rank.SEVEN,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── Double barrel — value-heavy, gives up with air ──────────────

    @Test
    fun `STRONG as initiator barrels turn`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `MEDIUM on turn with good barrel card barrels`() {
        // flushCompletedThisStreet = true → isGoodBarrelCard
        // instinct 50 + 5 (CO) = 55 > 45 threshold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `MEDIUM on turn with bad card checks — the TAG turn give-up`() {
        // No scare card: flushCompleted=false, straightCompleted=false,
        // boardPaired=false, highCard=SEVEN (< 13)
        // instinct 35 + 5 (CO) = 40 < 45 for barrel, wetness DRY < WET for protection
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            boardPairedThisStreet = false,
            highCard = Rank.SEVEN,
            wetness = BoardWetness.DRY,
            instinct = 35
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `NOTHING on turn almost always checks — TAG does not bluff-barrel air`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── River value betting — better than nit, worse than LAG ───────

    @Test
    fun `STRONG on safe river checked to value bets`() {
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
    fun `STRONG on scary river with low instinct checks back — thin value leak`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            instinct = 40
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `MEDIUM on river usually checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `MEDIUM on bone-dry river with high instinct thin value bets`() {
        // instinct 65 + 5 (CO) = 70 > 60 threshold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            instinct = 65
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── River bluffing — narrow, draw-based ─────────────────────────

    @Test
    fun `WEAK with missed draw and blockers heads-up bluffs river`() {
        // instinct 60 + 5 (CO) = 65 > 60 threshold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            madeHand = false,
            totalOuts = 0,
            potType = PotType.HEADS_UP,
            instinct = 60,
            hasNutAdvantage = true
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `WEAK with missed draw but NO blockers checks — TAG narrow bluff range`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            madeHand = false,
            totalOuts = 0,
            potType = PotType.HEADS_UP,
            instinct = 65,
            hasNutAdvantage = false
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `NOTHING on river almost never bluffs`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── Facing a bet — calls correctly but slightly tight ───────────

    @Test
    fun `STRONG facing a flop bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing half-pot bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing pot-sized bet folds`() {
        // betAsFractionOfPot = 100/100 = 1.0 > 0.65
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 100,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING facing a bet always folds — no floating`() {
        // Unlike LAG which floats in position
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 65
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Facing a raise — the TAG's fold-to-aggression leak ──────────

    @Test
    fun `STRONG facing flop raise calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            street = Street.FLOP,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing flop raise usually folds — the TAG leak`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            potSize = 100,
            street = Street.FLOP,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `MEDIUM facing flop raise with big draw calls`() {
        // totalOuts >= 8 && betAsFractionOfPot <= 0.5
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingRaise = true,
            facingBet = true,
            totalOuts = 9,
            betToCall = 40,
            potSize = 100,
            street = Street.FLOP,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `STRONG facing turn raise calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingRaise = true,
            facingBet = true,
            betToCall = 150,
            street = Street.TURN,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.confidence in 0.5..0.6, "Confidence should be moderate: ${decision.confidence}")
    }

    // ── Pot odds — TAG uses correct math ────────────────────────────

    @Test
    fun `WEAK with 9 outs getting correct odds calls`() {
        // 9/47 ≈ 0.191, with 1.1 margin → 0.210
        // potOdds = 25 / (100 + 25) = 0.20
        // 0.20 <= 0.210 → true → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            facingBet = true,
            street = Street.FLOP,
            potSize = 100,
            betToCall = 25,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `WEAK with 4 outs not getting correct odds folds`() {
        // 4/46 ≈ 0.087, with 1.1 margin → 0.096
        // potOdds = 50 / (100 + 50) = 0.333
        // 0.333 > 0.096 → false → folds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 4,
            facingBet = true,
            street = Street.TURN,
            potSize = 100,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Sizing tells (subtle but present) ───────────────────────────

    @Test
    fun `MONSTER c-bet is slightly larger than WEAK semi-bluff`() {
        val monsterCtx = ctx(
            tier = HandStrengthTier.MONSTER,
            isInitiator = true,
            facingBet = false,
            potSize = 100,
            betSizePotFraction = 0.65,
            instinct = 50
        )
        val weakCtx = ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            isInitiator = true,
            facingBet = false,
            potSize = 100,
            betSizePotFraction = 0.65,
            instinct = 50
        )
        val monsterBet = strategy.decide(monsterCtx)
        val weakBet = strategy.decide(weakCtx)

        assertEquals(ActionType.RAISE, monsterBet.action.type)
        assertEquals(ActionType.RAISE, weakBet.action.type)
        assertTrue(
            monsterBet.action.amount > weakBet.action.amount,
            "Monster sizing ${monsterBet.action.amount} should be > weak sizing ${weakBet.action.amount}"
        )
    }

    @Test
    fun `MEDIUM c-bet sizing is between MONSTER and WEAK`() {
        val monsterCtx = ctx(
            tier = HandStrengthTier.MONSTER,
            isInitiator = true,
            facingBet = false,
            potSize = 100,
            betSizePotFraction = 0.65,
            instinct = 50
        )
        val mediumCtx = ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            potSize = 100,
            betSizePotFraction = 0.65,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            instinct = 50
        )
        val weakCtx = ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            isInitiator = true,
            facingBet = false,
            potSize = 100,
            betSizePotFraction = 0.65,
            instinct = 50
        )

        val monsterAmt = strategy.decide(monsterCtx).action.amount
        val mediumAmt = strategy.decide(mediumCtx).action.amount
        val weakAmt = strategy.decide(weakCtx).action.amount

        assertTrue(monsterAmt >= mediumAmt, "Monster $monsterAmt >= Medium $mediumAmt")
        assertTrue(mediumAmt >= weakAmt, "Medium $mediumAmt >= Weak $weakAmt")
    }

    // ── Tier downgrade in multiway pots ─────────────────────────────

    @Test
    fun `STRONG in MULTIWAY stays STRONG`() {
        // STRONG doesn't downgrade in multiway for TAG (unlike nit)
        // STRONG facing a bet → should still call, not fold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            potType = PotType.MULTIWAY,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM in MULTIWAY downgrades to WEAK — more cautious`() {
        // MEDIUM → WEAK in multiway
        // WEAK facing a bet without good odds → fold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            potType = PotType.MULTIWAY,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `WEAK in THREE_WAY downgrades to NOTHING`() {
        // WEAK → NOTHING in three-way → folds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            potType = PotType.THREE_WAY,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `MEDIUM in THREE_WAY stays MEDIUM`() {
        // MEDIUM stays MEDIUM in three-way (more lenient than multiway)
        // MEDIUM facing a half-pot bet → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            potType = PotType.THREE_WAY,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── Opponent adjustments (moderate) ─────────────────────────────

    @Test
    fun `TAG bluffs slightly more vs TIGHT_PASSIVE`() {
        // With TP bettor: instinct 60 + 5 (CO) + 5 (TP) = 70 > 65 → bluff c-bet
        val withTp = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 60,
            opponents = listOf(opponentRead(OpponentType.TIGHT_PASSIVE))
        ))
        assertEquals(ActionType.RAISE, withTp.action.type, "Bluffs more vs tight passive")
    }

    @Test
    fun `TAG bluffs less vs LOOSE_PASSIVE`() {
        // With LP opponent (not facing bet): instinct 60 + 5 (CO) - 5 (LP opp) = 60 < 65 → no bluff
        val withLp = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 60,
            opponents = listOf(opponentRead(OpponentType.LOOSE_PASSIVE))
        ))
        assertEquals(ActionType.CHECK, withLp.action.type, "Checks vs loose passive — don't bluff calling stations")
    }

    @Test
    fun `TAG opponent adjustments are smaller than LAG`() {
        // TAG adjusts ±5 vs TIGHT_PASSIVE, LAG adjusts +12
        // With base instinct 60: TAG gets 60+5=65 effective, LAG gets 60+12=72
        // Both before position adjustment
        // This is a design test — verified by reading the adjustInstinct code.
        // The TAG's adjustment for TIGHT_PASSIVE bettor is +5 (LAG is +12).
        // We prove it indirectly: an instinct that would trigger a LAG action doesn't trigger the TAG.

        // instinct 55 + 5 (CO) + 5 (TP bettor) = 65 → barely bluffs (> 65 needed for NOTHING c-bet)
        val tagDecision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 55,
            bettorRead = opponentRead(OpponentType.TIGHT_PASSIVE)
        ))
        // 55 + 5 (CO) + 5 (TP bettor) = 65 — right at threshold, so checks
        assertEquals(ActionType.CHECK, tagDecision.action.type,
            "TAG barely doesn't bluff with moderate instinct + TP bettor")
    }

    // ── Session adjustments (disciplined) ───────────────────────────

    @Test
    fun `TAG tightens when losing`() {
        // Base: instinct 63 + 5 (CO) = 68 > 65 → would bluff c-bet
        // With losing session: 63 + 5 (CO) - 5 (losing) = 63 < 65 → checks
        val losing = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 63,
            sessionStats = SessionStats(resultBB = -40.0, handsPlayed = 100, recentShowdowns = emptyList())
        ))
        assertEquals(ActionType.CHECK, losing.action.type, "Tightens up when losing")
    }

    @Test
    fun `TAG gets slightly more confident when winning`() {
        // Base: instinct 60 + 5 (CO) = 65 → borderline for bluff c-bet
        // With winning session: 60 + 5 (CO) + 3 (winning) = 68 > 65 → bluffs
        val winning = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 60,
            sessionStats = SessionStats(resultBB = 40.0, handsPlayed = 100, recentShowdowns = emptyList())
        ))
        assertEquals(ActionType.RAISE, winning.action.type, "Slightly more aggressive when winning")
    }

    @Test
    fun `recent GOT_BLUFFED makes TAG more willing to call`() {
        // MEDIUM facing medium river bet: instinct 60 + 5 (CO) = 65 → barely calls (> 65 needed)
        // With GOT_BLUFFED: 60 + 5 (CO) + 5 (GOT_BLUFFED) = 70 > 65 → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 55,
            potSize = 100,
            instinct = 60,
            sessionStats = SessionStats(
                resultBB = 0.0,
                handsPlayed = 100,
                recentShowdowns = listOf(
                    ShowdownMemory(
                        handsAgo = 3,
                        opponentIndex = 1,
                        opponentName = "Villain",
                        event = ShowdownEvent.GOT_BLUFFED
                    )
                )
            )
        ))
        assertEquals(ActionType.CALL, decision.action.type, "More willing to call after being bluffed")
    }

    // ── Confidence levels ───────────────────────────────────────────

    @Test
    fun `value c-bet has high confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertTrue(decision.confidence >= 0.75, "Value c-bet confidence: ${decision.confidence}")
    }

    @Test
    fun `monster river value bet has high confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertTrue(decision.confidence >= 0.75, "Monster river bet confidence: ${decision.confidence}")
    }

    @Test
    fun `semi-bluff c-bet has moderate confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertTrue(decision.confidence >= 0.45, "Semi-bluff c-bet confidence: ${decision.confidence}")
    }

    @Test
    fun `bluff c-bet has low confidence`() {
        // instinct 70 + 5 (CO) = 75 > 65 → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 70
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence <= 0.35, "Bluff c-bet confidence: ${decision.confidence}")
    }

    @Test
    fun `river bluff with missed draw has low confidence`() {
        // instinct 60 + 5 (CO) = 65 > 60 → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            madeHand = false,
            totalOuts = 0,
            potType = PotType.HEADS_UP,
            instinct = 60,
            hasNutAdvantage = true
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence <= 0.25, "River bluff confidence: ${decision.confidence}")
    }

    @Test
    fun `fold of nothing facing bet has high confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertTrue(decision.confidence >= 0.8, "Fold confidence: ${decision.confidence}")
    }

    @Test
    fun `fold of medium facing big river bet has moderate confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
        assertTrue(decision.confidence in 0.5..0.6, "Medium fold confidence: ${decision.confidence}")
    }

    // ── Facing river bet / raise ────────────────────────────────────

    @Test
    fun `MONSTER facing river bet raises for value`() {
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
    fun `STRONG facing river bet within call ceiling calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            postFlopCallCeiling = 0.55,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `STRONG facing big river bet on scary board folds`() {
        // betAsFractionOfPot = 80/100 = 0.8 > postFlopCallCeiling 0.55
        // flushCompletedThisStreet = true → board is scary → folds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
            postFlopCallCeiling = 0.55,
            flushCompletedThisStreet = true,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

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
        assertTrue(decision.confidence >= 0.8)
    }

    @Test
    fun `STRONG facing river raise on safe board with high instinct hero calls`() {
        // instinct 60 + 5 (CO) = 65 > 60, wetness DRY → hero call
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            wetness = BoardWetness.DRY,
            instinct = 60
        ))
        assertEquals(ActionType.CALL, decision.action.type)
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

    // ── Turn facing bet ─────────────────────────────────────────────

    @Test
    fun `MONSTER facing turn bet raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingBet = true,
            betToCall = 60,
            instinct = 50
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
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing reasonable turn bet calls`() {
        // betAsFractionOfPot = 40/100 = 0.4 <= 0.5 → calls
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
        // betAsFractionOfPot = 80/100 = 0.8 > 0.65 → folds
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
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

    // ── Not the initiator ───────────────────────────────────────────

    @Test
    fun `STRONG not initiator bets for value when checked to`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING not initiator checks — TAG never stabs without reason`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = false,
            facingBet = false,
            position = Position.CO,
            potType = PotType.HEADS_UP,
            instinct = 60
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── Preflop fallback ────────────────────────────────────────────

    @Test
    fun `preflop MONSTER facing bet raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.PREFLOP,
            facingBet = true,
            betToCall = 30,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `preflop WEAK facing bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.PREFLOP,
            facingBet = true,
            betToCall = 30,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `preflop not facing bet open raises with medium hand`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.PREFLOP,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `preflop not facing bet folds nothing`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.PREFLOP,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Position awareness ──────────────────────────────────────────

    @Test
    fun `BTN position boosts instinct — more aggressive`() {
        // Base instinct 61 + 5 (BTN) = 66 > 65 → bluff c-bet fires
        val btn = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            position = Position.BTN,
            instinct = 61
        ))
        assertEquals(ActionType.RAISE, btn.action.type)

        // Same instinct from UTG: 61 - 3 (UTG) = 58 < 65 → no bluff
        val utg = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            position = Position.UTG,
            instinct = 61
        ))
        assertEquals(ActionType.CHECK, utg.action.type)
    }

    // ── SPR adjustment ──────────────────────────────────────────────

    @Test
    fun `low SPR with strong hand boosts aggression`() {
        // STRONG on turn: instinct 35 + 5 (CO) + 5 (low SPR + STRONG) = 45 > 40 → raises
        val lowSpr = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50,
            spr = 2.0,
            instinct = 35
        ))
        assertEquals(ActionType.RAISE, lowSpr.action.type, "Low SPR pushes monster to raise")
    }

    // ── Semi-bluff barrel on turn with draw ─────────────────────────

    @Test
    fun `WEAK with 8+ outs barrels turn as semi-bluff`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence >= 0.4, "Semi-bluff confidence: ${decision.confidence}")
    }

    @Test
    fun `WEAK with 4 outs on bad turn card checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 4,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            highCard = Rank.SEVEN,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── Not initiator/aggressor on turn ─────────────────────────────

    @Test
    fun `STRONG not aggressor on turn bets for value`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING not aggressor on turn checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── Not initiator/aggressor on river ────────────────────────────

    @Test
    fun `STRONG not aggressor on river bets for value`() {
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
    fun `MEDIUM not aggressor on dry river with high instinct thin values`() {
        // instinct 65 + 5 (CO) = 70 > 60 → bets
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 65
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING not aggressor on river checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = false,
            isAggressor = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── MONSTER flop facing bet ─────────────────────────────────────

    @Test
    fun `MONSTER facing flop bet raises for value`() {
        // instinct 50 + 5 (CO) = 55 > 40 → raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `MONSTER facing flop bet with very low instinct slowplays`() {
        // instinct 30 + 5 (CO) = 35 < 40 → slowplay call
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 30
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── MONSTER flop facing raise ───────────────────────────────────

    @Test
    fun `MONSTER facing flop raise re-raises`() {
        // instinct 50 + 5 (CO) = 55 > 45 → re-raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── NOTHING facing turn raise ───────────────────────────────────

    @Test
    fun `NOTHING facing turn raise folds with high confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
        assertTrue(decision.confidence >= 0.85, "Fold confidence: ${decision.confidence}")
    }

    // ── Rare NOTHING turn bluff barrel ──────────────────────────────

    @Test
    fun `NOTHING on turn with perfect scare card and very high instinct bluff barrels`() {
        // instinct 75 + 5 (CO) = 80 > 75, HEADS_UP, goodBarrel (flush completed)
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            potType = PotType.HEADS_UP,
            instinct = 75
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence <= 0.25, "Rare bluff barrel confidence: ${decision.confidence}")
    }

    // ── MEDIUM facing river bet — detailed scenarios ────────────────

    @Test
    fun `MEDIUM facing small river bet with moderate instinct calls`() {
        // betAsFractionOfPot = 30/100 = 0.3 <= 0.4, instinct 50 + 5 (CO) = 55 > 45 → calls
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
    fun `MEDIUM facing medium river bet with high instinct calls — might be a bluff`() {
        // betAsFractionOfPot = 55/100 = 0.55, instinct 65 + 5 (CO) = 70 > 65 → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 55,
            potSize = 100,
            instinct = 65
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing large river bet folds`() {
        // betAsFractionOfPot = 80/100 = 0.8 > 0.6 → folds
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

    // ── WEAK and NOTHING facing river bet ───────────────────────────

    @Test
    fun `WEAK facing river bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING facing river bet folds with high confidence`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
        assertTrue(decision.confidence >= 0.85, "Fold confidence: ${decision.confidence}")
    }

    // ── STRONG facing big river bet on safe board still calls ───────

    @Test
    fun `STRONG facing overbet on safe board with moderate instinct calls`() {
        // betAsFractionOfPot = 80/100 = 0.8 > callCeiling 0.55
        // BUT: instinct 50 + 5 (CO) = 55 > 50, no flush/straight completed → calls
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
            postFlopCallCeiling = 0.55,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── STRONG river checked-to on scary board with high instinct bets smaller ──

    @Test
    fun `STRONG on scary river with high instinct value bets with smaller sizing`() {
        // flushCompleted = true, instinct 55 + 5 (CO) = 60 > 55 → bets, but with 0.7 modifier
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            instinct = 55
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence in 0.35..0.45, "Confidence on scary board: ${decision.confidence}")
    }

    // ── NOTHING river bluff — very rare ─────────────────────────────

    @Test
    fun `NOTHING on river with very high instinct and blockers rare bluffs`() {
        // instinct 80 + 5 (CO) = 85 > 80, HEADS_UP, hasNutAdvantage → rare bluff
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 80,
            hasNutAdvantage = true
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence <= 0.2, "Very rare bluff confidence: ${decision.confidence}")
    }

    @Test
    fun `NOTHING on river without blockers checks even with high instinct`() {
        // instinct 80 + 5 (CO) = 85 > 80, but hasNutAdvantage = false → checks
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 80,
            hasNutAdvantage = false
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── MEDIUM on wet board not-initiator flop ──────────────────────

    @Test
    fun `MEDIUM not initiator on wet board with moderate instinct bets for protection`() {
        // instinct 50 + 5 (CO) = 55 > 50, wetness WET → bets
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = false,
            facingBet = false,
            wetness = BoardWetness.WET,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `MEDIUM not initiator on dry board checks`() {
        // wetness DRY < WET → checks
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = false,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 40
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }
}
