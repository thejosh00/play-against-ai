package com.pokerai.eval

import com.pokerai.ai.Street
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.LagArchetype
import com.pokerai.model.archetype.NitArchetype
import kotlin.test.*

class ScenarioBuilderTest {

    @Test
    fun `creates basic scenario with correct defaults`() {
        val scenario = ScenarioBuilder.create("test_basic") {
            name = "Basic fold"
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            hand(HandStrengthTier.NOTHING, "seven high")
            board(Street.RIVER, BoardWetness.DRY)
            pot(size = 100, betToCall = 100)
            correct(ActionType.FOLD, weight = 1.0)
        }

        assertEquals("test_basic", scenario.id)
        assertEquals("Basic fold", scenario.name)
        assertEquals(ScenarioCategory.STRATEGIC_CORRECTNESS, scenario.category)
        assertEquals(EvalDifficulty.EASY, scenario.difficulty)

        // Verify DecisionContext fields are populated
        val ctx = scenario.context
        assertEquals(HandStrengthTier.NOTHING, ctx.hand.tier)
        assertEquals("seven high", ctx.hand.madeHandDescription)
        assertEquals(Street.RIVER, ctx.street)
        assertEquals(BoardWetness.DRY, ctx.board.wetness)
        assertEquals(100, ctx.potSize)
        assertEquals(100, ctx.betToCall)
    }

    @Test
    fun `hand tier and description are set correctly`() {
        val scenario = ScenarioBuilder.create("test_hand") {
            name = "Monster hand"
            hand(HandStrengthTier.MONSTER, "set of jacks")
            board(Street.FLOP, BoardWetness.DRY)
            correct(ActionType.RAISE, weight = 1.0)
        }

        assertEquals(HandStrengthTier.MONSTER, scenario.context.hand.tier)
        assertEquals("set of jacks", scenario.context.hand.madeHandDescription)
        assertTrue(scenario.context.hand.madeHand, "MONSTER tier should default to madeHand=true")
    }

    @Test
    fun `board texture fields propagate`() {
        val scenario = ScenarioBuilder.create("test_board") {
            name = "Wet board"
            hand(HandStrengthTier.MEDIUM, "top pair")
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone")
            correct(ActionType.CALL, weight = 1.0)
        }

        assertEquals(BoardWetness.WET, scenario.context.board.wetness)
        assertEquals(Street.FLOP, scenario.context.street)
        assertEquals("wet, two-tone", scenario.context.board.description)
    }

    @Test
    fun `pot geometry calculations are correct`() {
        val scenario = ScenarioBuilder.create("test_pot") {
            name = "Pot odds test"
            hand(HandStrengthTier.NOTHING, "nothing")
            board(Street.RIVER, BoardWetness.DRY)
            pot(size = 200, betToCall = 100, effectiveStack = 500)
            correct(ActionType.FOLD, weight = 1.0)
        }

        val ctx = scenario.context
        // potOdds = 100 / (200 + 100) = 0.333...
        assertEquals(100.0 / 300.0, ctx.potOdds, 0.001)
        // betAsFractionOfPot = 100 / 200 = 0.5
        assertEquals(0.5, ctx.betAsFractionOfPot, 0.001)
        // spr = 500 / 200 = 2.5
        assertEquals(2.5, ctx.spr, 0.001)
        assertEquals(500, ctx.effectiveStack)
    }

    @Test
    fun `correct and wrong actions are stored`() {
        val scenario = ScenarioBuilder.create("test_actions") {
            name = "Actions test"
            hand(HandStrengthTier.NOTHING, "nothing")
            board(Street.RIVER, BoardWetness.DRY)
            pot(size = 100, betToCall = 100)
            correct(ActionType.FOLD, weight = 1.0)
            correct(ActionType.CALL, weight = 0.3)
            wrong(ActionType.RAISE, ActionType.ALL_IN)
        }

        assertEquals(2, scenario.correctActions.size)
        assertEquals(ActionType.FOLD, scenario.correctActions[0].actionType)
        assertEquals(1.0, scenario.correctActions[0].weight)
        assertEquals(ActionType.CALL, scenario.correctActions[1].actionType)
        assertEquals(0.3, scenario.correctActions[1].weight)
        assertTrue(ActionType.RAISE in scenario.wrongActions)
        assertTrue(ActionType.ALL_IN in scenario.wrongActions)
    }

    @Test
    fun `headsUp sets pot type and player count`() {
        val scenario = ScenarioBuilder.create("test_hu") {
            name = "Heads up"
            hand(HandStrengthTier.MEDIUM, "pair")
            board(Street.FLOP, BoardWetness.DRY)
            headsUp()
            correct(ActionType.CHECK, weight = 1.0)
        }

        assertEquals(PotType.HEADS_UP, scenario.context.potType)
        assertEquals(2, scenario.context.actions.numPlayersInPot)
    }

    @Test
    fun `threeWay sets pot type and player count`() {
        val scenario = ScenarioBuilder.create("test_3way") {
            name = "Three way"
            hand(HandStrengthTier.MEDIUM, "pair")
            board(Street.FLOP, BoardWetness.DRY)
            threeWay()
            correct(ActionType.CHECK, weight = 1.0)
        }

        assertEquals(PotType.THREE_WAY, scenario.context.potType)
        assertEquals(3, scenario.context.actions.numPlayersInPot)
    }

    @Test
    fun `multiway sets pot type and player count`() {
        val scenario = ScenarioBuilder.create("test_multi") {
            name = "Multiway"
            hand(HandStrengthTier.MEDIUM, "pair")
            board(Street.FLOP, BoardWetness.DRY)
            multiway()
            correct(ActionType.CHECK, weight = 1.0)
        }

        assertEquals(PotType.MULTIWAY, scenario.context.potType)
        assertEquals(5, scenario.context.actions.numPlayersInPot)
    }

    @Test
    fun `archetype override works`() {
        val scenario = ScenarioBuilder.create("test_archetype") {
            name = "LAG archetype"
            hand(HandStrengthTier.MEDIUM, "pair")
            board(Street.FLOP, BoardWetness.DRY)
            archetype(LagArchetype)
            correct(ActionType.RAISE, weight = 1.0)
        }

        assertEquals(LagArchetype, scenario.context.profile.archetype)
    }

    @Test
    fun `default archetype is Nit`() {
        val scenario = ScenarioBuilder.create("test_default_arch") {
            name = "Default archetype"
            hand(HandStrengthTier.NOTHING, "nothing")
            board(Street.RIVER, BoardWetness.DRY)
            correct(ActionType.FOLD, weight = 1.0)
        }

        assertEquals(NitArchetype, scenario.context.profile.archetype)
    }

    @Test
    fun `suggested bet sizes are calculated from pot`() {
        val scenario = ScenarioBuilder.create("test_sizes") {
            name = "Bet sizes"
            hand(HandStrengthTier.STRONG, "overpair")
            board(Street.FLOP, BoardWetness.DRY)
            pot(size = 300, betToCall = 0)
            correct(ActionType.RAISE, weight = 1.0)
        }

        val sizes = scenario.context.suggestedSizes
        assertEquals(100, sizes.thirdPot)
        assertEquals(150, sizes.halfPot)
        assertEquals(200, sizes.twoThirdsPot)
        assertEquals(300, sizes.fullPot)
        assertEquals(100, sizes.minRaise) // potSize / 3 when no betToCall
    }

    @Test
    fun `facing bet sets facingBet flag`() {
        val scenario = ScenarioBuilder.create("test_facing") {
            name = "Facing bet"
            hand(HandStrengthTier.MEDIUM, "pair")
            board(Street.FLOP, BoardWetness.DRY)
            pot(size = 100, betToCall = 50)
            correct(ActionType.CALL, weight = 1.0)
        }

        assertTrue(scenario.context.facingBet)
    }

    @Test
    fun `no bet means not facing bet`() {
        val scenario = ScenarioBuilder.create("test_no_bet") {
            name = "No bet"
            hand(HandStrengthTier.MEDIUM, "pair")
            board(Street.FLOP, BoardWetness.DRY)
            pot(size = 100, betToCall = 0)
            correct(ActionType.CHECK, weight = 1.0)
        }

        assertFalse(scenario.context.facingBet)
    }

    @Test
    fun `draws are set correctly`() {
        val scenario = ScenarioBuilder.create("test_draws") {
            name = "Draw scenario"
            hand(HandStrengthTier.NOTHING, "no pair, flush draw", madeHand = false)
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false))
            board(Street.FLOP, BoardWetness.WET)
            correct(ActionType.CALL, weight = 1.0)
        }

        assertEquals(1, scenario.context.hand.draws.size)
        assertEquals(DrawType.FLUSH_DRAW, scenario.context.hand.draws[0].type)
        assertEquals(9, scenario.context.hand.draws[0].outs)
        assertEquals(9, scenario.context.hand.totalOuts)
    }
}
