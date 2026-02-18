package com.pokerai.ai

import com.pokerai.model.Difficulty
import com.pokerai.model.archetype.*
import kotlin.test.Test
import kotlin.test.assertEquals

class GameContextAdjustmentTest {

    private val archetypes = listOf(
        SharkArchetype, TagArchetype, LagArchetype, NitArchetype, CallingStationArchetype
    )

    private fun ctx(
        tournamentStage: TournamentStage? = null,
        difficulty: Difficulty = Difficulty.MEDIUM,
        antesActive: Boolean = false,
        rakeEnabled: Boolean = false
    ) = GameContext(
        isTournament = tournamentStage != null,
        difficulty = difficulty,
        antesActive = antesActive,
        rakeEnabled = rakeEnabled,
        tournamentStage = tournamentStage
    )

    // --- NEUTRAL returns 0 for all ---

    @Test
    fun `all archetypes return 0 for NEUTRAL context`() {
        for (archetype in archetypes) {
            for (scenario in Scenario.entries) {
                assertEquals(
                    0, archetype.getGameContextAdjustment(GameContext.NEUTRAL, scenario),
                    "${archetype.displayName} should return 0 for NEUTRAL in $scenario"
                )
            }
        }
    }

    // --- Shark ---

    @Test
    fun `Shark widens +3 with antes`() {
        assertEquals(3, SharkArchetype.getGameContextAdjustment(ctx(antesActive = true), Scenario.OPEN))
    }

    @Test
    fun `Shark tightens -1 with rake`() {
        assertEquals(-1, SharkArchetype.getGameContextAdjustment(ctx(rakeEnabled = true), Scenario.OPEN))
    }

    @Test
    fun `Shark unaffected by difficulty`() {
        assertEquals(0, SharkArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.LOW), Scenario.OPEN))
        assertEquals(0, SharkArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.HIGH), Scenario.OPEN))
    }

    @Test
    fun `Shark EARLY stage adjusts 0`() {
        assertEquals(0, SharkArchetype.getGameContextAdjustment(ctx(TournamentStage.EARLY), Scenario.OPEN))
    }

    @Test
    fun `Shark MIDDLE stage adjusts -1`() {
        assertEquals(-1, SharkArchetype.getGameContextAdjustment(ctx(TournamentStage.MIDDLE), Scenario.OPEN))
    }

    @Test
    fun `Shark BUBBLE stage adjusts +2`() {
        assertEquals(2, SharkArchetype.getGameContextAdjustment(ctx(TournamentStage.BUBBLE), Scenario.OPEN))
    }

    @Test
    fun `Shark FINAL_TABLE stage adjusts 0`() {
        assertEquals(0, SharkArchetype.getGameContextAdjustment(ctx(TournamentStage.FINAL_TABLE), Scenario.OPEN))
    }

    @Test
    fun `Shark HEADS_UP stage adjusts +3`() {
        assertEquals(3, SharkArchetype.getGameContextAdjustment(ctx(TournamentStage.HEADS_UP), Scenario.OPEN))
    }

    @Test
    fun `Shark BUBBLE with antes nets +5`() {
        assertEquals(5, SharkArchetype.getGameContextAdjustment(ctx(TournamentStage.BUBBLE, antesActive = true), Scenario.OPEN))
    }

    // --- TAG ---

    @Test
    fun `TAG widens +2 with antes`() {
        assertEquals(2, TagArchetype.getGameContextAdjustment(ctx(antesActive = true), Scenario.OPEN))
    }

    @Test
    fun `TAG tightens -1 with rake`() {
        assertEquals(-1, TagArchetype.getGameContextAdjustment(ctx(rakeEnabled = true), Scenario.OPEN))
    }

    @Test
    fun `TAG unaffected by difficulty`() {
        assertEquals(0, TagArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.LOW), Scenario.OPEN))
        assertEquals(0, TagArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.HIGH), Scenario.OPEN))
    }

    @Test
    fun `TAG EARLY stage adjusts 0`() {
        assertEquals(0, TagArchetype.getGameContextAdjustment(ctx(TournamentStage.EARLY), Scenario.OPEN))
    }

    @Test
    fun `TAG MIDDLE stage adjusts -1`() {
        assertEquals(-1, TagArchetype.getGameContextAdjustment(ctx(TournamentStage.MIDDLE), Scenario.OPEN))
    }

    @Test
    fun `TAG BUBBLE stage adjusts -2`() {
        assertEquals(-2, TagArchetype.getGameContextAdjustment(ctx(TournamentStage.BUBBLE), Scenario.OPEN))
    }

    @Test
    fun `TAG FINAL_TABLE stage adjusts -1`() {
        assertEquals(-1, TagArchetype.getGameContextAdjustment(ctx(TournamentStage.FINAL_TABLE), Scenario.OPEN))
    }

    @Test
    fun `TAG HEADS_UP stage adjusts +2`() {
        assertEquals(2, TagArchetype.getGameContextAdjustment(ctx(TournamentStage.HEADS_UP), Scenario.OPEN))
    }

    // --- LAG ---

    @Test
    fun `LAG widens +2 with antes`() {
        assertEquals(2, LagArchetype.getGameContextAdjustment(ctx(antesActive = true), Scenario.OPEN))
    }

    @Test
    fun `LAG widens +2 at LOW difficulty`() {
        assertEquals(2, LagArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.LOW), Scenario.OPEN))
    }

    @Test
    fun `LAG tightens -1 at HIGH difficulty`() {
        assertEquals(-1, LagArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.HIGH), Scenario.OPEN))
    }

    @Test
    fun `LAG ignores rake`() {
        assertEquals(0, LagArchetype.getGameContextAdjustment(ctx(rakeEnabled = true), Scenario.OPEN))
    }

    @Test
    fun `LAG antes plus LOW difficulty nets +4`() {
        assertEquals(4, LagArchetype.getGameContextAdjustment(ctx(antesActive = true, difficulty = Difficulty.LOW), Scenario.OPEN))
    }

    @Test
    fun `LAG EARLY stage adjusts 0`() {
        assertEquals(0, LagArchetype.getGameContextAdjustment(ctx(TournamentStage.EARLY), Scenario.OPEN))
    }

    @Test
    fun `LAG MIDDLE stage adjusts 0`() {
        assertEquals(0, LagArchetype.getGameContextAdjustment(ctx(TournamentStage.MIDDLE), Scenario.OPEN))
    }

    @Test
    fun `LAG BUBBLE stage adjusts -1`() {
        assertEquals(-1, LagArchetype.getGameContextAdjustment(ctx(TournamentStage.BUBBLE), Scenario.OPEN))
    }

    @Test
    fun `LAG FINAL_TABLE stage adjusts +1`() {
        assertEquals(1, LagArchetype.getGameContextAdjustment(ctx(TournamentStage.FINAL_TABLE), Scenario.OPEN))
    }

    @Test
    fun `LAG HEADS_UP stage adjusts +4`() {
        assertEquals(4, LagArchetype.getGameContextAdjustment(ctx(TournamentStage.HEADS_UP), Scenario.OPEN))
    }

    // --- Nit ---

    @Test
    fun `Nit tightens -1 with rake`() {
        assertEquals(-1, NitArchetype.getGameContextAdjustment(ctx(rakeEnabled = true), Scenario.OPEN))
    }

    @Test
    fun `Nit tightens -1 at LOW difficulty`() {
        assertEquals(-1, NitArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.LOW), Scenario.OPEN))
    }

    @Test
    fun `Nit widens +1 at HIGH difficulty`() {
        assertEquals(1, NitArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.HIGH), Scenario.OPEN))
    }

    @Test
    fun `Nit ignores antes`() {
        assertEquals(0, NitArchetype.getGameContextAdjustment(ctx(antesActive = true), Scenario.OPEN))
    }

    @Test
    fun `Nit EARLY stage adjusts 0`() {
        assertEquals(0, NitArchetype.getGameContextAdjustment(ctx(TournamentStage.EARLY), Scenario.OPEN))
    }

    @Test
    fun `Nit MIDDLE stage adjusts -2`() {
        assertEquals(-2, NitArchetype.getGameContextAdjustment(ctx(TournamentStage.MIDDLE), Scenario.OPEN))
    }

    @Test
    fun `Nit BUBBLE stage adjusts -3`() {
        assertEquals(-3, NitArchetype.getGameContextAdjustment(ctx(TournamentStage.BUBBLE), Scenario.OPEN))
    }

    @Test
    fun `Nit FINAL_TABLE stage adjusts -2`() {
        assertEquals(-2, NitArchetype.getGameContextAdjustment(ctx(TournamentStage.FINAL_TABLE), Scenario.OPEN))
    }

    @Test
    fun `Nit HEADS_UP stage adjusts +1`() {
        assertEquals(1, NitArchetype.getGameContextAdjustment(ctx(TournamentStage.HEADS_UP), Scenario.OPEN))
    }

    @Test
    fun `Nit BUBBLE at LOW difficulty nets -4`() {
        assertEquals(-4, NitArchetype.getGameContextAdjustment(ctx(TournamentStage.BUBBLE, difficulty = Difficulty.LOW), Scenario.OPEN))
    }

    // --- CallingStation ---

    @Test
    fun `CallingStation widens +2 at LOW difficulty`() {
        assertEquals(2, CallingStationArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.LOW), Scenario.OPEN))
    }

    @Test
    fun `CallingStation tightens -1 at HIGH difficulty`() {
        assertEquals(-1, CallingStationArchetype.getGameContextAdjustment(ctx(difficulty = Difficulty.HIGH), Scenario.OPEN))
    }

    @Test
    fun `CallingStation ignores antes`() {
        assertEquals(0, CallingStationArchetype.getGameContextAdjustment(ctx(antesActive = true), Scenario.OPEN))
    }

    @Test
    fun `CallingStation ignores rake`() {
        assertEquals(0, CallingStationArchetype.getGameContextAdjustment(ctx(rakeEnabled = true), Scenario.OPEN))
    }

    @Test
    fun `CallingStation EARLY stage adjusts 0`() {
        assertEquals(0, CallingStationArchetype.getGameContextAdjustment(ctx(TournamentStage.EARLY), Scenario.OPEN))
    }

    @Test
    fun `CallingStation MIDDLE stage adjusts 0`() {
        assertEquals(0, CallingStationArchetype.getGameContextAdjustment(ctx(TournamentStage.MIDDLE), Scenario.OPEN))
    }

    @Test
    fun `CallingStation BUBBLE stage adjusts 0`() {
        assertEquals(0, CallingStationArchetype.getGameContextAdjustment(ctx(TournamentStage.BUBBLE), Scenario.OPEN))
    }

    @Test
    fun `CallingStation FINAL_TABLE stage adjusts 0`() {
        assertEquals(0, CallingStationArchetype.getGameContextAdjustment(ctx(TournamentStage.FINAL_TABLE), Scenario.OPEN))
    }

    @Test
    fun `CallingStation HEADS_UP stage adjusts +2`() {
        assertEquals(2, CallingStationArchetype.getGameContextAdjustment(ctx(TournamentStage.HEADS_UP), Scenario.OPEN))
    }

    // --- null stage returns 0 for all archetypes ---

    @Test
    fun `null tournament stage returns 0 stage adjustment for all archetypes`() {
        val context = ctx()
        for (archetype in archetypes) {
            assertEquals(
                0, archetype.getGameContextAdjustment(context, Scenario.OPEN),
                "${archetype.displayName} should return 0 for null tournament stage"
            )
        }
    }
}
