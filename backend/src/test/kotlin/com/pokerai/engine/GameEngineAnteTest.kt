package com.pokerai.engine

import com.pokerai.model.GameState
import com.pokerai.model.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameEngineAnteTest {

    private fun createTestState(ante: Int, chips: Int = 1000): GameState {
        val players = (0..5).map { i ->
            Player(
                index = i,
                name = if (i == 0) "Player" else "AI$i",
                isHuman = i == 0,
                profile = null,
                chips = chips
            )
        }
        return GameState(
            players = players,
            smallBlind = 5,
            bigBlind = 10,
            ante = ante,
            dealerIndex = 0
        )
    }

    @Test
    fun `ante deducted from all players when ante is greater than 0`() {
        val state = createTestState(ante = 10)
        GameEngine.startNewHand(state)

        // Each player started with 1000, paid 10 ante plus potentially blinds
        // The pot should include antes from all 6 players (60) + SB (5) + BB (10) = 75
        assertEquals(75, state.pot)

        // All non-blind players should have 990 chips (1000 - 10 ante)
        val nonBlindPlayers = state.players.filter {
            it.currentBet == 0 // not SB or BB
        }
        for (player in nonBlindPlayers) {
            assertEquals(990, player.chips, "Player ${player.name} should have 990 chips after ante")
        }
    }

    @Test
    fun `no ante deducted when ante is 0`() {
        val state = createTestState(ante = 0)
        GameEngine.startNewHand(state)

        // Pot should only contain SB + BB = 15
        assertEquals(15, state.pot)

        // Non-blind players should have full chips
        val nonBlindPlayers = state.players.filter {
            it.currentBet == 0
        }
        for (player in nonBlindPlayers) {
            assertEquals(1000, player.chips, "Player ${player.name} should have 1000 chips with no ante")
        }
    }

    @Test
    fun `ante with short stack player goes all-in`() {
        val state = createTestState(ante = 10, chips = 1000)
        // Give one player only 5 chips
        state.players[3].chips = 5

        GameEngine.startNewHand(state)

        // Player 3 should be all-in with 0 chips
        assertEquals(0, state.players[3].chips)
        assertTrue(state.players[3].isAllIn)
    }

    @Test
    fun `pot includes antes plus blinds`() {
        val state = createTestState(ante = 20)
        GameEngine.startNewHand(state)

        // 6 players * 20 ante = 120, plus SB(5) + BB(10) = 135
        assertEquals(135, state.pot)
    }
}
