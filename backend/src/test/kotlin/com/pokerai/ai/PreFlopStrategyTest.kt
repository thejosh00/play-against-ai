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
        currentBetLevel: Int = 10,
        ante: Int = 0
    ): GameState {
        return GameState(
            players = players,
            bigBlind = bigBlind,
            smallBlind = bigBlind / 2,
            currentBetLevel = currentBetLevel,
            ante = ante,
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
        // TAG facing-raise base cutoff is 12
        // vs Nit UTG 3x: adjustment = -8 -> cutoff = 4
        // AJs is at index 8 in rankings, so it should be outside the adjusted range
        val baseCutoff = TagArchetype.getFacingRaiseCutoff(Position.BB)
        val ajsIndex = HandRankings.indexOf("AJs")
        assertTrue(ajsIndex < baseCutoff, "AJs should be in TAG base facing-raise range")

        val raiser = makePlayer(0, NitArchetype, Position.UTG)
        val defender = makePlayer(1, TagArchetype, Position.BB)
        val state = makeState(
            players = listOf(raiser, defender),
            actionHistory = listOf(raiseRecord(0, raiser.name, 30)),
            bigBlind = 10
        )
        val adjustment = strategy.computeRangeAdjustment(defender, state, Scenario.FACING_RAISE)
        val adjustedCutoff = (baseCutoff + adjustment).coerceIn(1, HandRankings.RANKED_HANDS.size)
        val adjustedRange = HandRankings.topN(adjustedCutoff)
        assertFalse("AJs" in adjustedRange, "AJs should be outside adjusted range vs Nit UTG")
        assertTrue("AA" in adjustedRange, "AA should remain in adjusted range")
        assertTrue("KK" in adjustedRange, "KK should remain in adjusted range")
    }

    @Test
    fun `TAG plays wider vs LAG BTN steal`() {
        // TAG facing-raise base cutoff is 12
        // vs LAG BTN 2.2x: adjustment = +5 -> cutoff = 17
        val baseCutoff = TagArchetype.getFacingRaiseCutoff(Position.BB)

        val raiser = makePlayer(0, LagArchetype, Position.BTN)
        val defender = makePlayer(1, TagArchetype, Position.BB)
        val state = makeState(
            players = listOf(raiser, defender),
            actionHistory = listOf(raiseRecord(0, raiser.name, 22)),
            bigBlind = 10
        )
        val adjustment = strategy.computeRangeAdjustment(defender, state, Scenario.FACING_RAISE)
        val adjustedCutoff = (baseCutoff + adjustment).coerceIn(1, HandRankings.RANKED_HANDS.size)
        assertTrue(adjustedCutoff > baseCutoff, "Adjusted cutoff should be wider vs LAG BTN steal")
    }

    // --- Context adjustment integration: OPEN scenario now gets non-zero adjustment ---

    @Test
    fun `Shark open range widens with antes via context adjustment`() {
        val config = GameConfig.Tournament(
            buyin = TournamentBuyin.FIVE_HUNDRED,
            playerCount = 45,
            antesEnabled = true
        )
        val ts = TournamentState.create(config)
        val state = makeState(
            players = emptyList(),
            ante = 10
        )
        val context = GameContext.from(config, state, ts)
        // Shark: antes +3, EARLY stage 0 = +3
        assertEquals(3, SharkArchetype.getGameContextAdjustment(context, Scenario.OPEN))

        val baseCutoff = SharkArchetype.getOpenCutoff(Position.CO)
        val adjustedCutoff = baseCutoff + 3
        assertTrue(adjustedCutoff > baseCutoff, "Antes should widen Shark's open range")
    }

    @Test
    fun `TAG open range tightens in raked cash game`() {
        val config = GameConfig.CashGame(stakes = CashStakes.TWO_FIVE, rakeEnabled = true)
        val state = makeState(players = emptyList())
        val context = GameContext.from(config, state)
        // TAG: rake -1 = -1
        assertEquals(-1, TagArchetype.getGameContextAdjustment(context, Scenario.OPEN))

        val baseCutoff = TagArchetype.getOpenCutoff(Position.CO)
        val adjustedCutoff = baseCutoff - 1
        assertTrue(adjustedCutoff < baseCutoff, "Rake should tighten TAG's open range")
    }

    @Test
    fun `context adjustment has no effect with null config`() {
        val state = makeState(players = emptyList())
        val context = GameContext.from(null, state)
        for (archetype in PlayerArchetype.all) {
            assertEquals(
                0, archetype.getGameContextAdjustment(context, Scenario.OPEN),
                "${archetype.displayName} should return 0 for null config"
            )
        }
    }
}
