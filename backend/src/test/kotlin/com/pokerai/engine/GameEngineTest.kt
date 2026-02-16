package com.pokerai.engine

import com.pokerai.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameEngineTest {

    @Test
    fun `create game has 6 players`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        assertEquals(6, state.players.size)
        assertTrue(state.players[0].isHuman)
        assertTrue(state.players.drop(1).all { !it.isHuman })
        assertTrue(state.players.drop(1).all { it.profile != null })
    }

    @Test
    fun `start new hand deals cards and posts blinds`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)

        assertEquals(GamePhase.PRE_FLOP, state.phase)
        assertEquals(1, state.handNumber)

        // All players have hole cards
        for (player in state.players) {
            assertNotNull(player.holeCards)
        }

        // Blinds posted: pot should be SB + BB = 15
        assertEquals(15, state.pot)

        // Community cards empty
        assertTrue(state.communityCards.isEmpty())
    }

    @Test
    fun `fold action marks player as folded`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)

        val playerIdx = state.currentPlayerIndex
        GameEngine.applyAction(state, playerIdx, Action.fold())

        assertTrue(state.players[playerIdx].isFolded)
    }

    @Test
    fun `call action deducts chips correctly`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)

        val playerIdx = state.currentPlayerIndex
        val player = state.players[playerIdx]
        val chipsBefore = player.chips
        val potBefore = state.pot
        val callAmount = GameEngine.getCallAmount(state, playerIdx)

        GameEngine.applyAction(state, playerIdx, Action.call(callAmount))

        assertEquals(chipsBefore - callAmount, player.chips)
        assertEquals(potBefore + callAmount, state.pot)
    }

    @Test
    fun `raise action updates bet level`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)

        val playerIdx = state.currentPlayerIndex
        val raiseAmount = 30

        GameEngine.applyAction(state, playerIdx, Action.raise(raiseAmount))

        assertEquals(raiseAmount, state.currentBetLevel)
        assertEquals(playerIdx, state.lastRaiserIndex)
    }

    @Test
    fun `deal community cards for flop`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)
        state.phase = GamePhase.PRE_FLOP

        GameEngine.dealCommunity(state)

        assertEquals(GamePhase.FLOP, state.phase)
        assertEquals(3, state.communityCards.size)
    }

    @Test
    fun `deal community cards for turn`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)
        state.phase = GamePhase.PRE_FLOP
        GameEngine.dealCommunity(state) // flop

        GameEngine.dealCommunity(state) // turn

        assertEquals(GamePhase.TURN, state.phase)
        assertEquals(4, state.communityCards.size)
    }

    @Test
    fun `deal community cards for river`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)
        state.phase = GamePhase.PRE_FLOP
        GameEngine.dealCommunity(state) // flop
        GameEngine.dealCommunity(state) // turn

        GameEngine.dealCommunity(state) // river

        assertEquals(GamePhase.RIVER, state.phase)
        assertEquals(5, state.communityCards.size)
    }

    @Test
    fun `hand complete when all but one fold`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)

        // Fold everyone except player 0
        for (i in 1 until state.players.size) {
            state.players[i].isFolded = true
        }

        assertTrue(GameEngine.isHandComplete(state))
    }

    @Test
    fun `valid actions include fold and call when facing a bet`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)

        val playerIdx = state.currentPlayerIndex
        val actions = GameEngine.getValidActions(state, playerIdx)

        // Pre-flop, facing BB, should have fold, call, raise, all-in
        assertTrue(ActionType.FOLD in actions)
        assertTrue(ActionType.CALL in actions)
        assertTrue(ActionType.RAISE in actions)
        assertTrue(ActionType.ALL_IN in actions)
    }

    @Test
    fun `first to act pre-flop is UTG`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        state.dealerIndex = 0
        GameEngine.startNewHand(state)

        // With dealer=0 in 6-player game: SB=seat1, BB=seat2, UTG=seat3, MP=seat4, CO=seat5, BTN=seat0
        val firstToAct = state.currentPlayerIndex
        assertEquals(Position.UTG, state.players[firstToAct].position)

        // After UTG acts, getNextToAct should return MP (seat 4)
        GameEngine.applyAction(state, firstToAct, Action.call(10))
        val secondToAct = GameEngine.getNextToAct(state)
        assertNotNull(secondToAct)
        assertEquals(Position.MP, state.players[secondToAct!!].position)
    }

    @Test
    fun `create game with config tableSize 9 has 9 players`() {
        val config = GameConfig.CashGame(CashStakes.TWO_FIVE, tableSize = 9)
        val state = GameEngine.createGame("Player", config)
        assertEquals(9, state.players.size)
        assertTrue(state.players[0].isHuman)
        assertTrue(state.players.drop(1).all { !it.isHuman })
        assertTrue(state.players.drop(1).all { it.profile != null })
    }

    @Test
    fun `create game with config tableSize 6 has 6 players`() {
        val config = GameConfig.CashGame(CashStakes.TWO_FIVE, tableSize = 6)
        val state = GameEngine.createGame("Player", config)
        assertEquals(6, state.players.size)
    }

    @Test
    fun `9 player game assigns all positions correctly`() {
        val config = GameConfig.CashGame(CashStakes.TWO_FIVE, tableSize = 9)
        val state = GameEngine.createGame("Player", config)
        state.dealerIndex = 0
        GameEngine.startNewHand(state)

        val positions = state.players.map { it.position }
        assertEquals(9, positions.toSet().size, "All 9 positions should be unique")
        assertTrue(Position.UTG1 in positions)
        assertTrue(Position.LJ in positions)
        assertTrue(Position.HJ in positions)
    }

    @Test
    fun `advance dealer moves to next active player`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        val originalDealer = state.dealerIndex

        GameEngine.advanceDealer(state)

        val expected = (originalDealer + 1) % state.players.size
        assertEquals(expected, state.dealerIndex)
    }

    @Test
    fun `showdown evaluates and awards pot`() {
        val state = GameEngine.createGame("Player", 1000, 5, 10)
        GameEngine.startNewHand(state)

        // Fold everyone except players 0 and 1
        for (i in 2 until state.players.size) {
            state.players[i].isFolded = true
        }

        // Set up community cards
        state.communityCards.clear()
        state.communityCards.addAll(
            listOf(
                Card.fromNotation("Ah"),
                Card.fromNotation("Kh"),
                Card.fromNotation("Qh"),
                Card.fromNotation("2d"),
                Card.fromNotation("3c")
            )
        )

        val potBefore = state.pot
        val results = GameEngine.evaluateShowdown(state)

        assertEquals(GamePhase.HAND_COMPLETE, state.phase)
        assertTrue(results.isNotEmpty())
        assertEquals(0, state.pot)
        // The total awarded should equal the original pot
        assertEquals(potBefore, results.sumOf { it.second })
    }
}
