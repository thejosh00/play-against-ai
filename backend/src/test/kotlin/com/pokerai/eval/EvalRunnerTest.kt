package com.pokerai.eval

import com.pokerai.ai.Street
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.NitArchetype
import com.pokerai.model.archetype.TagArchetype
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Fake adapter for testing the eval runner without actual LLM calls.
 */
class FakeEvalAdapter(
    override val modelName: String = "fake-model",
    override val provider: String = "test",
    private val responses: Iterator<String>
) : LlmAdapter {

    constructor(modelName: String = "fake-model", vararg responses: String) :
        this(modelName, "test", responses.iterator())

    override suspend fun complete(systemPrompt: String, userPrompt: String, temperature: Double): String {
        return if (responses.hasNext()) responses.next()
        else """{"action": "check", "amount": null, "reasoning": "default"}"""
    }
}

class EvalRunnerTest {

    private fun foldScenario(
        id: String = "s1",
        category: ScenarioCategory = ScenarioCategory.STRATEGIC_CORRECTNESS
    ): EvalScenario {
        return ScenarioBuilder.create(id) {
            name = "Fold scenario"
            this.category = category
            difficulty = EvalDifficulty.EASY
            hand(HandStrengthTier.NOTHING, "seven high")
            board(Street.RIVER, BoardWetness.DRY)
            pot(size = 100, betToCall = 100)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            wrong(ActionType.RAISE)
            keywords("weak", "fold")
        }
    }

    // ── runSingleScenario ────────────────────────────────

    @Test
    fun `runSingleScenario with perfect response scores high`() = runTest {
        val adapter = FakeEvalAdapter(
            "test-model",
            """{"action": "fold", "amount": null, "reasoning": "too weak to continue"}"""
        )
        val scenario = foldScenario()
        val runner = EvalRunner(listOf(adapter), listOf(scenario))

        val score = runner.runSingleScenario(adapter, scenario, NitArchetype)

        assertEquals(3, score.formatScore)
        assertEquals(4, score.strategyScore)
        assertEquals(ActionType.FOLD, score.action?.type)
        assertNotNull(score.reasoning)
    }

    @Test
    fun `runSingleScenario with wrong action scores zero strategy`() = runTest {
        val adapter = FakeEvalAdapter(
            "test-model",
            """{"action": "raise", "amount": 200, "reasoning": "bluff"}"""
        )
        val scenario = foldScenario()
        val runner = EvalRunner(listOf(adapter), listOf(scenario))

        val score = runner.runSingleScenario(adapter, scenario, NitArchetype)

        assertEquals(3, score.formatScore)
        assertEquals(0, score.strategyScore)
        assertEquals(ActionType.RAISE, score.action?.type)
    }

    @Test
    fun `runSingleScenario with parse failure scores zero`() = runTest {
        val adapter = FakeEvalAdapter("test-model", "I dunno man")
        val scenario = foldScenario()
        val runner = EvalRunner(listOf(adapter), listOf(scenario))

        val score = runner.runSingleScenario(adapter, scenario, NitArchetype)

        assertEquals(0, score.formatScore)
        assertEquals(0, score.strategyScore)
        assertNull(score.action)
    }

    // ── runConsistencyTest ───────────────────────────────

    @Test
    fun `runConsistencyTest produces action distribution`() = runTest {
        val adapter = FakeEvalAdapter(
            "test-model",
            """{"action": "fold", "amount": null, "reasoning": "weak"}""",
            """{"action": "fold", "amount": null, "reasoning": "weak"}""",
            """{"action": "fold", "amount": null, "reasoning": "weak"}""",
            """{"action": "fold", "amount": null, "reasoning": "weak"}""",
            """{"action": "call", "amount": 100, "reasoning": "maybe"}"""
        )
        val scenario = foldScenario(category = ScenarioCategory.BEHAVIORAL_CONSISTENCY)
        val runner = EvalRunner(listOf(adapter), listOf(scenario), repetitions = 5, delayBetweenCallsMs = 0)

        val result = runner.runConsistencyTest(adapter, scenario, NitArchetype, 5)

        assertEquals(ActionType.FOLD, result.dominantAction)
        assertEquals(0.8, result.dominantActionPct, 0.001)
        assertEquals(4, result.actionCounts[ActionType.FOLD])
        assertEquals(1, result.actionCounts[ActionType.CALL])
        assertEquals(5, result.totalRuns)
    }

    // ── runModelEval ─────────────────────────────────────

    @Test
    fun `runModelEval aggregates scores across scenarios`() = runTest {
        val responses = listOf(
            """{"action": "fold", "amount": null, "reasoning": "too weak"}""",
            """{"action": "fold", "amount": null, "reasoning": "no equity"}""",
            """{"action": "fold", "amount": null, "reasoning": "clear fold"}"""
        )
        val adapter = FakeEvalAdapter("test-model", *responses.toTypedArray())

        val scenarios = listOf(
            foldScenario("s1"),
            foldScenario("s2"),
            foldScenario("s3")
        )
        val runner = EvalRunner(listOf(adapter), scenarios, delayBetweenCallsMs = 0)

        val report = runner.runModelEval(adapter)

        assertEquals("test-model", report.modelName)
        assertEquals(3, report.totalScenarios)
        assertTrue(report.formatScore > 0)
        assertTrue(report.strategyScore > 0)
    }

    // ── Fidelity scenarios ───────────────────────────────

    @Test
    fun `fidelity scenarios run through multiple archetypes`() = runTest {
        // One response per archetype (5 archetypes)
        val responses = List(5) {
            """{"action": "fold", "amount": null, "reasoning": "test"}"""
        }
        val adapter = FakeEvalAdapter("test-model", *responses.toTypedArray())

        val fidelityScenario = ScenarioBuilder.create("fid1") {
            name = "Fidelity test"
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            hand(HandStrengthTier.MEDIUM, "middle pair")
            board(Street.FLOP, BoardWetness.SEMI_WET)
            pot(size = 100, betToCall = 50)
            correct(ActionType.FOLD, weight = 0.5)
            correct(ActionType.CALL, weight = 0.5)
        }

        val runner = EvalRunner(listOf(adapter), listOf(fidelityScenario), delayBetweenCallsMs = 0)
        val report = runner.runModelEval(adapter)

        // Should have 5 scores (one per archetype)
        assertEquals(5, report.scenarioScores.size)
        // Should have scores from different archetypes
        val archetypeNames = report.scenarioScores.map { it.archetypeName }.distinct()
        assertEquals(5, archetypeNames.size)
    }

    // ── Full eval ────────────────────────────────────────

    @Test
    fun `runFullEval produces EvalReport with recommendation`() = runTest {
        val responses = List(3) {
            """{"action": "fold", "amount": null, "reasoning": "weak hand"}"""
        }
        val adapter = FakeEvalAdapter("test-model", *responses.toTypedArray())
        val scenarios = listOf(foldScenario("s1"), foldScenario("s2"), foldScenario("s3"))
        val runner = EvalRunner(listOf(adapter), scenarios, delayBetweenCallsMs = 0)

        val report = runner.runFullEval()

        assertEquals(1, report.models.size)
        assertEquals(3, report.scenarioCount)
        assertTrue(report.recommendation.isNotEmpty())
    }
}
