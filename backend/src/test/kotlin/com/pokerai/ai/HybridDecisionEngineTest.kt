package com.pokerai.ai

import com.pokerai.ai.strategy.ActionDecision
import com.pokerai.ai.strategy.NitStrategy
import com.pokerai.ai.strategy.TagStrategy
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.NitArchetype
import com.pokerai.model.archetype.TagArchetype
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class HybridDecisionEngineTest {

    // ── Test doubles ──────────────────────────────────────────

    private class FakeLlmClient(private val fixedAction: Action = Action.check()) : LlmClient {
        var getDecisionCalled = false
            private set
        var getEnrichedDecisionCalled = false
            private set
        var lastCtx: DecisionContext? = null
            private set
        var lastCodedSuggestion: ActionDecision? = null
            private set

        override suspend fun getDecision(player: Player, state: GameState): AiDecision {
            getDecisionCalled = true
            return AiDecision(fixedAction, null, "test")
        }

        override suspend fun isAvailable(): Boolean = true

        override suspend fun getEnrichedDecision(
            player: Player,
            state: GameState,
            ctx: DecisionContext,
            codedSuggestion: ActionDecision
        ): AiDecision {
            getEnrichedDecisionCalled = true
            lastCtx = ctx
            lastCodedSuggestion = codedSuggestion
            return AiDecision(fixedAction, null, "test")
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun card(notation: String) = Card.fromNotation(notation)
    private fun hole(c1: String, c2: String) = HoleCards(card(c1), card(c2))

    private val nitProfile = PlayerProfile(
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

    private val tagProfile = PlayerProfile(
        archetype = TagArchetype,
        openRaiseProb = 0.55,
        threeBetProb = 0.25,
        fourBetProb = 0.15,
        rangeFuzzProb = 0.05,
        openRaiseSizeMin = 2.5,
        openRaiseSizeMax = 3.0,
        threeBetSizeMin = 3.0,
        threeBetSizeMax = 3.5,
        fourBetSizeMin = 2.2,
        fourBetSizeMax = 2.5,
        postFlopFoldProb = 0.35,
        postFlopCallCeiling = 0.90,
        postFlopCheckProb = 0.40,
        betSizePotFraction = 0.65,
        raiseMultiplier = 2.5
    )

    private fun player(
        index: Int,
        chips: Int = 1000,
        profile: PlayerProfile? = nitProfile,
        holeCards: HoleCards? = null,
        position: Position = Position.CO,
        currentBet: Int = 0
    ): Player = Player(
        index = index,
        name = "Player$index",
        isHuman = false,
        profile = profile,
        chips = chips
    ).apply {
        this.holeCards = holeCards
        this.position = position
        this.currentBet = currentBet
    }

    private fun opponent(index: Int, chips: Int = 1000, position: Position = Position.BTN): Player = Player(
        index = index,
        name = "Player$index",
        isHuman = false,
        profile = null,
        chips = chips
    ).apply {
        this.position = position
    }

    private fun record(playerIndex: Int, type: ActionType, amount: Int = 0, phase: GamePhase) =
        ActionRecord(playerIndex, "Player$playerIndex", Action(type, amount), phase)

    private fun flopState(
        players: List<Player>,
        communityCards: List<Card>,
        pot: Int = 100,
        currentBetLevel: Int = 0,
        minRaise: Int = 10,
        actions: List<ActionRecord> = emptyList()
    ): GameState = GameState(
        players = players,
        communityCards = communityCards.toMutableList(),
        phase = GamePhase.FLOP,
        pot = pot,
        currentBetLevel = currentBetLevel,
        minRaise = minRaise,
        dealerIndex = 0,
        smallBlind = 5,
        bigBlind = 10,
        actionHistory = actions.toMutableList()
    )

    // ── High-confidence coded decision → LLM not called ──────

    @Test
    fun `nit with MONSTER facing bet uses coded strategy without calling LLM`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.check())
        val engine = HybridDecisionEngine(fakeLlm)

        val aiPlayer = player(
            index = 1,
            holeCards = hole("7s", "7h"),
            position = Position.CO
        )
        val opp = opponent(2, position = Position.BTN)

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opp
            ),
            communityCards = listOf(card("Kd"), card("7d"), card("2s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50,
            actions = listOf(
                record(0, ActionType.CHECK, 0, GamePhase.FLOP),
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        )
        aiPlayer.currentBet = 0

        val result = engine.decide(aiPlayer, state)

        assertFalse(fakeLlm.getDecisionCalled, "LLM getDecision should not be called")
        assertFalse(fakeLlm.getEnrichedDecisionCalled, "LLM getEnrichedDecision should not be called")
        // Monster facing bet should call or raise (not fold)
        assertTrue(
            result.action.type == ActionType.CALL || result.action.type == ActionType.RAISE,
            "Should call or raise with a set, got ${result.action.type}"
        )
    }

    @Test
    fun `nit with NOTHING facing bet folds without calling LLM`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.check())
        val engine = HybridDecisionEngine(fakeLlm)

        val aiPlayer = player(
            index = 1,
            holeCards = hole("2s", "3c"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("Qh"), card("9s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50,
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        )
        aiPlayer.currentBet = 0

        val result = engine.decide(aiPlayer, state)

        assertFalse(fakeLlm.getDecisionCalled)
        assertFalse(fakeLlm.getEnrichedDecisionCalled)
        assertEquals(ActionType.FOLD, result.action.type)
    }

    // ── No coded strategy → LLM called directly ─────────────

    @Test
    fun `TAG archetype has coded strategy so LLM is not called directly`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.call(50))
        val engine = HybridDecisionEngine(fakeLlm)

        val aiPlayer = player(
            index = 1,
            profile = tagProfile,
            holeCards = hole("As", "Kc"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50
        )

        val result = engine.decide(aiPlayer, state)

        assertFalse(fakeLlm.getDecisionCalled, "LLM getDecision should not be called — TAG has coded strategy")
    }

    // ── No profile → LLM called directly ────────────────────

    @Test
    fun `player with no profile falls back to LLM`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.check())
        val engine = HybridDecisionEngine(fakeLlm)

        val aiPlayer = player(
            index = 1,
            profile = null,
            holeCards = hole("As", "Kc"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 100
        )

        val result = engine.decide(aiPlayer, state)

        assertTrue(fakeLlm.getDecisionCalled, "LLM should be called when player has no profile")
        assertEquals(ActionType.CHECK, result.action.type)
    }

    // ── Low-confidence coded decision → LLM fallback ────────

    @Test
    fun `low confidence decision triggers LLM fallback with enriched context`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.call(25))
        // Set threshold very high so coded decisions fall below it
        val engine = HybridDecisionEngine(fakeLlm, confidenceThreshold = 0.99)

        val aiPlayer = player(
            index = 1,
            holeCards = hole("7s", "7h"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7d"), card("2s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50,
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        )

        val result = engine.decide(aiPlayer, state)

        assertTrue(fakeLlm.getEnrichedDecisionCalled, "Enriched decision should be called for low confidence")
        assertNotNull(fakeLlm.lastCtx, "Context should be passed to LLM")
        assertNotNull(fakeLlm.lastCodedSuggestion, "Coded suggestion should be passed to LLM")
        assertEquals(ActionType.CALL, result.action.type)
    }

    // ── Confidence threshold boundary ────────────────────────

    @Test
    fun `decision at exactly threshold uses coded decision`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.check())
        // Use threshold of 0.0 — any coded decision should pass
        val engine = HybridDecisionEngine(fakeLlm, confidenceThreshold = 0.0)

        val aiPlayer = player(
            index = 1,
            holeCards = hole("2s", "3c"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("Qh"), card("9s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50,
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        )

        engine.decide(aiPlayer, state)

        assertFalse(fakeLlm.getDecisionCalled, "LLM should not be called when threshold is 0.0")
        assertFalse(fakeLlm.getEnrichedDecisionCalled, "Enriched LLM should not be called when threshold is 0.0")
    }

    @Test
    fun `decision below threshold triggers LLM`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.fold())
        // Use very high threshold — everything goes to LLM
        val engine = HybridDecisionEngine(fakeLlm, confidenceThreshold = 1.01)

        val aiPlayer = player(
            index = 1,
            holeCards = hole("2s", "3c"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("Qh"), card("9s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50,
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        )

        engine.decide(aiPlayer, state)

        assertTrue(fakeLlm.getEnrichedDecisionCalled, "Enriched LLM should be called when threshold is very high")
    }

    // ── AiDecisionService integration ────────────────────────

    @Test
    fun `preflop still uses PreFlopStrategy not hybrid engine`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.call(10))
        val service = AiDecisionService(llmClient = fakeLlm)

        val aiPlayer = player(
            index = 1,
            holeCards = hole("As", "Kc"),
            position = Position.CO,
            chips = 1000
        )

        val state = GameState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            phase = GamePhase.PRE_FLOP,
            pot = 15,
            currentBetLevel = 10,
            minRaise = 10,
            dealerIndex = 0,
            smallBlind = 5,
            bigBlind = 10
        )

        service.decide(aiPlayer, state)

        assertFalse(fakeLlm.getDecisionCalled, "LLM should not be called during preflop")
        assertFalse(fakeLlm.getEnrichedDecisionCalled, "Enriched LLM should not be called during preflop")
    }

    @Test
    fun `postflop uses hybrid engine for nit player`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.check())
        val service = AiDecisionService(llmClient = fakeLlm)

        val aiPlayer = player(
            index = 1,
            holeCards = hole("2s", "3c"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("Qh"), card("9s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50,
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        )

        val result = service.decide(aiPlayer, state)

        // Nit with nothing facing a bet should fold (coded strategy, high confidence)
        // sanitizeAction should leave the fold as-is since there IS a bet
        assertEquals(ActionType.FOLD, result.action.type)
        assertFalse(fakeLlm.getDecisionCalled, "Coded strategy should handle this without LLM")
    }

    @Test
    fun `postflop uses coded strategy for TAG player`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.call(50))
        val service = AiDecisionService(llmClient = fakeLlm)

        val aiPlayer = player(
            index = 1,
            profile = tagProfile,
            holeCards = hole("As", "Kc"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50,
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        )

        val result = service.decide(aiPlayer, state)

        assertFalse(fakeLlm.getDecisionCalled, "LLM should not be called — TAG has coded strategy")
    }

    // ── sanitizeAction still works with coded actions ────────

    @Test
    fun `sanitizeAction converts fold to check when not facing bet`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.check())
        val service = AiDecisionService(llmClient = fakeLlm)

        val aiPlayer = player(
            index = 1,
            holeCards = hole("2s", "3c"),
            position = Position.CO
        )

        // Checked to — no bet to face
        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("Qh"), card("9s")),
            pot = 100,
            currentBetLevel = 0,
            minRaise = 10
        )

        val result = service.decide(aiPlayer, state)

        // If the nit strategy returns CHECK, sanitize should leave it as CHECK
        assertEquals(ActionType.CHECK, result.action.type)
    }

    @Test
    fun `sanitizeAction converts raise exceeding chips to all-in`() = runBlocking {
        val fakeLlm = FakeLlmClient(Action.check())
        val service = AiDecisionService(llmClient = fakeLlm)

        val aiPlayer = player(
            index = 1,
            chips = 30, // very short stack
            holeCards = hole("As", "Ah"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 100,
            currentBetLevel = 50,
            minRaise = 50,
            actions = listOf(
                record(2, ActionType.RAISE, 50, GamePhase.FLOP)
            )
        )

        val result = service.decide(aiPlayer, state)

        // Short-stacked player should end up all-in or calling (sanitize handles this)
        assertTrue(
            result.action.type == ActionType.ALL_IN || result.action.type == ActionType.CALL,
            "Short-stack should go all-in or call, got ${result.action.type}"
        )
    }

    // ── PlayerArchetype.getStrategy() ────────────────────────

    @Test
    fun `NitArchetype getStrategy returns NitStrategy`() {
        val strategy = NitArchetype.getStrategy()
        assertNotNull(strategy)
        assertTrue(strategy is NitStrategy)
    }

    @Test
    fun `TagArchetype getStrategy returns TagStrategy`() {
        val strategy = TagArchetype.getStrategy()
        assertNotNull(strategy)
        assertTrue(strategy is TagStrategy)
    }

    // ── LlmPromptBuilder enriched prompt ─────────────────────

    @Test
    fun `buildEnrichedUserPrompt includes hand strength info`() {
        val aiPlayer = player(
            index = 1,
            holeCards = hole("As", "Kc"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 100
        )

        val ctx = DecisionContextBuilder.build(aiPlayer, state, instinctOverride = 50)

        val prompt = LlmPromptBuilder.buildEnrichedUserPrompt(aiPlayer, state, ctx)

        assertTrue(prompt.contains("Hand strength:"), "Should contain hand strength label")
        assertTrue(prompt.contains("Board texture:"), "Should contain board texture")
        assertTrue(prompt.contains("Draws:"), "Should contain draws info")
        assertTrue(prompt.contains("Street:"), "Should contain street")
        assertTrue(prompt.contains("FLOP"), "Should contain FLOP")
    }

    @Test
    fun `buildEnrichedUserPrompt includes action narratives`() {
        val aiPlayer = player(
            index = 1,
            holeCards = hole("As", "Kc"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 100,
            actions = listOf(
                record(0, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
                record(1, ActionType.CALL, 30, GamePhase.PRE_FLOP)
            )
        )

        val ctx = DecisionContextBuilder.build(aiPlayer, state, instinctOverride = 50)

        val prompt = LlmPromptBuilder.buildEnrichedUserPrompt(aiPlayer, state, ctx)

        assertTrue(prompt.contains("Preflop:"), "Should contain preflop narrative label")
        assertTrue(prompt.contains("Flop:"), "Should contain flop narrative label")
        assertTrue(prompt.contains("ACTION HISTORY"), "Should contain action history section")
    }

    @Test
    fun `buildEnrichedUserPrompt includes coded suggestion when provided`() {
        val aiPlayer = player(
            index = 1,
            holeCards = hole("As", "Kc"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 100
        )

        val ctx = DecisionContextBuilder.build(aiPlayer, state, instinctOverride = 50)
        val suggestion = ActionDecision(Action.fold(), 0.3, "too risky")

        val prompt = LlmPromptBuilder.buildEnrichedUserPrompt(aiPlayer, state, ctx, suggestion)

        assertTrue(prompt.contains("Instinct suggests: fold"), "Should contain coded suggestion")
        assertTrue(prompt.contains("too risky"), "Should contain suggestion reasoning")
    }

    @Test
    fun `buildEnrichedUserPrompt works without coded suggestion`() {
        val aiPlayer = player(
            index = 1,
            holeCards = hole("As", "Kc"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 100
        )

        val ctx = DecisionContextBuilder.build(aiPlayer, state, instinctOverride = 50)

        val prompt = LlmPromptBuilder.buildEnrichedUserPrompt(aiPlayer, state, ctx, codedSuggestion = null)

        assertFalse(prompt.contains("Instinct suggests"), "Should not contain coded suggestion")
        assertTrue(prompt.contains("INSTINCT: 50"), "Should still contain instinct value")
        assertTrue(prompt.contains("What is your action?"), "Should end with the question")
    }

    @Test
    fun `buildEnrichedUserPrompt includes pot geometry`() {
        val aiPlayer = player(
            index = 1,
            holeCards = hole("As", "Kc"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            communityCards = listOf(card("Kd"), card("7h"), card("2s")),
            pot = 200,
            currentBetLevel = 50,
            minRaise = 50
        )

        val ctx = DecisionContextBuilder.build(aiPlayer, state, instinctOverride = 50)

        val prompt = LlmPromptBuilder.buildEnrichedUserPrompt(aiPlayer, state, ctx)

        assertTrue(prompt.contains("Pot size: 200"), "Should contain pot size")
        assertTrue(prompt.contains("Stack-to-pot ratio:"), "Should contain SPR")
        assertTrue(prompt.contains("Pot odds:"), "Should contain pot odds")
        assertTrue(prompt.contains("Suggested sizes:"), "Should contain suggested sizes")
    }

    @Test
    fun `buildEnrichedUserPrompt includes draw info`() {
        val aiPlayer = player(
            index = 1,
            // Hearts for flush draw
            holeCards = hole("Ah", "Kh"),
            position = Position.CO
        )

        val state = flopState(
            players = listOf(
                opponent(0, position = Position.SB),
                aiPlayer,
                opponent(2, position = Position.BTN)
            ),
            // Two hearts on board → flush draw
            communityCards = listOf(card("9h"), card("7h"), card("2s")),
            pot = 100
        )

        val ctx = DecisionContextBuilder.build(aiPlayer, state, instinctOverride = 50)

        val prompt = LlmPromptBuilder.buildEnrichedUserPrompt(aiPlayer, state, ctx)

        assertTrue(prompt.contains("Draws:"), "Should contain draws section")
        // If there's a flush draw detected, we should see outs info
        if (ctx.hand.draws.isNotEmpty()) {
            assertTrue(prompt.contains("outs"), "Should contain outs info for draws")
        }
    }
}
