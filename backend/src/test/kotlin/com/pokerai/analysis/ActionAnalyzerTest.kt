package com.pokerai.analysis

import com.pokerai.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ActionAnalyzerTest {

    // 6 players, dealer=0 → BTN=0, SB=1, BB=2, UTG=3, MP=4, CO=5
    // Preflop action order: UTG(3), MP(4), CO(5), BTN(0), SB(1), BB(2)

    private fun player(index: Int, chips: Int = 1000): Player =
        Player(index = index, name = "Player$index", isHuman = index == 0, profile = null, chips = chips)

    private fun gameState(
        playerCount: Int = 6,
        dealerIndex: Int = 0,
        phase: GamePhase = GamePhase.FLOP,
        smallBlind: Int = 5,
        bigBlind: Int = 10,
        ante: Int = 0,
        actions: List<ActionRecord> = emptyList()
    ): GameState {
        val players = (0 until playerCount).map { player(it) }
        return GameState(
            players = players,
            phase = phase,
            dealerIndex = dealerIndex,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            ante = ante,
            actionHistory = actions.toMutableList()
        )
    }

    private fun record(
        playerIndex: Int,
        type: ActionType,
        amount: Int = 0,
        phase: GamePhase
    ): ActionRecord = ActionRecord(
        playerIndex = playerIndex,
        playerName = "Player$playerIndex",
        action = Action(type, amount),
        phase = phase
    )

    private fun assertClose(expected: Double, actual: Double, message: String = "") {
        assertTrue(
            kotlin.math.abs(expected - actual) < 0.001,
            "$message: expected $expected but got $actual"
        )
    }

    // ========== Aggressor Tracking ==========

    @Test
    fun `preflop aggressor is the raiser`() {
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.CALL, 20, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(3, result.preflopAggressor)
    }

    @Test
    fun `last raiser is aggressor when multiple raises`() {
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.RAISE, 90, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(3, ActionType.CALL, 60, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(5, result.preflopAggressor)
    }

    @Test
    fun `no aggressor when all check on flop`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop: all check
            record(3, ActionType.CHECK, phase = GamePhase.FLOP),
            record(5, ActionType.CHECK, phase = GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertNull(result.flopAggressor)
    }

    @Test
    fun `flop aggressor is the bettor`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop
            record(3, ActionType.RAISE, 40, GamePhase.FLOP),
            record(5, ActionType.CALL, 40, GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(3, result.flopAggressor)
    }

    @Test
    fun `all-in aggressive is aggressor`() {
        // UTG raises to 30, CO goes all-in 200 (remaining chips)
        // CO's total bet = 0 + 200 = 200 > 30 → aggressive
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.ALL_IN, 200, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(3, ActionType.CALL, 170, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(5, result.preflopAggressor)
        assertTrue(result.preflopActions[2].isAggressive)
    }

    @Test
    fun `all-in non-aggressive is not aggressor`() {
        // UTG raises to 100, BB goes all-in 40 (remaining chips, had 10 blind)
        // BB's total bet = 10 + 40 = 50 < 100 → not aggressive
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 100, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.ALL_IN, 40, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(3, result.preflopAggressor) // UTG, not BB
        assertFalse(result.preflopActions[5].isAggressive)
    }

    // ========== Initiative ==========

    @Test
    fun `no initiative on preflop`() {
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertNull(result.initiativeHolder)
    }

    @Test
    fun `flop initiative is preflop aggressor`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop: checks
            record(3, ActionType.CHECK, phase = GamePhase.FLOP),
            record(5, ActionType.CHECK, phase = GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(3, result.initiativeHolder)
    }

    @Test
    fun `turn initiative falls back to preflop when no flop aggressor`() {
        val state = gameState(phase = GamePhase.TURN, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop: all check
            record(3, ActionType.CHECK, phase = GamePhase.FLOP),
            record(5, ActionType.CHECK, phase = GamePhase.FLOP),
            // Turn
            record(3, ActionType.CHECK, phase = GamePhase.TURN)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(3, result.initiativeHolder)
    }

    @Test
    fun `turn initiative is flop aggressor`() {
        val state = gameState(phase = GamePhase.TURN, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop: CO bets
            record(3, ActionType.CHECK, phase = GamePhase.FLOP),
            record(5, ActionType.RAISE, 40, GamePhase.FLOP),
            record(3, ActionType.CALL, 40, GamePhase.FLOP),
            // Turn
            record(3, ActionType.CHECK, phase = GamePhase.TURN)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(5, result.initiativeHolder) // Flop aggressor is CO(5)
    }

    // ========== Pot Type ==========

    @Test
    fun `pot type heads up after folds`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.CALL, 20, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(PotType.HEADS_UP, result.potType)
        assertEquals(2, result.numPlayersInPot)
    }

    @Test
    fun `pot type three way`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.CALL, 20, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(PotType.THREE_WAY, result.potType)
        assertEquals(3, result.numPlayersInPot)
    }

    @Test
    fun `pot type multiway no folds`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(1, ActionType.CALL, 25, GamePhase.PRE_FLOP),
            record(2, ActionType.CALL, 20, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(PotType.MULTIWAY, result.potType)
        assertEquals(6, result.numPlayersInPot)
    }

    // ========== Pot Fractions ==========

    @Test
    fun `pot fraction preflop raise`() {
        // Starting pot = 5 + 10 = 15, betLevel = 10
        // UTG raises to 30: raiseSize = 30-10 = 20, fraction = 20/15 = 1.333
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(20.0 / 15.0, result.preflopActions[0].amountAsPotFraction, "UTG raise fraction")
    }

    @Test
    fun `pot fraction preflop call`() {
        // Starting pot = 15, UTG raises to 30 → runningPot = 45
        // CO calls 30: fraction = 30/45 = 0.666
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(30.0 / 45.0, result.preflopActions[1].amountAsPotFraction, "CO call fraction")
    }

    @Test
    fun `pot fraction BB call accounts for blind`() {
        // Starting pot = 15, UTG raises to 30 → runningPot = 45
        // CO calls 30 → runningPot = 75, SB folds, BB calls 20 (30-10 blind)
        // BB fraction = 20/75 = 0.266
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.CALL, 20, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(20.0 / 75.0, result.preflopActions[5].amountAsPotFraction, "BB call fraction")
    }

    @Test
    fun `pot fraction flop bet`() {
        // After preflop: UTG raises to 30, CO calls 30, BB calls 20
        // runningPot = 15 + 30 + 30 + 20 = 95
        // Flop: UTG bets 40 → raiseSize = 40-0 = 40, fraction = 40/95 = 0.421
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.CALL, 20, GamePhase.PRE_FLOP),
            // Flop
            record(2, ActionType.CHECK, phase = GamePhase.FLOP),
            record(3, ActionType.RAISE, 40, GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(40.0 / 95.0, result.flopActions[1].amountAsPotFraction, "Flop bet fraction")
    }

    @Test
    fun `pot fraction postflop call`() {
        // Flop: UTG bets 40 → runningPot = 95+40=135
        // CO calls 40: fraction = 40/135 = 0.296
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop
            record(3, ActionType.RAISE, 40, GamePhase.FLOP),
            record(5, ActionType.CALL, 40, GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        // runningPot at flop start: 15 + 30 + 30 = 75
        // UTG bets 40 → runningPot = 115
        // CO calls 40: fraction = 40/115
        assertClose(40.0 / 115.0, result.flopActions[1].amountAsPotFraction, "Flop call fraction")
    }

    @Test
    fun `ante affects starting pot`() {
        // ante=2, 6 players → starting pot = 5 + 10 + 12 = 27
        // UTG raises to 30: raiseSize = 30-10 = 20, fraction = 20/27
        val state = gameState(phase = GamePhase.PRE_FLOP, ante = 2, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(20.0 / 27.0, result.preflopActions[0].amountAsPotFraction, "Raise with ante")
    }

    @Test
    fun `preflop 3-bet pot fraction`() {
        // Starting pot = 15, betLevel = 10
        // UTG raises to 30: increment=30, runningPot=45, betLevel=30
        // CO raises to 90: raiseSize = 90-30 = 60, fraction = 60/45 = 1.333
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.RAISE, 90, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(60.0 / 45.0, result.preflopActions[1].amountAsPotFraction, "3-bet fraction")
    }

    // ========== Num Bets Current Street ==========

    @Test
    fun `numBets counts aggressive actions on current street`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop: bet and raise
            record(3, ActionType.RAISE, 40, GamePhase.FLOP),
            record(5, ActionType.RAISE, 120, GamePhase.FLOP),
            record(3, ActionType.CALL, 80, GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(2, result.numBetsCurrentStreet) // bet + raise
    }

    @Test
    fun `numBets zero when all check`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop: all check
            record(3, ActionType.CHECK, phase = GamePhase.FLOP),
            record(5, ActionType.CHECK, phase = GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(0, result.numBetsCurrentStreet)
    }

    // ========== Narrative Formatting ==========

    @Test
    fun `preflop narrative uses raises to`() {
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertTrue(result.preflopNarrative.contains("UTG raises to \$30"), result.preflopNarrative)
        assertTrue(result.preflopNarrative.contains("MP folds"), result.preflopNarrative)
        assertTrue(result.preflopNarrative.contains("CO calls \$30"), result.preflopNarrative)
    }

    @Test
    fun `postflop first bet says bets`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop
            record(3, ActionType.RAISE, 40, GamePhase.FLOP),
            record(5, ActionType.CALL, 40, GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertTrue(result.flopNarrative.contains("UTG bets \$40"), result.flopNarrative)
        assertTrue(result.flopNarrative.contains("CO calls \$40"), result.flopNarrative)
    }

    @Test
    fun `postflop re-raise says raises to`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop: bet then raise
            record(3, ActionType.RAISE, 40, GamePhase.FLOP),
            record(5, ActionType.RAISE, 120, GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertTrue(result.flopNarrative.contains("UTG bets \$40"), result.flopNarrative)
        assertTrue(result.flopNarrative.contains("CO raises to \$120"), result.flopNarrative)
    }

    @Test
    fun `narrative includes You label for player`() {
        // playerIndex = 2 (BB)
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.CALL, 20, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 2)
        assertTrue(result.preflopNarrative.contains("BB (You) calls \$20"), result.preflopNarrative)
        assertFalse(result.preflopNarrative.contains("UTG (You)"), result.preflopNarrative)
    }

    @Test
    fun `narrative all-in aggressive`() {
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.ALL_IN, 200, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertTrue(result.preflopNarrative.contains("CO goes all-in \$200"), result.preflopNarrative)
    }

    @Test
    fun `narrative all-in non-aggressive`() {
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 100, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.ALL_IN, 40, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertTrue(result.preflopNarrative.contains("BB calls all-in \$40"), result.preflopNarrative)
    }

    @Test
    fun `narrative check`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop
            record(3, ActionType.CHECK, phase = GamePhase.FLOP),
            record(5, ActionType.CHECK, phase = GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals("UTG checks, CO checks", result.flopNarrative)
    }

    // ========== Street Action Grouping ==========

    @Test
    fun `actions grouped by street`() {
        val state = gameState(phase = GamePhase.TURN, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop
            record(3, ActionType.RAISE, 40, GamePhase.FLOP),
            record(5, ActionType.CALL, 40, GamePhase.FLOP),
            // Turn
            record(3, ActionType.RAISE, 80, GamePhase.TURN),
            record(5, ActionType.CALL, 80, GamePhase.TURN)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(6, result.preflopActions.size)
        assertEquals(2, result.flopActions.size)
        assertEquals(2, result.turnActions.size)
        assertEquals(0, result.riverActions.size)
    }

    @Test
    fun `empty streets return empty lists and narratives`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertTrue(result.flopActions.isEmpty())
        assertEquals("", result.flopNarrative)
        assertTrue(result.turnActions.isEmpty())
        assertTrue(result.riverActions.isEmpty())
    }

    // ========== Current Street ==========

    @Test
    fun `current street maps to correct phase`() {
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            // Flop
            record(3, ActionType.RAISE, 40, GamePhase.FLOP),
            record(5, ActionType.CALL, 40, GamePhase.FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(result.flopAggressor, result.currentStreetAggressor)
        assertEquals(result.flopNarrative, result.currentStreetNarrative)
    }

    // ========== Position Mapping ==========

    @Test
    fun `positions are correctly mapped`() {
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),  // UTG
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),  // MP
            record(5, ActionType.CALL, 30, GamePhase.PRE_FLOP),  // CO
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),  // BTN
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),  // SB
            record(2, ActionType.CALL, 20, GamePhase.PRE_FLOP)   // BB
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertEquals(Position.UTG, result.preflopActions[0].position)
        assertEquals(Position.MP, result.preflopActions[1].position)
        assertEquals(Position.CO, result.preflopActions[2].position)
        assertEquals(Position.BTN, result.preflopActions[3].position)
        assertEquals(Position.SB, result.preflopActions[4].position)
        assertEquals(Position.BB, result.preflopActions[5].position)
    }

    // ========== Edge Cases ==========

    @Test
    fun `preflop re-raise pot tracking is accurate`() {
        // Starting pot = 15, betLevel = 10
        // UTG raises to 30: increment=30 (0→30), runningPot=45, betLevel=30
        // CO 3-bets to 90: increment=90 (0→90), runningPot=135, betLevel=90
        // UTG 4-bets to 250: increment=220 (30→250), runningPot=355, betLevel=250
        // CO calls 160 (90→250): increment=160, runningPot=515
        val state = gameState(phase = GamePhase.FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.RAISE, 90, GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(3, ActionType.RAISE, 250, GamePhase.PRE_FLOP),
            record(5, ActionType.CALL, 160, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        // 4-bet: raiseSize = 250 - 90 = 160, runningPot before = 135
        assertClose(160.0 / 135.0, result.preflopActions[6].amountAsPotFraction, "4-bet fraction")
        // Call: 160 / 355
        assertClose(160.0 / 355.0, result.preflopActions[7].amountAsPotFraction, "Call after 4-bet")
    }

    @Test
    fun `SB call tracks blind correctly`() {
        // SB(1) has 5 in blind, calls raise to 30 → call amount = 25
        // Starting pot=15, UTG raises to 30 → runningPot = 45
        // SB calls 25: fraction = 25/45
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.CALL, 25, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(25.0 / 45.0, result.preflopActions[4].amountAsPotFraction, "SB call fraction")
    }

    @Test
    fun `all-in aggressive pot fraction`() {
        // UTG raises to 30, CO goes all-in 200 (0→200)
        // raiseSize = 200-30 = 170, runningPot before = 45
        // fraction = 170/45 = 3.777
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 30, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.ALL_IN, 200, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(170.0 / 45.0, result.preflopActions[2].amountAsPotFraction, "All-in aggressive fraction")
    }

    @Test
    fun `all-in non-aggressive pot fraction`() {
        // UTG raises to 100 → runningPot = 15+100 = 115
        // BB goes all-in 40 (had 10 blind, total=50 < 100) → not aggressive
        // fraction = 40/115
        val state = gameState(phase = GamePhase.PRE_FLOP, actions = listOf(
            record(3, ActionType.RAISE, 100, GamePhase.PRE_FLOP),
            record(4, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(5, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(0, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(1, ActionType.FOLD, phase = GamePhase.PRE_FLOP),
            record(2, ActionType.ALL_IN, 40, GamePhase.PRE_FLOP)
        ))
        val result = ActionAnalyzer.analyze(state, playerIndex = 0)
        assertClose(40.0 / 115.0, result.preflopActions[5].amountAsPotFraction, "All-in non-aggressive fraction")
    }
}
