package com.pokerai.eval

import com.pokerai.ai.*
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.NitArchetype
import kotlin.test.*

class ScorerTest {

    // ── Helpers ──────────────────────────────────────────

    private fun simpleScenario(
        correctActions: List<WeightedAction> = listOf(WeightedAction(ActionType.FOLD, 1.0)),
        wrongActions: Set<ActionType> = setOf(ActionType.RAISE),
        keywords: Set<String> = setOf("weak", "fold")
    ): EvalScenario {
        return ScenarioBuilder.create("test_scenario") {
            name = "Test"
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            hand(HandStrengthTier.NOTHING, "seven high")
            board(Street.RIVER, BoardWetness.DRY)
            pot(size = 100, betToCall = 100)
            this.keywords(*keywords.toTypedArray())
            for (ca in correctActions) correct(ca.actionType, ca.weight, ca.minAmount, ca.maxAmount)
            for (wa in wrongActions) wrong(wa)
        }
    }

    // ── Format Scoring ───────────────────────────────────

    @Test
    fun `format score 3 for Success`() {
        val result = ParseResult.Success(Action.fold(), "reason", "raw")
        assertEquals(3, Scorer.scoreFormat(result))
    }

    @Test
    fun `format score 2 for PartialSuccess with markdown warning`() {
        val result = ParseResult.PartialSuccess(
            Action.fold(), "reason", "raw",
            listOf("Response wrapped in markdown code fences")
        )
        assertEquals(2, Scorer.scoreFormat(result))
    }

    @Test
    fun `format score 1 for PartialSuccess with regex warning`() {
        val result = ParseResult.PartialSuccess(
            Action.fold(), null, "raw",
            listOf("Parsed via regex extraction — no valid JSON found")
        )
        assertEquals(1, Scorer.scoreFormat(result))
    }

    @Test
    fun `format score 0 for Failure`() {
        val result = ParseResult.Failure("raw", "error")
        assertEquals(0, Scorer.scoreFormat(result))
    }

    // ── Strategy Scoring ─────────────────────────────────

    @Test
    fun `strategy score 4 for optimal action`() {
        val scenario = simpleScenario(
            correctActions = listOf(WeightedAction(ActionType.FOLD, 1.0))
        )
        assertEquals(4, Scorer.scoreStrategy(Action.fold(), scenario))
    }

    @Test
    fun `strategy score 3 for acceptable action`() {
        val scenario = simpleScenario(
            correctActions = listOf(
                WeightedAction(ActionType.FOLD, 1.0),
                WeightedAction(ActionType.CALL, 0.6)
            ),
            wrongActions = setOf(ActionType.ALL_IN)
        )
        assertEquals(3, Scorer.scoreStrategy(Action.call(100), scenario))
    }

    @Test
    fun `strategy score 2 for suboptimal acceptable action`() {
        val scenario = simpleScenario(
            correctActions = listOf(
                WeightedAction(ActionType.FOLD, 1.0),
                WeightedAction(ActionType.CHECK, 0.3)
            ),
            wrongActions = emptySet()
        )
        assertEquals(2, Scorer.scoreStrategy(Action.check(), scenario))
    }

    @Test
    fun `strategy score 1 for action not in correct or wrong list`() {
        val scenario = simpleScenario(
            correctActions = listOf(WeightedAction(ActionType.FOLD, 1.0)),
            wrongActions = setOf(ActionType.RAISE)
        )
        assertEquals(1, Scorer.scoreStrategy(Action.check(), scenario))
    }

    @Test
    fun `strategy score 0 for clearly wrong action`() {
        val scenario = simpleScenario(
            correctActions = listOf(WeightedAction(ActionType.FOLD, 1.0)),
            wrongActions = setOf(ActionType.RAISE)
        )
        assertEquals(0, Scorer.scoreStrategy(Action.raise(200), scenario))
    }

    @Test
    fun `strategy score 0 for null action`() {
        val scenario = simpleScenario()
        assertEquals(0, Scorer.scoreStrategy(null, scenario))
    }

    @Test
    fun `strategy score partial credit for correct raise type but wrong amount`() {
        val scenario = simpleScenario(
            correctActions = listOf(WeightedAction(ActionType.RAISE, 1.0, minAmount = 100, maxAmount = 200)),
            wrongActions = setOf(ActionType.FOLD)
        )
        // Amount 500 is outside range — partial credit
        val score = Scorer.scoreStrategy(Action.raise(500), scenario)
        assertTrue(score in 1..3, "Expected partial credit (1-3) but got $score")
    }

    // ── Reasoning Scoring ────────────────────────────────

    @Test
    fun `reasoning score 0 for null reasoning`() {
        val scenario = simpleScenario()
        assertEquals(0, Scorer.scoreReasoning(null, scenario, Action.fold()))
    }

    @Test
    fun `reasoning score 0 for empty reasoning`() {
        val scenario = simpleScenario()
        assertEquals(0, Scorer.scoreReasoning("", scenario, Action.fold()))
    }

    @Test
    fun `reasoning score 0 for very short reasoning`() {
        val scenario = simpleScenario()
        assertEquals(0, Scorer.scoreReasoning("ok", scenario, Action.fold()))
    }

    @Test
    fun `reasoning score 1 for generic text without keywords`() {
        val scenario = simpleScenario(keywords = setOf("monster", "nuts"))
        assertEquals(1, Scorer.scoreReasoning("I decided to take this action here", scenario, Action.fold()))
    }

    @Test
    fun `reasoning score 2 for text with expected keywords`() {
        val scenario = simpleScenario(keywords = setOf("weak", "fold"))
        assertEquals(2, Scorer.scoreReasoning("My hand is too weak to continue", scenario, Action.fold()))
    }

    @Test
    fun `reasoning score 3 for keywords plus poker terminology`() {
        val scenario = simpleScenario(keywords = setOf("weak"))
        assertEquals(3, Scorer.scoreReasoning(
            "My hand is weak. The pot odds don't justify a call with this board texture.",
            scenario, Action.fold()
        ))
    }

    // ── Consistency Scoring ──────────────────────────────

    @Test
    fun `consistency score 3 for reasonable distribution with correct dominant`() {
        val scenario = simpleScenario()
        val counts = mapOf(ActionType.FOLD to 7, ActionType.CHECK to 3)
        val result = Scorer.scoreConsistency(counts, 10, scenario)
        assertEquals(3, result.consistencyScore)
        assertEquals(ActionType.FOLD, result.dominantAction)
        assertEquals(0.7, result.dominantActionPct)
    }

    @Test
    fun `consistency score 2 for too deterministic distribution`() {
        val scenario = simpleScenario()
        val counts = mapOf(ActionType.FOLD to 10)
        val result = Scorer.scoreConsistency(counts, 10, scenario)
        assertEquals(2, result.consistencyScore)
    }

    @Test
    fun `consistency score 1 for catastrophic outlier`() {
        val scenario = simpleScenario(
            correctActions = listOf(WeightedAction(ActionType.FOLD, 1.0)),
            wrongActions = setOf(ActionType.RAISE)
        )
        val counts = mapOf(ActionType.FOLD to 8, ActionType.RAISE to 2)
        val result = Scorer.scoreConsistency(counts, 10, scenario)
        assertEquals(1, result.consistencyScore)
        assertTrue(result.hasCatastrophicOutlier)
    }

    @Test
    fun `consistency score 0 for incorrect dominant action`() {
        val scenario = simpleScenario(
            correctActions = listOf(WeightedAction(ActionType.FOLD, 1.0)),
            wrongActions = emptySet()
        )
        val counts = mapOf(ActionType.CHECK to 8, ActionType.FOLD to 2)
        val result = Scorer.scoreConsistency(counts, 10, scenario)
        assertEquals(0, result.consistencyScore)
    }
}
