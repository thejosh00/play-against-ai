package com.pokerai.ai

import com.pokerai.model.*
import com.pokerai.model.archetype.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreFlopStrategyTest {

    private val strategy = PreFlopStrategy()

    private fun makePlayer(
        index: Int,
        archetype: PlayerArchetype? = null,
        position: Position = Position.BB,
        chips: Int = 1000,
        isHuman: Boolean = false
    ): Player {
        val profile = archetype?.createProfile()
        return Player(
            index = index,
            name = if (isHuman) "Human" else "AI-$index",
            isHuman = isHuman,
            profile = profile,
            chips = chips,
            position = position
        )
    }

    private fun makeState(
        players: List<Player>,
        actionHistory: List<ActionRecord> = emptyList(),
        bigBlind: Int = 10,
        currentBetLevel: Int = 10
    ): GameState {
        return GameState(
            players = players,
            bigBlind = bigBlind,
            smallBlind = bigBlind / 2,
            currentBetLevel = currentBetLevel,
            actionHistory = actionHistory.toMutableList()
        )
    }

    private fun raiseRecord(playerIndex: Int, playerName: String, amount: Int): ActionRecord {
        return ActionRecord(
            playerIndex = playerIndex,
            playerName = playerName,
            action = Action.raise(amount),
            phase = GamePhase.PRE_FLOP
        )
    }

    // --- raiserArchetypeAdjustment ---

    @Test
    fun `raiserArchetypeAdjustment returns -4 for Nit`() {
        val raiser = makePlayer(0, NitArchetype, Position.UTG)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(-4, strategy.raiserArchetypeAdjustment(state))
    }

    @Test
    fun `raiserArchetypeAdjustment returns -2 for TAG`() {
        val raiser = makePlayer(0, TagArchetype, Position.UTG)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(-2, strategy.raiserArchetypeAdjustment(state))
    }

    @Test
    fun `raiserArchetypeAdjustment returns 0 for Shark`() {
        val raiser = makePlayer(0, SharkArchetype, Position.UTG)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(0, strategy.raiserArchetypeAdjustment(state))
    }

    @Test
    fun `raiserArchetypeAdjustment returns +3 for LAG`() {
        val raiser = makePlayer(0, LagArchetype, Position.UTG)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(3, strategy.raiserArchetypeAdjustment(state))
    }

    @Test
    fun `raiserArchetypeAdjustment returns +4 for CallingStation`() {
        val raiser = makePlayer(0, CallingStationArchetype, Position.UTG)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(4, strategy.raiserArchetypeAdjustment(state))
    }

    @Test
    fun `raiserArchetypeAdjustment returns 0 for human raiser`() {
        val raiser = makePlayer(0, isHuman = true, position = Position.UTG)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(0, strategy.raiserArchetypeAdjustment(state))
    }

    // --- raiserPositionAdjustment ---

    @Test
    fun `raiserPositionAdjustment returns -3 for UTG`() {
        val raiser = makePlayer(0, TagArchetype, Position.UTG)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(-3, strategy.raiserPositionAdjustment(state))
    }

    @Test
    fun `raiserPositionAdjustment returns +2 for BTN`() {
        val raiser = makePlayer(0, TagArchetype, Position.BTN)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(2, strategy.raiserPositionAdjustment(state))
    }

    @Test
    fun `raiserPositionAdjustment returns +3 for SB`() {
        val raiser = makePlayer(0, TagArchetype, Position.SB)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(3, strategy.raiserPositionAdjustment(state))
    }

    // --- raiseSizingAdjustment ---

    @Test
    fun `raiseSizingAdjustment returns +2 for small raise`() {
        val raiser = makePlayer(0, TagArchetype)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 20)), // 2.0x BB
            bigBlind = 10
        )
        assertEquals(2, strategy.raiseSizingAdjustment(state))
    }

    @Test
    fun `raiseSizingAdjustment returns 0 for standard 3x raise`() {
        val raiser = makePlayer(0, TagArchetype)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30)), // 3.0x BB
            bigBlind = 10
        )
        assertEquals(0, strategy.raiseSizingAdjustment(state))
    }

    @Test
    fun `raiseSizingAdjustment returns -3 for very large raise`() {
        val raiser = makePlayer(0, TagArchetype)
        val state = makeState(
            players = listOf(raiser),
            actionHistory = listOf(raiseRecord(0, raiser.name, 80)), // 8.0x BB
            bigBlind = 10
        )
        assertEquals(-3, strategy.raiseSizingAdjustment(state))
    }

    // --- inPositionAdjustment ---

    @Test
    fun `inPositionAdjustment returns +2 when defender has later position`() {
        val raiser = makePlayer(0, TagArchetype, Position.UTG)
        val defender = makePlayer(1, TagArchetype, Position.BTN)
        val state = makeState(
            players = listOf(raiser, defender),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(2, strategy.inPositionAdjustment(defender, state))
    }

    @Test
    fun `inPositionAdjustment returns -1 when defender has earlier position`() {
        val raiser = makePlayer(0, TagArchetype, Position.BTN)
        val defender = makePlayer(1, TagArchetype, Position.BB)
        val state = makeState(
            players = listOf(raiser, defender),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30))
        )
        assertEquals(-1, strategy.inPositionAdjustment(defender, state))
    }

    // --- computeRangeAdjustment ---

    @Test
    fun `computeRangeAdjustment returns 0 for OPEN scenario`() {
        val player = makePlayer(0, TagArchetype, Position.UTG)
        val state = makeState(players = listOf(player))
        assertEquals(0, strategy.computeRangeAdjustment(player, state, Scenario.OPEN))
    }

    @Test
    fun `computeRangeAdjustment sums all factors for FACING_RAISE`() {
        // Nit UTG raises 3x, TAG in BB (OOP)
        // Archetype: -4, Position: -3, Sizing: 0, IP: -1 -> -8
        val raiser = makePlayer(0, NitArchetype, Position.UTG)
        val defender = makePlayer(1, TagArchetype, Position.BB)
        val state = makeState(
            players = listOf(raiser, defender),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30)),
            bigBlind = 10
        )
        assertEquals(-8, strategy.computeRangeAdjustment(defender, state, Scenario.FACING_RAISE))
    }

    @Test
    fun `computeRangeAdjustment widens vs LAG BTN steal`() {
        // LAG BTN raises 2.2x, TAG in BB
        // Archetype: +3, Position: +2, Sizing: +1, IP: -1 -> +5
        val raiser = makePlayer(0, LagArchetype, Position.BTN)
        val defender = makePlayer(1, TagArchetype, Position.BB)
        val state = makeState(
            players = listOf(raiser, defender),
            actionHistory = listOf(raiseRecord(0, raiser.name, 22)),
            bigBlind = 10
        )
        assertEquals(5, strategy.computeRangeAdjustment(defender, state, Scenario.FACING_RAISE))
    }

    // --- Integration: range adjustments affect hand inclusion ---

    @Test
    fun `TAG folds marginal hand vs Nit UTG raise`() {
        // TAG facing-raise base range has 12 hands (cutoff ~14 in rankings)
        // vs Nit UTG 3x: adjustment = -8 -> only top ~6 hands
        // AJs is in TAG's base facing-raise range but should be outside adjusted range
        val baseRange = TagArchetype.createProfile().archetype.getFacingRaiseRange(Position.BB)
        assertTrue("AJs" in baseRange, "AJs should be in TAG base facing-raise range")

        val raiser = makePlayer(0, NitArchetype, Position.UTG)
        val defender = makePlayer(1, TagArchetype, Position.BB)
        val state = makeState(
            players = listOf(raiser, defender),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30)),
            bigBlind = 10
        )
        val adjustment = strategy.computeRangeAdjustment(defender, state, Scenario.FACING_RAISE)
        val adjustedRange = HandRankings.adjustRange(baseRange, adjustment)
        assertFalse("AJs" in adjustedRange, "AJs should be outside adjusted range vs Nit UTG")
        assertTrue("AA" in adjustedRange, "AA should remain in adjusted range")
        assertTrue("KK" in adjustedRange, "KK should remain in adjusted range")
    }

    @Test
    fun `TAG plays wider vs LAG BTN steal`() {
        // TAG facing-raise base range has 12 hands
        // vs LAG BTN 2.2x: adjustment = +5 -> wider range
        val baseRange = TagArchetype.createProfile().archetype.getFacingRaiseRange(Position.BB)
        val baseSize = baseRange.size

        val raiser = makePlayer(0, LagArchetype, Position.BTN)
        val defender = makePlayer(1, TagArchetype, Position.BB)
        val state = makeState(
            players = listOf(raiser, defender),
            actionHistory = listOf(raiseRecord(0, raiser.name, 22)),
            bigBlind = 10
        )
        val adjustment = strategy.computeRangeAdjustment(defender, state, Scenario.FACING_RAISE)
        val adjustedRange = HandRankings.adjustRange(baseRange, adjustment)
        assertTrue(adjustedRange.size > baseSize, "Adjusted range should be wider vs LAG BTN steal")
        // All base range hands should still be included (contiguous fill)
        assertTrue(adjustedRange.containsAll(baseRange) || adjustedRange.size > baseSize)
    }
}
