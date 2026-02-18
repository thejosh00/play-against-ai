package com.pokerai.ai

import com.pokerai.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameContextTest {

    private fun makeState(ante: Int = 0): GameState {
        return GameState(
            players = emptyList(),
            bigBlind = 10,
            smallBlind = 5,
            ante = ante
        )
    }

    @Test
    fun `null config returns NEUTRAL`() {
        val ctx = GameContext.from(null, makeState())
        assertEquals(GameContext.NEUTRAL, ctx)
    }

    @Test
    fun `NEUTRAL has expected defaults`() {
        val ctx = GameContext.NEUTRAL
        assertFalse(ctx.isTournament)
        assertEquals(Difficulty.MEDIUM, ctx.difficulty)
        assertFalse(ctx.antesActive)
        assertFalse(ctx.rakeEnabled)
        assertNull(ctx.tournamentStage)
    }

    @Test
    fun `cash game with rake enabled`() {
        val config = GameConfig.CashGame(stakes = CashStakes.ONE_TWO, rakeEnabled = true)
        val ctx = GameContext.from(config, makeState())
        assertFalse(ctx.isTournament)
        assertEquals(Difficulty.LOW, ctx.difficulty)
        assertFalse(ctx.antesActive)
        assertTrue(ctx.rakeEnabled)
        assertNull(ctx.tournamentStage)
    }

    @Test
    fun `cash game without rake`() {
        val config = GameConfig.CashGame(stakes = CashStakes.FIVE_TEN, rakeEnabled = false)
        val ctx = GameContext.from(config, makeState())
        assertFalse(ctx.isTournament)
        assertEquals(Difficulty.HIGH, ctx.difficulty)
        assertFalse(ctx.antesActive)
        assertFalse(ctx.rakeEnabled)
        assertNull(ctx.tournamentStage)
    }

    @Test
    fun `tournament with antes active in state`() {
        val config = GameConfig.Tournament(
            buyin = TournamentBuyin.FIFTEEN_HUNDRED,
            playerCount = 180,
            antesEnabled = true
        )
        val ctx = GameContext.from(config, makeState(ante = 10))
        assertTrue(ctx.isTournament)
        assertEquals(Difficulty.HIGH, ctx.difficulty)
        assertTrue(ctx.antesActive)
        assertFalse(ctx.rakeEnabled)
        assertNull(ctx.tournamentStage)
    }

    @Test
    fun `tournament with antes enabled but not yet active`() {
        val config = GameConfig.Tournament(
            buyin = TournamentBuyin.FIFTEEN_HUNDRED,
            playerCount = 180,
            antesEnabled = true
        )
        val ctx = GameContext.from(config, makeState(ante = 0))
        assertTrue(ctx.isTournament)
        assertFalse(ctx.antesActive)
    }

    @Test
    fun `tournament without antes`() {
        val config = GameConfig.Tournament(
            buyin = TournamentBuyin.HUNDRED,
            playerCount = 45,
            antesEnabled = false
        )
        val ctx = GameContext.from(config, makeState())
        assertTrue(ctx.isTournament)
        assertEquals(Difficulty.LOW, ctx.difficulty)
        assertFalse(ctx.antesActive)
        assertFalse(ctx.rakeEnabled)
        assertNull(ctx.tournamentStage)
    }

    @Test
    fun `tournament with TournamentState derives correct stage`() {
        val config = GameConfig.Tournament(
            buyin = TournamentBuyin.FIVE_HUNDRED,
            playerCount = 45,
            antesEnabled = false
        )
        val ts = TournamentState.create(config).also { it.remainingPlayers = 10 }
        val ctx = GameContext.from(config, makeState(), ts)
        assertTrue(ctx.isTournament)
        assertEquals(TournamentStage.BUBBLE, ctx.tournamentStage)
    }

    @Test
    fun `tournament without TournamentState has null stage`() {
        val config = GameConfig.Tournament(
            buyin = TournamentBuyin.FIVE_HUNDRED,
            playerCount = 45,
            antesEnabled = false
        )
        val ctx = GameContext.from(config, makeState())
        assertTrue(ctx.isTournament)
        assertNull(ctx.tournamentStage)
    }
}
