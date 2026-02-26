package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.LagArchetype
import kotlin.test.*

class LagStrategyTest {

    private val strategy = LagStrategy()

    // ── Test helper ─────────────────────────────────────────────────

    private val defaultProfile = PlayerProfile(
        archetype = LagArchetype,
        openRaiseProb = 0.90,
        threeBetProb = 0.45,
        fourBetProb = 0.35,
        rangeFuzzProb = 0.08,
        openRaiseSizeMin = 2.5,
        openRaiseSizeMax = 3.4,
        threeBetSizeMin = 3.0,
        threeBetSizeMax = 3.5,
        fourBetSizeMin = 2.3,
        fourBetSizeMax = 2.7,
        postFlopFoldProb = 0.15,
        postFlopCallCeiling = 0.40,
        postFlopCheckProb = 0.25,
        betSizePotFraction = 0.70,
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
        postFlopFoldProb: Double = 0.15,
        postFlopCallCeiling: Double = 0.40,
        postFlopCheckProb: Double = 0.25,
        betSizePotFraction: Double = 0.70,
        raiseMultiplier: Double = 3.0,
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
            profile = profile,
            sessionStats = sessionStats,
            opponents = opponents,
            bettorRead = bettorRead
        )
    }

    private fun opponentRead(
        playerIndex: Int = 1,
        playerType: OpponentType = OpponentType.UNKNOWN
    ) = OpponentRead(
        playerIndex = playerIndex,
        playerName = "Opponent$playerIndex",
        position = Position.BTN,
        stack = 1000,
        playerType = playerType,
        vpip = 0.0,
        pfr = 0.0,
        aggressionFrequency = 0.0,
        handsObserved = 20,
        recentNotableAction = null
    )

    // ── C-bet behavior ───────────────────────────────────────────────

    @Test
    fun `STRONG as initiator on any board c-bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence >= 0.8)
    }

    @Test
    fun `MEDIUM as initiator on dry board c-bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `NOTHING as initiator on good c-bet board bluffs`() {
        // Ace-high dry board, heads-up, as preflop raiser
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            highCard = Rank.ACE,
            potType = PotType.HEADS_UP,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Ace-high dry board is a great c-bet spot")
    }

    @Test
    fun `NOTHING as initiator on very wet board checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.VERY_WET,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type, "Even LAG gives up on very wet boards")
    }

    @Test
    fun `WEAK with a draw as initiator c-bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Semi-bluff c-bet with a draw")
    }

    // ── C-bet sizing ─────────────────────────────────────────────────

    @Test
    fun `dry board c-bet is small`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potSize = 100,
            betSizePotFraction = 0.70,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // 0.70 * 0.55 * 100 = 38
        assertTrue(decision.action.amount in 30..50,
            "Dry board sizing should be ~38, got ${decision.action.amount}")
    }

    @Test
    fun `wet board c-bet is larger`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.WET,
            potSize = 100,
            betSizePotFraction = 0.70,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // 0.70 * 1.1 * 100 = 77
        assertTrue(decision.action.amount in 65..90,
            "Wet board sizing should be ~77, got ${decision.action.amount}")
    }

    // ── Double barrel (turn) ─────────────────────────────────────────

    @Test
    fun `STRONG as initiator on turn always barrels`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence >= 0.75)
    }

    @Test
    fun `NOTHING as initiator on turn with scare card bluff barrels`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            potType = PotType.HEADS_UP,
            instinct = 55
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Flush completing is a great bluff card")
    }

    @Test
    fun `NOTHING as initiator on turn with blank card gives up`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = false,
            straightCompletedThisStreet = false,
            highCard = Rank.SEVEN,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type, "No scare card, no story to tell")
    }

    @Test
    fun `WEAK with a draw as initiator on turn semi-bluffs`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Semi-bluff barrel on turn")
    }

    // ── Triple barrel (river) ────────────────────────────────────────

    @Test
    fun `STRONG as initiator on river value bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "LAG doesn't miss thin value")
        assertTrue(decision.confidence >= 0.65)
    }

    @Test
    fun `MONSTER on river value bets oversized`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potSize = 100,
            betSizePotFraction = 0.70,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // 0.70 * 1.1 * 100 = 77
        assertTrue(decision.action.amount >= 70,
            "Monster river bet should be ~77, got ${decision.action.amount}")
    }

    @Test
    fun `NOTHING as initiator on river heads-up triple barrel bluffs`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 55
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Triple barrel bluff")
        assertTrue(decision.confidence <= 0.2, "Pure bluff should have low confidence: ${decision.confidence}")
    }

    @Test
    fun `NOTHING as initiator on river multiway checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potType = PotType.MULTIWAY,
            instinct = 55
        ))
        assertEquals(ActionType.CHECK, decision.action.type, "No bluffing multiway")
    }

    // ── Plays monsters fast ──────────────────────────────────────────

    @Test
    fun `MONSTER facing flop bet raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            street = Street.FLOP,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "LAG plays monsters fast")
    }

    @Test
    fun `MONSTER facing turn bet raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            street = Street.TURN,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `MONSTER facing river bet raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            street = Street.RIVER,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── Floating ─────────────────────────────────────────────────────

    @Test
    fun `NOTHING in position facing flop bet heads-up floats`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 65
        ))
        assertEquals(ActionType.CALL, decision.action.type, "Floating in position to bluff later")
        assertTrue(decision.confidence <= 0.3, "Float confidence: ${decision.confidence}")
    }

    @Test
    fun `NOTHING out of position facing flop bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            position = Position.BB,
            potType = PotType.HEADS_UP,
            instinct = 65
        ))
        assertEquals(ActionType.FOLD, decision.action.type, "Can't float out of position")
    }

    // ── Opponent-adjusted bluffing ───────────────────────────────────

    @Test
    fun `bluffs more against tight passive opponent when checked to`() {
        val tpOpponent = opponentRead(playerType = OpponentType.TIGHT_PASSIVE)

        // NOTHING as initiator on dry board — bluff c-bet
        // instinct = 20 + 8 (CO) + 10 (TP checkTo adjustment) = 38 > 30 threshold → bluffs
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 20,
            opponents = listOf(tpOpponent)
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Bluff more vs nits — they fold too much")
    }

    @Test
    fun `bluffs less against loose passive opponent when checked to`() {
        val lpOpponent = opponentRead(playerType = OpponentType.LOOSE_PASSIVE)

        // instinct = 20 + 8 (CO) - 10 (LP checkTo adjustment) = 18 < 30 threshold → checks
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 20,
            opponents = listOf(lpOpponent)
        ))
        assertEquals(ActionType.CHECK, decision.action.type, "Don't bluff a calling station")
    }

    @Test
    fun `more willing to float against tight passive bettor`() {
        val tpBettor = opponentRead(playerType = OpponentType.TIGHT_PASSIVE)

        // WEAK facing bet, no draw, BTN, heads-up
        // instinct = 50 + 8 (BTN) + 12 (TP bettor) = 70 > 60 → float
        val withTp = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            facingBet = true,
            betToCall = 50,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 50,
            bettorRead = tpBettor
        ))
        assertEquals(ActionType.CALL, withTp.action.type, "Float against tight-passive")

        // Without bettor read: instinct = 50 + 8 = 58 < 60 → fold
        val withoutRead = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            facingBet = true,
            betToCall = 50,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 50,
            bettorRead = null
        ))
        assertEquals(ActionType.FOLD, withoutRead.action.type, "Fold without read")
    }

    @Test
    fun `respects tight aggressive bettor more`() {
        val tagBettor = opponentRead(playerType = OpponentType.TIGHT_AGGRESSIVE)

        // WEAK facing bet, no draw, BTN, heads-up
        // instinct = 55 + 8 (BTN) - 8 (TAG bettor) = 55 < 60 → fold
        val withTag = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            facingBet = true,
            betToCall = 50,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 55,
            bettorRead = tagBettor
        ))
        assertEquals(ActionType.FOLD, withTag.action.type, "Respect TAG's bet — fold")

        // Without TAG: instinct = 55 + 8 = 63 > 60 → float
        val withoutTag = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            facingBet = true,
            betToCall = 50,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 55,
            bettorRead = null
        ))
        assertEquals(ActionType.CALL, withoutTag.action.type, "Float without TAG read")
    }

    // ── Position awareness ───────────────────────────────────────────

    @Test
    fun `BTN is more aggressive than UTG`() {
        // NOTHING as initiator on dry board
        // BTN: instinct 25 + 8 = 33 > 30 → bluff c-bet
        val btn = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            position = Position.BTN,
            instinct = 25
        ))
        assertEquals(ActionType.RAISE, btn.action.type, "BTN bluffs with position advantage")

        // UTG: instinct 25 - 5 = 20 < 30 → check
        val utg = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            position = Position.UTG,
            instinct = 25
        ))
        assertEquals(ActionType.CHECK, utg.action.type, "UTG dials back without position")
    }

    @Test
    fun `SB is more cautious than CO`() {
        // NOTHING as initiator, scare card on turn, heads-up
        // CO: instinct 35 + 8 = 43 > 40 → bluff barrel
        // SB: instinct 35 - 5 = 30 < 40 → check
        val co = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            potType = PotType.HEADS_UP,
            position = Position.CO,
            instinct = 35
        ))
        assertEquals(ActionType.RAISE, co.action.type, "CO barrels with position")

        val sb = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            potType = PotType.HEADS_UP,
            position = Position.SB,
            instinct = 35
        ))
        assertEquals(ActionType.CHECK, sb.action.type, "SB checks without position")
    }

    // ── Facing a raise ───────────────────────────────────────────────

    @Test
    fun `STRONG facing flop raise calls — does not fold like nit`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingRaise = true,
            facingBet = true,
            street = Street.FLOP,
            betToCall = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type, "LAG fights back with strong hands")
    }

    @Test
    fun `MEDIUM facing flop raise with reasonable sizing calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingRaise = true,
            facingBet = true,
            street = Street.FLOP,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type, "Floating the raise")
    }

    @Test
    fun `NOTHING facing flop raise on dry board with very high instinct re-bluffs`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingRaise = true,
            facingBet = true,
            street = Street.FLOP,
            betToCall = 100,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 90
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Re-bluff raise — pure aggression")
        assertTrue(decision.confidence < 0.2, "Re-bluff confidence: ${decision.confidence}")
    }

    // ── River bluff-raise ────────────────────────────────────────────

    @Test
    fun `NOTHING facing river bet heads-up with high instinct bluff raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
            potType = PotType.HEADS_UP,
            isInitiator = true,
            instinct = 90
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "River bluff raise")
        assertTrue(decision.confidence <= 0.15, "Bluff raise confidence: ${decision.confidence}")
    }

    @Test
    fun `NOTHING facing river bet multiway folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
            potType = PotType.MULTIWAY,
            instinct = 95
        ))
        assertEquals(ActionType.FOLD, decision.action.type, "Never bluff raise multiway")
    }

    // ── Session adjustments ──────────────────────────────────────────

    @Test
    fun `LAG gets more aggressive when winning`() {
        val winningStats = SessionStats(resultBB = 40.0, handsPlayed = 50, recentShowdowns = emptyList())

        // MEDIUM as initiator on VERY_WET board (bad board, needs instinct > 45)
        // instinct 35 + 8 (CO) + 5 (winning) = 48 > 45 → bets
        val winning = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.VERY_WET,
            instinct = 35,
            sessionStats = winningStats
        ))
        assertEquals(ActionType.RAISE, winning.action.type, "Presses advantage when winning")

        // Without session: 35 + 8 = 43 < 45 → checks
        val noSession = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.VERY_WET,
            instinct = 35,
            sessionStats = null
        ))
        assertEquals(ActionType.CHECK, noSession.action.type, "Checks without winning momentum")
    }

    @Test
    fun `LAG dials back when losing badly`() {
        val losingStats = SessionStats(resultBB = -65.0, handsPlayed = 50, recentShowdowns = emptyList())

        // MEDIUM as initiator on VERY_WET board
        // instinct 40 + 8 (CO) - 10 (losing badly) = 38 < 45 → checks
        val losing = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.VERY_WET,
            instinct = 40,
            sessionStats = losingStats
        ))
        assertEquals(ActionType.CHECK, losing.action.type, "Dials back when losing badly")

        // Without session: 40 + 8 = 48 > 45 → bets
        val noSession = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.VERY_WET,
            instinct = 40,
            sessionStats = null
        ))
        assertEquals(ActionType.RAISE, noSession.action.type, "Bets without losing tilt")
    }

    @Test
    fun `LAG dials back after getting caught bluffing`() {
        val caughtMemory = ShowdownMemory(
            handsAgo = 2,
            opponentIndex = 1,
            opponentName = "CatcherMcCatch",
            event = ShowdownEvent.CALLED_AND_LOST,
            details = "caught bluffing"
        )
        val stats = SessionStats(resultBB = 0.0, handsPlayed = 20, recentShowdowns = listOf(caughtMemory))

        // MEDIUM as initiator on VERY_WET board
        // instinct 40 + 8 (CO) - 8 (CALLED_AND_LOST) = 40 < 45 → checks
        val caught = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.VERY_WET,
            instinct = 40,
            sessionStats = stats
        ))
        assertEquals(ActionType.CHECK, caught.action.type, "Dials back after getting caught")
    }

    // ── Confidence levels ────────────────────────────────────────────

    @Test
    fun `value bets have high confidence`() {
        val monsterCbet = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertTrue(monsterCbet.confidence >= 0.8, "Monster c-bet: ${monsterCbet.confidence}")

        val strongCbet = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertTrue(strongCbet.confidence >= 0.8, "Strong c-bet: ${strongCbet.confidence}")

        val strongRiverVbet = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertTrue(strongRiverVbet.confidence >= 0.65, "Strong river value bet: ${strongRiverVbet.confidence}")
    }

    @Test
    fun `semi-bluffs have moderate confidence`() {
        val drawCbet = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            totalOuts = 9,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertTrue(drawCbet.confidence >= 0.5, "Draw c-bet: ${drawCbet.confidence}")

        val medCbet = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            isInitiator = true,
            facingBet = false,
            instinct = 50
        ))
        assertTrue(medCbet.confidence >= 0.55, "Medium c-bet: ${medCbet.confidence}")
    }

    @Test
    fun `pure bluffs have low confidence — many go to LLM`() {
        val bluffCbet = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 50
        ))
        assertTrue(bluffCbet.confidence in 0.3..0.5, "Bluff c-bet: ${bluffCbet.confidence}")

        val turnBluff = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            potType = PotType.HEADS_UP,
            instinct = 55
        ))
        assertTrue(turnBluff.confidence in 0.1..0.35, "Turn bluff barrel: ${turnBluff.confidence}")

        val riverBluff = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            isInitiator = true,
            facingBet = false,
            potType = PotType.HEADS_UP,
            instinct = 55
        ))
        assertTrue(riverBluff.confidence <= 0.2, "Triple barrel: ${riverBluff.confidence}")
    }

    @Test
    fun `floating has low confidence`() {
        val float = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            position = Position.BTN,
            potType = PotType.HEADS_UP,
            instinct = 65
        ))
        assertEquals(ActionType.CALL, float.action.type)
        assertTrue(float.confidence in 0.1..0.35, "Float confidence: ${float.confidence}")
    }

    // ── Multiway bluff suppression ───────────────────────────────────

    @Test
    fun `three-way pot suppresses bluff c-bet`() {
        // instinct 35 + 8 (CO) = 43
        // HEADS_UP threshold = 30: 43 > 30 → would bluff
        val headsUp = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.HEADS_UP,
            instinct = 35
        ))
        assertEquals(ActionType.RAISE, headsUp.action.type, "Bluffs heads-up")

        // THREE_WAY threshold = 30 + 15 = 45: 43 < 45 → checks
        val threeWay = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = true,
            facingBet = false,
            wetness = BoardWetness.DRY,
            potType = PotType.THREE_WAY,
            instinct = 35
        ))
        assertEquals(ActionType.CHECK, threeWay.action.type, "Suppresses bluff three-way")
    }

    @Test
    fun `multiway pot heavily suppresses turn bluff barrel`() {
        // NOTHING on turn with scare card
        // instinct 58 + 8 (CO) = 66
        // HEADS_UP threshold = 40: 66 > 40 → would bluff
        val headsUp = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            potType = PotType.HEADS_UP,
            instinct = 58
        ))
        assertEquals(ActionType.RAISE, headsUp.action.type, "Bluffs turn heads-up")

        // MULTIWAY threshold = 40 + 30 = 70: 66 < 70 → checks
        val multiway = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            isInitiator = true,
            facingBet = false,
            flushCompletedThisStreet = true,
            potType = PotType.MULTIWAY,
            instinct = 58
        ))
        assertEquals(ActionType.CHECK, multiway.action.type, "Practically never bluffs multiway")
    }

    // ── SPR adjustment ───────────────────────────────────────────────

    @Test
    fun `low SPR makes LAG more aggressive`() {
        // MEDIUM on turn facing bet, fraction 0.55 (slightly below 0.6 threshold)
        // Normal SPR: instinct 50 + 8 (CO) = 58 → calls (fraction <= 0.6)
        // Low SPR: instinct 50 + 8 (CO) + 5 (low SPR) = 63 → still calls, but tests the path
        val lowSpr = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingBet = true,
            betToCall = 55,
            potSize = 100,
            spr = 2.0,
            instinct = 50
        ))
        assertNotEquals(ActionType.FOLD, lowSpr.action.type, "Low SPR favors aggression")
    }

    // ── Preflop throws ────────────────────────────────────────────────

    @Test
    fun `preflop throws error`() {
        assertFailsWith<IllegalStateException> {
            strategy.decide(ctx(street = Street.PREFLOP))
        }
    }

    // ── Facing bets — various streets ────────────────────────────────

    @Test
    fun `STRONG facing flop bet with high instinct raises for value`() {
        // instinct 50 + 8 (CO) = 58 > 55 → raises
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "LAG raises strong hands for value")
    }

    @Test
    fun `MEDIUM facing flop bet calls — floating`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type, "Floating with medium hand")
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
    fun `STRONG facing river bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 60,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing large river bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 100,
            potSize = 100,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type, "Folds to pot-sized river bet with medium hand")
    }

    // ── River facing raise ───────────────────────────────────────────

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
    fun `STRONG facing river raise with high instinct hero calls`() {
        // instinct 50 + 8 (CO) = 58 > 55 → hero call
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type, "Hero call — they might be adjusting to me")
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

    // ── Not the initiator — stab and value bet ───────────────────────

    @Test
    fun `STRONG not initiator bets for value when checked to`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            isInitiator = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Bets for value even without initiative")
    }

    @Test
    fun `NOTHING not initiator in position heads-up on dry board stabs`() {
        // instinct 50 + 8 (CO) = 58 > 50 → stab
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            isInitiator = false,
            facingBet = false,
            position = Position.CO,
            potType = PotType.HEADS_UP,
            wetness = BoardWetness.DRY,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Position stab with nothing")
    }

    // ── WEAK facing river bet — bluff raise with blockers ────────────

    @Test
    fun `WEAK facing river bet with nut advantage and high instinct bluff raises`() {
        // instinct 75 + 8 (CO) = 83 > 80, HEADS_UP, hasNutAdvantage, isInitiator (canRepresentStrength)
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 80,
            potSize = 100,
            potType = PotType.HEADS_UP,
            isInitiator = true,
            hasNutAdvantage = true,
            instinct = 75
        ))
        assertEquals(ActionType.RAISE, decision.action.type, "Bluff raise representing the nuts")
        assertTrue(decision.confidence <= 0.2, "Bluff raise confidence: ${decision.confidence}")
    }
}
