package com.pokerai.engine

import com.pokerai.model.GameState
import com.pokerai.model.Player
import kotlin.test.Test
import kotlin.test.assertEquals

class PotManagerRakeTest {

    private fun stateWithPot(pot: Int): GameState {
        return GameState(
            players = listOf(
                Player(index = 0, name = "P1", isHuman = true, profile = null, chips = 500),
                Player(index = 1, name = "P2", isHuman = false, profile = null, chips = 500),
            ),
            pot = pot
        )
    }

    @Test
    fun `rake is 5 percent of pot`() {
        val state = stateWithPot(200)
        val rake = PotManager.deductRake(state, 0.05, 10)
        assertEquals(10, rake)
        assertEquals(190, state.pot)
    }

    @Test
    fun `rake is capped at configured cap`() {
        val state = stateWithPot(1000)
        val rake = PotManager.deductRake(state, 0.05, 5) // 5% of 1000 = 50, but cap = 5
        assertEquals(5, rake)
        assertEquals(995, state.pot)
    }

    @Test
    fun `rake is 0 for empty pot`() {
        val state = stateWithPot(0)
        val rake = PotManager.deductRake(state, 0.05, 10)
        assertEquals(0, rake)
        assertEquals(0, state.pot)
    }

    @Test
    fun `pot is reduced by rake amount`() {
        val state = stateWithPot(100)
        PotManager.deductRake(state, 0.05, 10)
        assertEquals(95, state.pot) // 5% of 100 = 5
    }

    @Test
    fun `small pot rake rounds correctly`() {
        val state = stateWithPot(10)
        val rake = PotManager.deductRake(state, 0.05, 10) // 5% of 10 = 0.5, rounds to 1
        assertEquals(1, rake)
        assertEquals(9, state.pot)
    }
}
