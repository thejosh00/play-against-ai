package com.pokerai.eval

import com.pokerai.ai.Street
import com.pokerai.analysis.BoardWetness
import com.pokerai.analysis.HandStrengthTier
import com.pokerai.model.Action
import com.pokerai.model.ActionType
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ReasoningJudgeTest {

    private fun simpleScenario(): EvalScenario = ScenarioBuilder.create("test_judge") {
        name = "Judge test scenario"
        difficulty = EvalDifficulty.EASY
        hand(HandStrengthTier.NOTHING, "seven high")
        board(Street.RIVER, BoardWetness.DRY)
        pot(size = 100, betToCall = 100)
        headsUp()
        correct(ActionType.FOLD, weight = 1.0)
        wrong(ActionType.RAISE)
        keywords("weak", "fold", "nothing")
    }

    @Test
    fun `disabled judge falls back to heuristic scoring`() = runTest {
        val judge = ReasoningJudge(judgeAdapter = null, isEnabled = false)
        val scenario = simpleScenario()
        val action = Action.fold()

        val judgeScore = judge.scoreReasoning("I should fold this weak hand", scenario, action, "Nit")
        val heuristicScore = Scorer.scoreReasoning("I should fold this weak hand", scenario, action)

        assertEquals(heuristicScore, judgeScore)
    }

    @Test
    fun `null reasoning returns 0`() = runTest {
        val judge = ReasoningJudge(judgeAdapter = null, isEnabled = false)
        val scenario = simpleScenario()

        val score = judge.scoreReasoning(null, scenario, Action.fold(), "Nit")

        assertEquals(0, score)
    }

    @Test
    fun `blank reasoning returns 0`() = runTest {
        val adapter = FakeEvalAdapter("judge", "3")
        val judge = ReasoningJudge(judgeAdapter = adapter, isEnabled = true)
        val scenario = simpleScenario()

        val score = judge.scoreReasoning("", scenario, Action.fold(), "Nit")

        assertEquals(0, score)
    }

    @Test
    fun `null action returns 0`() = runTest {
        val adapter = FakeEvalAdapter("judge", "3")
        val judge = ReasoningJudge(judgeAdapter = adapter, isEnabled = true)
        val scenario = simpleScenario()

        val score = judge.scoreReasoning("some reasoning", scenario, null, "Nit")

        assertEquals(0, score)
    }

    @Test
    fun `judge with fake adapter parses single digit response`() = runTest {
        val adapter = FakeEvalAdapter("judge", "3")
        val judge = ReasoningJudge(judgeAdapter = adapter, isEnabled = true)
        val scenario = simpleScenario()

        val score = judge.scoreReasoning("Folding because hand is weak", scenario, Action.fold(), "Nit")

        assertEquals(3, score)
    }

    @Test
    fun `judge handles response with trailing newline`() = runTest {
        val adapter = FakeEvalAdapter("judge", "2\n")
        val judge = ReasoningJudge(judgeAdapter = adapter, isEnabled = true)
        val scenario = simpleScenario()

        val score = judge.scoreReasoning("Generic fold reasoning", scenario, Action.fold(), "Nit")

        assertEquals(2, score)
    }

    @Test
    fun `judge handles verbose non-numeric response`() = runTest {
        // Adapter returns "The score is 2 because..." — first char 'T' is not a digit
        val adapter = FakeEvalAdapter("judge", "The score is 2 because the reasoning is adequate")
        val judge = ReasoningJudge(judgeAdapter = adapter, isEnabled = true)
        val scenario = simpleScenario()

        val score = judge.scoreReasoning("I fold", scenario, Action.fold(), "Nit")

        // 'T'.toIntOrNull() → null → default 1
        assertEquals(1, score)
    }

    @Test
    fun `judge gracefully handles adapter exception`() = runTest {
        val adapter = object : LlmAdapter {
            override val modelName = "broken"
            override val provider = "test"
            override suspend fun complete(systemPrompt: String, userPrompt: String, temperature: Double): String {
                throw RuntimeException("Connection failed")
            }
        }
        val judge = ReasoningJudge(judgeAdapter = adapter, isEnabled = true)
        val scenario = simpleScenario()

        // Should not crash — falls back to heuristic
        val score = judge.scoreReasoning("Folding this weak hand", scenario, Action.fold(), "Nit")

        // Heuristic should give >= 1 for non-empty reasoning
        assertTrue(score >= 0)
        assertTrue(score <= 3)
    }

    @Test
    fun `score is clamped to 0-3 when adapter returns high value`() = runTest {
        val adapter = FakeEvalAdapter("judge", "5")
        val judge = ReasoningJudge(judgeAdapter = adapter, isEnabled = true)
        val scenario = simpleScenario()

        val score = judge.scoreReasoning("Good reasoning", scenario, Action.fold(), "Nit")

        assertEquals(3, score)
    }

    @Test
    fun `personality summaries exist for all archetypes`() {
        val judge = ReasoningJudge(judgeAdapter = null, isEnabled = false)
        val archetypeNames = listOf("Nit", "Calling Station", "Loose-Aggressive", "Tight-Aggressive", "Shark")

        for (name in archetypeNames) {
            val summary = judge.getArchetypePersonalitySummary(name)
            assertTrue(summary.isNotBlank(), "Personality summary for '$name' should not be blank")
            assertNotEquals("Standard poker player.", summary, "Archetype '$name' should have a specific summary")
        }
    }

    @Test
    fun `isEnabled reflects constructor parameter`() {
        val enabledJudge = ReasoningJudge(judgeAdapter = FakeEvalAdapter("j", "1"), isEnabled = true)
        val disabledJudge = ReasoningJudge(judgeAdapter = null, isEnabled = false)

        assertTrue(enabledJudge.isEnabled)
        assertFalse(disabledJudge.isEnabled)
    }
}
