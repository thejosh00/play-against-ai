package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.CallingStationArchetype
import kotlin.test.*

class CallingStationStrategyTest {

    private val strategy = CallingStationStrategy()
    private val nitStrategy = NitStrategy()

    // ── Test helper ─────────────────────────────────────────────────

    private val defaultProfile = PlayerProfile(
        archetype = CallingStationArchetype,
        openRaiseProb = 0.20,
        threeBetProb = 0.05,
        fourBetProb = 0.03,
        rangeFuzzProb = 0.10,
        openRaiseSizeMin = 2.0,
        openRaiseSizeMax = 2.5,
        threeBetSizeMin = 2.3,
        threeBetSizeMax = 2.8,
        fourBetSizeMin = 2.0,
        fourBetSizeMax = 2.3,
        postFlopFoldProb = 0.10,
        postFlopCallCeiling = 0.90,
        postFlopCheckProb = 0.75,
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
        isInitiator: Boolean = false,
        facingBet: Boolean = false,
        facingRaise: Boolean = false,
        numBetsThisStreet: Int = 0,
        potType: PotType = PotType.HEADS_UP,
        instinct: Int = 50,
        postFlopFoldProb: Double = 0.10,
        postFlopCallCeiling: Double = 0.90,
        postFlopCheckProb: Double = 0.75,
        betSizePotFraction: Double = 0.50,
        raiseMultiplier: Double = 2.2,
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

    // ── Core calling behavior — the signature move ────────────────────

    @Test
    fun `MEDIUM facing a flop bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertTrue(decision.confidence >= 0.8)
    }

    @Test
    fun `WEAK with made hand facing a flop bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            madeHand = true,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `WEAK with a draw facing a flop bet calls without pot odds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            madeHand = false,
            totalOuts = 4,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `STRONG facing a flop bet calls not raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MONSTER facing a flop bet with normal instinct calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MONSTER facing a flop bet with very high instinct raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 85
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    // ── Calling station DOES fold with nothing ────────────────────────

    @Test
    fun `NOTHING facing a flop bet with normal instinct folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `NOTHING facing a flop bet with very high instinct calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            instinct = 80
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── Sizing insensitivity ──────────────────────────────────────────

    @Test
    fun `MEDIUM facing half pot bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 50,
            potSize = 100
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing pot-sized bet still calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 100,
            potSize = 100
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing 2x pot overbet still calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 200,
            potSize = 100
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── Passivity — rarely bets when checked to ──────────────────────

    @Test
    fun `STRONG checked to on flop checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `MONSTER checked to on flop with instinct above 55 bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = false,
            instinct = 60
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `MONSTER checked to on flop with low instinct checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = false,
            instinct = 40
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `MEDIUM checked to always checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
        assertTrue(decision.confidence >= 0.85)
    }

    // ── Turn behavior — slightly tighter but still calls wide ────────

    @Test
    fun `MEDIUM facing a turn bet still calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `WEAK with made hand facing a turn bet calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.TURN,
            madeHand = true,
            facingBet = true,
            betToCall = 50,
            instinct = 55
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `NOTHING facing a turn bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.TURN,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── River — the most important street ─────────────────────────────

    @Test
    fun `WEAK with made hand facing a river bet calls — the defining move`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            madeHand = true,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `WEAK with missed draw facing a river bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            madeHand = false,
            totalOuts = 0,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    @Test
    fun `MEDIUM facing a large river bet still calls — sizing insensitive`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 150,
            potSize = 100
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `STRONG facing a river bet calls not raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── River — desperate bluffs ──────────────────────────────────────

    @Test
    fun `WEAK missed draw checked to on river with very high instinct bluffs`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            madeHand = false,
            facingBet = false,
            instinct = 90
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence < 0.15)
    }

    @Test
    fun `WEAK missed draw checked to on river with normal instinct checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            madeHand = false,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `NOTHING checked to on river with very high instinct bluffs`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = false,
            instinct = 95
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        assertTrue(decision.confidence < 0.10)
    }

    @Test
    fun `NOTHING checked to on river with normal instinct checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── Raise tell — when calling station raises they have a monster ──

    @Test
    fun `MONSTER facing bet with very high instinct raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 85
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG facing bet with very high instinct still just calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 50,
            instinct = 85
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing bet with max instinct still just calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 50,
            instinct = 100
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    // ── Facing a raise — still calls ──────────────────────────────────

    @Test
    fun `STRONG facing a flop raise calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingRaise = true,
            facingBet = true,
            betToCall = 100
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing a flop raise with normal instinct calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            instinct = 60
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `NOTHING facing a flop raise folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingRaise = true,
            facingBet = true,
            betToCall = 100
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Session adjustments ───────────────────────────────────────────

    @Test
    fun `calling station calls wider after winning a showdown`() {
        val winMemory = ShowdownMemory(
            handsAgo = 2,
            opponentIndex = 1,
            opponentName = "Victim",
            event = ShowdownEvent.CALLED_AND_WON,
            details = "won with bottom pair"
        )
        val stats = SessionStats(resultBB = 0.0, handsPlayed = 20, recentShowdowns = listOf(winMemory))

        // WEAK, no made hand, 3 outs, instinct 35
        // Without boost: instinct 35, no made hand, totalOuts < 4, instinct 35 < 45 → fold
        // With +15 boost: instinct 50 > 45 → call
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            madeHand = false,
            totalOuts = 3,
            facingBet = true,
            betToCall = 50,
            instinct = 35,
            sessionStats = stats
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `calling station calls wider after being bluffed`() {
        val bluffMemory = ShowdownMemory(
            handsAgo = 1,
            opponentIndex = 1,
            opponentName = "Bluffer",
            event = ShowdownEvent.GOT_BLUFFED,
            details = "showed 7-2"
        )
        val stats = SessionStats(resultBB = 0.0, handsPlayed = 20, recentShowdowns = listOf(bluffMemory))

        // NOTHING facing bet, instinct 68
        // Without boost: 68 < 75 → fold
        // With +10 boost: 78 > 75 → call
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            instinct = 68,
            sessionStats = stats
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `calling station gets looser when losing — opposite of nit`() {
        val losingStats = SessionStats(resultBB = -40.0, handsPlayed = 50, recentShowdowns = emptyList())

        // WEAK, no made hand, 3 outs, instinct 38
        // Without boost: 38 < 45 → fold
        // With +8 boost (losing): 46 > 45 → call
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            madeHand = false,
            totalOuts = 3,
            facingBet = true,
            betToCall = 50,
            instinct = 38,
            sessionStats = losingStats
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `no session data — default behavior unchanged`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 50,
            sessionStats = null
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `calling station folds more to recent winner — specific bettor memory`() {
        val lossMemory = ShowdownMemory(
            handsAgo = 3,
            opponentIndex = 2,
            opponentName = "StrongPlayer",
            event = ShowdownEvent.CALLED_AND_LOST,
            details = "lost to full house"
        )
        val stats = SessionStats(resultBB = -10.0, handsPlayed = 20, recentShowdowns = listOf(lossMemory))
        val bettorRead = opponentRead(playerIndex = 2)

        // NOTHING facing bet, instinct 80
        // Without memory: 80 > 75 → call
        // With -8 memory: 72 < 75 → fold
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            instinct = 80,
            sessionStats = stats,
            bettorRead = bettorRead
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Confidence levels ─────────────────────────────────────────────

    @Test
    fun `calling decisions have high confidence`() {
        val mediumCall = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 50
        ))
        assertTrue(mediumCall.confidence >= 0.75, "Medium call confidence: ${mediumCall.confidence}")

        val strongCall = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = true,
            betToCall = 50
        ))
        assertTrue(strongCall.confidence >= 0.85, "Strong call confidence: ${strongCall.confidence}")

        val monsterCall = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertTrue(monsterCall.confidence >= 0.9, "Monster call confidence: ${monsterCall.confidence}")
    }

    @Test
    fun `fold decisions have moderate confidence — calling station is never confident about folding`() {
        val nothingFold = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertTrue(nothingFold.confidence in 0.6..0.85,
            "Nothing fold confidence should be moderate: ${nothingFold.confidence}")

        val missedDrawFold = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            madeHand = false,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        ))
        assertTrue(missedDrawFold.confidence in 0.6..0.85,
            "Missed draw fold confidence: ${missedDrawFold.confidence}")
    }

    @Test
    fun `rare raise decisions have moderate confidence`() {
        val monsterRaise = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            facingBet = true,
            betToCall = 50,
            instinct = 85
        ))
        assertTrue(monsterRaise.confidence in 0.5..0.8,
            "Monster raise confidence: ${monsterRaise.confidence}")
    }

    @Test
    fun `desperate bluffs have very low confidence — will go to LLM`() {
        val missedDrawBluff = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            madeHand = false,
            facingBet = false,
            instinct = 90
        ))
        assertTrue(missedDrawBluff.confidence < 0.15,
            "Desperate bluff confidence: ${missedDrawBluff.confidence}")

        val nothingBluff = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.RIVER,
            facingBet = false,
            instinct = 95
        ))
        assertTrue(nothingBluff.confidence < 0.10,
            "Nothing bluff confidence: ${nothingBluff.confidence}")
    }

    // ── Comparison tests: calling station vs nit ──────────────────────

    @Test
    fun `WEAK made hand facing flop bet — station calls, nit folds`() {
        val context = ctx(
            tier = HandStrengthTier.WEAK,
            madeHand = true,
            facingBet = true,
            betToCall = 50,
            instinct = 50
        )
        val stationDecision = strategy.decide(context)
        val nitDecision = nitStrategy.decide(context)

        assertEquals(ActionType.CALL, stationDecision.action.type,
            "Calling station should call with weak made hand")
        assertEquals(ActionType.FOLD, nitDecision.action.type,
            "Nit should fold with weak made hand")
    }

    @Test
    fun `STRONG checked to on flop — station checks, nit cbets`() {
        val context = ctx(
            tier = HandStrengthTier.STRONG,
            facingBet = false,
            isInitiator = true,
            instinct = 50
        )
        val stationDecision = strategy.decide(context)
        val nitDecision = nitStrategy.decide(context)

        assertEquals(ActionType.CHECK, stationDecision.action.type,
            "Calling station should check — passive")
        assertEquals(ActionType.RAISE, nitDecision.action.type,
            "Nit should c-bet with strong hand as initiator")
    }

    @Test
    fun `MEDIUM facing a river bet — station calls, nit folds`() {
        val context = ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = true,
            betToCall = 50,
            potSize = 100,
            instinct = 50
        )
        val stationDecision = strategy.decide(context)
        val nitDecision = nitStrategy.decide(context)

        assertEquals(ActionType.CALL, stationDecision.action.type,
            "Calling station should call with medium hand on river")
        assertEquals(ActionType.FOLD, nitDecision.action.type,
            "Nit should fold medium hand on river")
    }

    // ── No tier downgrade in multiway ─────────────────────────────────

    @Test
    fun `STRONG in multiway pot stays STRONG — no tier downgrade`() {
        // Unlike the nit, calling station doesn't downgrade hand strength in multiway
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            potType = PotType.MULTIWAY,
            facingBet = true,
            betToCall = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type,
            "Calling station doesn't downgrade tier in multiway")
    }

    // ── Turn facing raise ─────────────────────────────────────────────

    @Test
    fun `MEDIUM facing turn raise with instinct above 50 calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingRaise = true,
            facingBet = true,
            betToCall = 100,
            instinct = 55
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
            betToCall = 100
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── River facing raise ────────────────────────────────────────────

    @Test
    fun `STRONG facing river raise calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `MEDIUM facing river raise with high instinct calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200,
            instinct = 65
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `WEAK facing river raise folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            facingRaise = true,
            facingBet = true,
            betToCall = 200
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Turn checked to ───────────────────────────────────────────────

    @Test
    fun `MONSTER checked to on turn with instinct above 50 bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.TURN,
            facingBet = false,
            instinct = 55
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG checked to on turn with normal instinct checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.TURN,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    @Test
    fun `MEDIUM checked to on turn always checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.TURN,
            facingBet = false,
            instinct = 80
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── River checked to value bets ───────────────────────────────────

    @Test
    fun `MONSTER checked to on river with instinct above 40 bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.RIVER,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `STRONG checked to on river with high instinct value bets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.STRONG,
            street = Street.RIVER,
            facingBet = false,
            instinct = 70
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `MEDIUM checked to on river checks`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            street = Street.RIVER,
            facingBet = false,
            instinct = 50
        ))
        assertEquals(ActionType.CHECK, decision.action.type)
    }

    // ── Call amounts ──────────────────────────────────────────────────

    @Test
    fun `call action uses correct betToCall amount`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MEDIUM,
            facingBet = true,
            betToCall = 73,
            potSize = 150
        ))
        assertEquals(ActionType.CALL, decision.action.type)
        assertEquals(73, decision.action.amount)
    }

    // ── Preflop fallback ──────────────────────────────────────────────

    @Test
    fun `preflop MONSTER facing bet with high instinct raises`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.PREFLOP,
            facingBet = true,
            betToCall = 30,
            instinct = 80
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
    }

    @Test
    fun `preflop MONSTER facing bet with normal instinct calls`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.MONSTER,
            street = Street.PREFLOP,
            facingBet = true,
            betToCall = 30,
            instinct = 50
        ))
        assertEquals(ActionType.CALL, decision.action.type)
    }

    @Test
    fun `preflop NOTHING facing bet folds`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.NOTHING,
            street = Street.PREFLOP,
            facingBet = true,
            betToCall = 30
        ))
        assertEquals(ActionType.FOLD, decision.action.type)
    }

    // ── Desperate bluff sizing ────────────────────────────────────────

    @Test
    fun `desperate bluff with very high instinct overbets`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            madeHand = false,
            facingBet = false,
            instinct = 95,
            betSizePotFraction = 0.50,
            potSize = 100
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // 0.50 * 1.2 * 100 = 60
        assertTrue(decision.action.amount >= 50,
            "Very high instinct bluff should overbet, got ${decision.action.amount}")
    }

    @Test
    fun `desperate bluff with high instinct undersizes`() {
        val decision = strategy.decide(ctx(
            tier = HandStrengthTier.WEAK,
            street = Street.RIVER,
            madeHand = false,
            facingBet = false,
            instinct = 88,
            betSizePotFraction = 0.50,
            potSize = 100
        ))
        assertEquals(ActionType.RAISE, decision.action.type)
        // 0.50 * 0.4 * 100 = 20
        assertEquals(20, decision.action.amount,
            "Lower instinct bluff should undersize")
    }
}
