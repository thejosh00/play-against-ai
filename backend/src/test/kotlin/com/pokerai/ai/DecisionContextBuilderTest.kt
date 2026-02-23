package com.pokerai.ai

import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.NitArchetype
import kotlin.test.*

class DecisionContextBuilderTest {

    // ── Helpers ─────────────────────────────────────────────

    private fun card(notation: String) = Card.fromNotation(notation)
    private fun hole(c1: String, c2: String) = HoleCards(card(c1), card(c2))

    private val testProfile = PlayerProfile(
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

    private fun player(index: Int, chips: Int = 1000, hasProfile: Boolean = true): Player =
        Player(
            index = index,
            name = "Player$index",
            isHuman = index == 0,
            profile = if (hasProfile) testProfile else null,
            chips = chips
        )

    private fun record(playerIndex: Int, type: ActionType, amount: Int = 0, phase: GamePhase) =
        ActionRecord(playerIndex, "Player$playerIndex", Action(type, amount), phase)

    private fun gameState(
        playerCount: Int = 6,
        dealerIndex: Int = 0,
        phase: GamePhase = GamePhase.FLOP,
        pot: Int = 15,
        currentBetLevel: Int = 0,
        minRaise: Int = 10,
        smallBlind: Int = 5,
        bigBlind: Int = 10,
        actions: List<ActionRecord> = emptyList(),
        communityCards: List<Card> = emptyList(),
        playerSetup: (List<Player>) -> Unit = {}
    ): GameState {
        val players = (0 until playerCount).map { player(it) }
        players.forEachIndexed { i, p ->
            p.position = Position.forSeat(i, dealerIndex, playerCount)
        }
        playerSetup(players)
        return GameState(
            players = players,
            communityCards = communityCards.toMutableList(),
            phase = phase,
            pot = pot,
            currentBetLevel = currentBetLevel,
            minRaise = minRaise,
            dealerIndex = dealerIndex,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            actionHistory = actions.toMutableList()
        )
    }

    // ── Street mapping ──────────────────────────────────────

    @Test
    fun `street maps correctly from PREFLOP`() {
        val state = gameState(
            phase = GamePhase.PRE_FLOP,
            pot = 15,
            currentBetLevel = 10,
            actions = listOf(
                record(2, ActionType.RAISE, 30, GamePhase.PRE_FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 10
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(Street.PREFLOP, ctx.street)
    }

    @Test
    fun `street maps correctly from FLOP`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(Street.FLOP, ctx.street)
    }

    @Test
    fun `street maps correctly from TURN`() {
        val state = gameState(
            phase = GamePhase.TURN,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"), card("5c"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(Street.TURN, ctx.street)
    }

    @Test
    fun `street maps correctly from RIVER`() {
        val state = gameState(
            phase = GamePhase.RIVER,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"), card("5c"), card("Jd"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(Street.RIVER, ctx.street)
    }

    @Test
    fun `street mapping throws for invalid phases`() {
        for (phase in listOf(GamePhase.WAITING, GamePhase.SHOWDOWN, GamePhase.HAND_COMPLETE)) {
            val state = gameState(phase = phase) { players ->
                players[1].holeCards = hole("As", "Kc")
            }
            assertFailsWith<IllegalStateException> {
                DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
            }
        }
    }

    // ── Pot geometry ────────────────────────────────────────

    @Test
    fun `betToCall is calculated correctly`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 50,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(50, ctx.betToCall)
    }

    @Test
    fun `betToCall is zero when not facing a bet`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 0,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(0, ctx.betToCall)
        assertFalse(ctx.facingBet)
    }

    @Test
    fun `potOdds calculated correctly when facing a bet`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 50,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        // potOdds = 50 / (100 + 50) = 0.333...
        assertEquals(50.0 / 150.0, ctx.potOdds, 0.001)
    }

    @Test
    fun `potOdds is zero when not facing a bet`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 0,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(0.0, ctx.potOdds)
    }

    @Test
    fun `betAsFractionOfPot calculated correctly`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 75,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(2, ActionType.RAISE, 75, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(0.75, ctx.betAsFractionOfPot, 0.001)
    }

    @Test
    fun `overbet results in betAsFractionOfPot greater than 1`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 200,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(2, ActionType.RAISE, 200, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(2.0, ctx.betAsFractionOfPot, 0.001)
    }

    @Test
    fun `SPR calculated correctly`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].chips = 500
            players[2].chips = 300
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(300, ctx.effectiveStack)
        assertEquals(3.0, ctx.spr, 0.001)
    }

    @Test
    fun `SPR uses shortest opponent stack`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].chips = 800
            players[2].chips = 600
            players[3].chips = 200
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(200, ctx.effectiveStack)
        assertEquals(2.0, ctx.spr, 0.001)
    }

    @Test
    fun `SPR ignores folded opponents`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].chips = 800
            players[2].chips = 600
            players[3].chips = 50
            players[3].isFolded = true
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        // Player 3 is folded, so shortest opponent is Player 2 with 600
        // But there are other players (0,4,5) with 1000
        assertEquals(600, ctx.effectiveStack)
    }

    @Test
    fun `all-in opponent with zero chips gives effective stack of zero`() {
        val state = gameState(
            playerCount = 2,
            phase = GamePhase.FLOP,
            pot = 200,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[0].holeCards = hole("As", "Kc")
            players[0].chips = 500
            players[1].chips = 0
            players[1].isAllIn = true
        }
        val ctx = DecisionContextBuilder.build(state.players[0], state, instinctOverride = 50)
        assertEquals(0, ctx.effectiveStack)
        assertEquals(0.0, ctx.spr, 0.001)
    }

    @Test
    fun `suggested sizes are calculated correctly`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 120,
            currentBetLevel = 0,
            minRaise = 10,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(40, ctx.suggestedSizes.thirdPot)
        assertEquals(60, ctx.suggestedSizes.halfPot)
        assertEquals(80, ctx.suggestedSizes.twoThirdsPot)
        assertEquals(120, ctx.suggestedSizes.fullPot)
        assertEquals(10, ctx.suggestedSizes.minRaise) // currentBetLevel(0) + minRaise(10)
    }

    @Test
    fun `suggested sizes have minimum of 1 for small pots`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 2,
            currentBetLevel = 0,
            minRaise = 10,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(1, ctx.suggestedSizes.thirdPot)  // 2/3 = 0 → clamped to 1
        assertEquals(1, ctx.suggestedSizes.halfPot)    // 2/2 = 1
        assertEquals(1, ctx.suggestedSizes.twoThirdsPot) // 2*2/3 = 1
        assertEquals(2, ctx.suggestedSizes.fullPot)
    }

    // ── Situation flags ─────────────────────────────────────

    @Test
    fun `facingBet true when there is a bet to call`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 50,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertTrue(ctx.facingBet)
    }

    @Test
    fun `facingRaise false when opponent bets and we have not acted`() {
        // Opponent bets, we haven't done anything aggressive
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 50,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertTrue(ctx.facingBet)
        assertFalse(ctx.facingRaise)
    }

    @Test
    fun `facingRaise true when we bet and opponent raised`() {
        // We bet 50, opponent raised to 150
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 250,
            currentBetLevel = 150,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(2, ActionType.CHECK, 0, GamePhase.FLOP),
                record(1, ActionType.RAISE, 50, GamePhase.FLOP),
                record(2, ActionType.RAISE, 150, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 50
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertTrue(ctx.facingBet)
        assertTrue(ctx.facingRaise)
    }

    @Test
    fun `facingRaise false when we check and opponent bets`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 50,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(1, ActionType.CHECK, 0, GamePhase.FLOP),
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertTrue(ctx.facingBet)
        assertFalse(ctx.facingRaise)
    }

    @Test
    fun `facingRaise true with multiple raises`() {
        // We bet 50, opponent raises to 150, we re-raise to 400, opponent raises to 900
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 1500,
            currentBetLevel = 900,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(1, ActionType.RAISE, 50, GamePhase.FLOP),
                record(2, ActionType.RAISE, 150, GamePhase.FLOP),
                record(1, ActionType.RAISE, 400, GamePhase.FLOP),
                record(2, ActionType.RAISE, 900, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].currentBet = 400
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertTrue(ctx.facingRaise)
    }

    @Test
    fun `isAggressor when we are current street aggressor`() {
        // We bet on the flop, opponent hasn't acted yet
        val state = gameState(
            playerCount = 2,
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 50,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(0, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        ) { players ->
            players[0].holeCards = hole("As", "Kc")
            players[0].currentBet = 50
        }
        val ctx = DecisionContextBuilder.build(state.players[0], state, instinctOverride = 50)
        assertTrue(ctx.isAggressor)
    }

    @Test
    fun `isAggressor false when opponent is current street aggressor`() {
        val state = gameState(
            playerCount = 2,
            phase = GamePhase.FLOP,
            pot = 100,
            currentBetLevel = 50,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(1, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        ) { players ->
            players[0].holeCards = hole("As", "Kc")
            players[0].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[0], state, instinctOverride = 50)
        assertFalse(ctx.isAggressor)
    }

    @Test
    fun `isInitiator true when we raised preflop and checking turn`() {
        // We raised preflop, bet flop, opponent called. Now on turn, no one has bet yet.
        val state = gameState(
            playerCount = 2,
            phase = GamePhase.TURN,
            pot = 200,
            currentBetLevel = 0,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"), card("5c")),
            actions = listOf(
                record(0, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
                record(1, ActionType.CALL, 20, GamePhase.PRE_FLOP),
                record(0, ActionType.RAISE, 50, GamePhase.FLOP),
                record(1, ActionType.CALL, 50, GamePhase.FLOP)
            )
        ) { players ->
            players[0].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[0], state, instinctOverride = 50)
        assertTrue(ctx.isInitiator)
        assertFalse(ctx.isAggressor) // No one has bet the turn yet
    }

    @Test
    fun `numBetsThisStreet reflects action analysis`() {
        val state = gameState(
            playerCount = 2,
            phase = GamePhase.FLOP,
            pot = 200,
            currentBetLevel = 150,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                record(0, ActionType.RAISE, 50, GamePhase.FLOP),
                record(1, ActionType.RAISE, 150, GamePhase.FLOP)
            )
        ) { players ->
            players[0].holeCards = hole("As", "Kc")
            players[0].currentBet = 50
        }
        val ctx = DecisionContextBuilder.build(state.players[0], state, instinctOverride = 50)
        assertEquals(2, ctx.numBetsThisStreet)
    }

    // ── Edge cases ──────────────────────────────────────────

    @Test
    fun `throws when player has no hole cards`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        )
        // Player 1 has no hole cards
        assertFailsWith<IllegalStateException>("Player Player1 has no hole cards") {
            DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        }
    }

    @Test
    fun `throws when player has no profile`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[0].holeCards = hole("As", "Kc")
            players[0].profile = null
        }
        assertFailsWith<IllegalStateException> {
            DecisionContextBuilder.build(state.players[0], state, instinctOverride = 50)
        }
    }

    @Test
    fun `instinct override works`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 42)
        assertEquals(42, ctx.instinct)
    }

    @Test
    fun `random instinct is in range 1 to 100`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        repeat(50) {
            val ctx = DecisionContextBuilder.build(state.players[1], state)
            assertTrue(ctx.instinct in 1..100, "instinct was ${ctx.instinct}")
        }
    }

    @Test
    fun `SPR is 99 when pot is zero`() {
        val state = gameState(
            phase = GamePhase.FLOP,
            pot = 0,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)
        assertEquals(99.0, ctx.spr)
    }

    // ── Board analysis integration ──────────────────────────

    @Test
    fun `previousCommunityCount is correct per street`() {
        // On FLOP: flush draw possible (2 hearts) but not completed
        val flopState = gameState(
            phase = GamePhase.FLOP,
            pot = 60,
            communityCards = listOf(card("9h"), card("8h"), card("3c"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val flopCtx = DecisionContextBuilder.build(flopState.players[1], flopState, instinctOverride = 50)
        assertFalse(flopCtx.board.flushCompletedThisStreet)

        // On TURN: 3rd heart arrives → flush completed this street
        val turnState = gameState(
            phase = GamePhase.TURN,
            pot = 120,
            communityCards = listOf(card("9h"), card("8h"), card("3c"), card("2h"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val turnCtx = DecisionContextBuilder.build(turnState.players[1], turnState, instinctOverride = 50)
        assertTrue(turnCtx.board.flushCompletedThisStreet)

        // On RIVER: board pairs (8 was already there)
        val riverState = gameState(
            phase = GamePhase.RIVER,
            pot = 240,
            communityCards = listOf(card("9h"), card("8h"), card("3c"), card("2h"), card("8c"))
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
        }
        val riverCtx = DecisionContextBuilder.build(riverState.players[1], riverState, instinctOverride = 50)
        assertTrue(riverCtx.board.boardPairedThisStreet)
    }

    // ── Integration: full scenario ──────────────────────────

    @Test
    fun `full hand scenario - flop decision facing a raise`() {
        // 6-player table, blinds 5/10
        // Preflop: Player 1 (CO-ish) raises to 30, others fold, Player 4 (BB) calls
        // Flop: K♦ 7♥ 2♠, Player 4 checks, Player 1 bets 30, Player 4 raises to 90
        // Player 1 must decide.
        val state = gameState(
            playerCount = 6,
            dealerIndex = 0,
            phase = GamePhase.FLOP,
            pot = 180,  // 60 preflop + 30 bet + 90 raise
            currentBetLevel = 90,
            minRaise = 60,
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            actions = listOf(
                // Preflop
                record(2, ActionType.FOLD, 0, GamePhase.PRE_FLOP),
                record(3, ActionType.FOLD, 0, GamePhase.PRE_FLOP),
                record(1, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
                record(5, ActionType.FOLD, 0, GamePhase.PRE_FLOP),
                record(0, ActionType.FOLD, 0, GamePhase.PRE_FLOP),
                record(4, ActionType.CALL, 20, GamePhase.PRE_FLOP),
                // Flop
                record(4, ActionType.CHECK, 0, GamePhase.FLOP),
                record(1, ActionType.RAISE, 30, GamePhase.FLOP),
                record(4, ActionType.RAISE, 90, GamePhase.FLOP)
            )
        ) { players ->
            players[1].holeCards = hole("As", "Kc")
            players[1].chips = 940   // 1000 - 30 preflop - 30 flop bet
            players[1].currentBet = 30

            // Mark folded players
            players[0].isFolded = true
            players[2].isFolded = true
            players[3].isFolded = true
            players[5].isFolded = true
            players[4].chips = 880   // 1000 - 30 preflop call - 90 flop raise
            players[4].currentBet = 90
        }

        val ctx = DecisionContextBuilder.build(state.players[1], state, instinctOverride = 50)

        // Hand analysis
        assertEquals(HandStrengthTier.STRONG, ctx.hand.tier)
        assertTrue(ctx.hand.madeHandDescription.contains("pair", ignoreCase = true), ctx.hand.madeHandDescription)

        // Board analysis
        assertEquals(BoardWetness.DRY, ctx.board.wetness)
        assertTrue(ctx.board.rainbow)
        assertFalse(ctx.board.paired)

        // Action analysis
        assertEquals(PotType.HEADS_UP, ctx.potType)

        // Situation
        assertEquals(Street.FLOP, ctx.street)
        assertFalse(ctx.isAggressor) // Player 4 is the flop aggressor (they raised last)
        assertTrue(ctx.isInitiator)  // Player 1 raised preflop
        assertTrue(ctx.facingBet)
        assertTrue(ctx.facingRaise)  // We bet 30, they raised to 90
        assertEquals(2, ctx.numBetsThisStreet)

        // Pot geometry
        assertEquals(60, ctx.betToCall)  // 90 - 30
        assertEquals(180, ctx.potSize)
        assertTrue(ctx.betAsFractionOfPot > 0)
        assertTrue(ctx.spr > 0)
        assertEquals(880, ctx.effectiveStack) // min(940, 880)

        // Instinct and profile
        assertEquals(50, ctx.instinct)
        assertEquals(testProfile, ctx.profile)
    }

    @Test
    fun `turn decision with initiative - no bet yet`() {
        // Player 0 raised preflop, c-bet flop, opponent called both.
        // Turn: opponent checks to us. We haven't bet the turn yet.
        val state = gameState(
            playerCount = 2,
            dealerIndex = 0,
            phase = GamePhase.TURN,
            pot = 200,
            currentBetLevel = 0,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"), card("5h")),
            actions = listOf(
                record(0, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
                record(1, ActionType.CALL, 20, GamePhase.PRE_FLOP),
                record(1, ActionType.CHECK, 0, GamePhase.FLOP),
                record(0, ActionType.RAISE, 40, GamePhase.FLOP),
                record(1, ActionType.CALL, 40, GamePhase.FLOP),
                record(1, ActionType.CHECK, 0, GamePhase.TURN)
            )
        ) { players ->
            players[0].holeCards = hole("As", "Ks")
            players[0].chips = 930
            players[1].chips = 930
        }

        val ctx = DecisionContextBuilder.build(state.players[0], state, instinctOverride = 75)

        assertEquals(Street.TURN, ctx.street)
        assertTrue(ctx.isInitiator)    // We had initiative from flop bet
        assertFalse(ctx.isAggressor)   // Haven't bet the turn yet
        assertFalse(ctx.facingBet)     // Opponent checked
        assertFalse(ctx.facingRaise)
        assertEquals(0, ctx.betToCall)
        assertEquals(0.0, ctx.potOdds)
        assertEquals(75, ctx.instinct)
    }

    @Test
    fun `position is correctly set from player`() {
        val state = gameState(
            playerCount = 6,
            dealerIndex = 0,
            phase = GamePhase.FLOP,
            communityCards = listOf(card("Kd"), card("7h"), card("2s"))
        ) { players ->
            players[3].holeCards = hole("As", "Kc")
        }
        val ctx = DecisionContextBuilder.build(state.players[3], state, instinctOverride = 50)
        assertEquals(state.players[3].position, ctx.position)
    }

    @Test
    fun `preflop context works`() {
        val state = gameState(
            playerCount = 6,
            dealerIndex = 0,
            phase = GamePhase.PRE_FLOP,
            pot = 45,
            currentBetLevel = 30,
            communityCards = emptyList(),
            actions = listOf(
                record(2, ActionType.FOLD, 0, GamePhase.PRE_FLOP),
                record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP)
            )
        ) { players ->
            players[4].holeCards = hole("Ah", "Kh")
            players[4].currentBet = 0
        }
        val ctx = DecisionContextBuilder.build(state.players[4], state, instinctOverride = 50)
        assertEquals(Street.PREFLOP, ctx.street)
        assertTrue(ctx.facingBet)
        assertEquals(30, ctx.betToCall)
    }
}
