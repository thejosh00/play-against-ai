package com.pokerai.eval

import com.pokerai.ai.Street
import com.pokerai.analysis.BoardWetness
import com.pokerai.analysis.HandStrengthTier
import com.pokerai.model.ActionType
import com.pokerai.model.archetype.*
import kotlin.test.*

class OracleValidatorTest {

    private fun easyFoldScenario(id: String = "oracle_test"): EvalScenario = ScenarioBuilder.create(id) {
        name = "Oracle fold test"
        category = ScenarioCategory.STRATEGIC_CORRECTNESS
        difficulty = EvalDifficulty.EASY
        hand(HandStrengthTier.NOTHING, "seven high, no draw")
        board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-high")
        pot(size = 100, betToCall = 100)
        headsUp()
        correct(ActionType.FOLD, weight = 1.0)
        wrong(ActionType.RAISE)
    }

    private fun fidelityScenario(): EvalScenario = ScenarioBuilder.create("oracle_fidelity") {
        name = "Oracle fidelity test"
        category = ScenarioCategory.ARCHETYPE_FIDELITY
        difficulty = EvalDifficulty.MEDIUM
        hand(HandStrengthTier.WEAK, "bottom pair, fives")
        board(Street.FLOP, BoardWetness.SEMI_WET, "semi-wet, Q-9-5 two-tone")
        boardFlags(flushPossible = true)
        pot(size = 60, betToCall = 30)
        headsUp()
        correct(ActionType.FOLD, weight = 0.7)
        correct(ActionType.CALL, weight = 0.7)
        correct(ActionType.RAISE, weight = 0.4)
        distribution(NitArchetype, ActionDistribution(0.70..0.95, 0.0..0.05, 0.05..0.25, 0.0..0.05))
        distribution(CallingStationArchetype, ActionDistribution(0.0..0.10, 0.0..0.05, 0.80..1.0, 0.0..0.10))
        distribution(LagArchetype, ActionDistribution(0.10..0.30, 0.0..0.05, 0.30..0.50, 0.25..0.50))
        distribution(TagArchetype, ActionDistribution(0.40..0.65, 0.0..0.05, 0.30..0.50, 0.05..0.15))
        distribution(SharkArchetype, ActionDistribution(0.20..0.45, 0.0..0.05, 0.35..0.55, 0.10..0.30))
    }

    @Test
    fun `oracle runs coded strategy across instinct range`() {
        val scenario = easyFoldScenario()
        val strategy = NitArchetype.getStrategy()!!

        val result = OracleValidator.runOracleForArchetype(
            scenario, NitArchetype, strategy, 1..100
        )

        assertEquals("oracle_test", result.scenarioId)
        assertEquals("Nit", result.archetypeName)
        assertEquals(100, result.totalRuns)
        // Nit facing pot-sized bet with NOTHING should fold most of the time
        assertTrue(result.actionCounts.isNotEmpty())
        assertTrue(result.dominantPct > 0.0)
    }

    @Test
    fun `oracle produces different distributions for different archetypes`() {
        val scenario = fidelityScenario()
        val nitStrategy = NitArchetype.getStrategy()!!
        val lagStrategy = LagArchetype.getStrategy()!!

        val nitResult = OracleValidator.runOracleForArchetype(
            scenario, NitArchetype, nitStrategy, 1..100
        )
        val lagResult = OracleValidator.runOracleForArchetype(
            scenario, LagArchetype, lagStrategy, 1..100
        )

        // They should have different action distributions
        val nitFolds = nitResult.actionCounts[ActionType.FOLD] ?: 0
        val lagFolds = lagResult.actionCounts[ActionType.FOLD] ?: 0
        val nitRaises = (nitResult.actionCounts[ActionType.RAISE] ?: 0) +
                       (nitResult.actionCounts[ActionType.ALL_IN] ?: 0)
        val lagRaises = (lagResult.actionCounts[ActionType.RAISE] ?: 0) +
                       (lagResult.actionCounts[ActionType.ALL_IN] ?: 0)

        // Nit should fold more than LAG
        assertTrue(
            nitFolds > lagFolds || lagRaises > nitRaises,
            "Nit and LAG should have different distributions. Nit folds=$nitFolds, LAG folds=$lagFolds, Nit raises=$nitRaises, LAG raises=$lagRaises"
        )
    }

    @Test
    fun `calibration check passes when distribution matches expected`() {
        val result = OracleResult(
            scenarioId = "test",
            archetypeName = "Nit",
            actionCounts = mapOf(ActionType.FOLD to 80, ActionType.CALL to 20),
            totalRuns = 100,
            dominantAction = ActionType.FOLD,
            dominantPct = 0.80,
            avgConfidence = 0.7,
            distribution = ActionDistribution(0.80..0.80, 0.0..0.0, 0.20..0.20, 0.0..0.0)
        )
        val expected = ActionDistribution(0.70..0.90, 0.0..0.05, 0.10..0.30, 0.0..0.05)

        val errors = OracleValidator.checkCalibration("test", "Nit", result, expected)

        assertTrue(errors.isEmpty(), "Should have no calibration errors, got: $errors")
    }

    @Test
    fun `calibration check fails when distribution is out of range`() {
        val result = OracleResult(
            scenarioId = "test",
            archetypeName = "Nit",
            actionCounts = mapOf(ActionType.FOLD to 30, ActionType.CALL to 70),
            totalRuns = 100,
            dominantAction = ActionType.CALL,
            dominantPct = 0.70,
            avgConfidence = 0.5,
            distribution = ActionDistribution(0.30..0.30, 0.0..0.0, 0.70..0.70, 0.0..0.0)
        )
        val expected = ActionDistribution(0.70..0.90, 0.0..0.05, 0.05..0.25, 0.0..0.05)

        val errors = OracleValidator.checkCalibration("test", "Nit", result, expected)

        assertTrue(errors.isNotEmpty(), "Should have calibration errors for 30% fold vs 70-90% expected")
    }

    @Test
    fun `calibration allows tolerance`() {
        val result = OracleResult(
            scenarioId = "test",
            archetypeName = "Nit",
            actionCounts = mapOf(ActionType.FOLD to 61, ActionType.CALL to 39),
            totalRuns = 100,
            dominantAction = ActionType.FOLD,
            dominantPct = 0.61,
            avgConfidence = 0.6,
            distribution = ActionDistribution(0.61..0.61, 0.0..0.0, 0.39..0.39, 0.0..0.0)
        )
        // Expected fold is 70-90%, but with 10% tolerance → 60-100% is accepted
        val expected = ActionDistribution(0.70..0.90, 0.0..0.05, 0.05..0.25, 0.0..0.05)

        val errors = OracleValidator.checkCalibration("test", "Nit", result, expected)

        val foldErrors = errors.filter { it.contains("fold") }
        assertTrue(foldErrors.isEmpty(), "61% fold should be within tolerance of 70-90% expected (tolerance ±10%). Errors: $errors")
    }

    @Test
    fun `validate runs across multiple scenarios and archetypes`() {
        val scenarios = listOf(
            easyFoldScenario("s1"),
            easyFoldScenario("s2")
        )

        val report = OracleValidator.validate(scenarios, instinctSweep = 1..10)

        // 2 scenarios × 5 archetypes = 10 results
        assertTrue(report.results.size >= 10, "Expected at least 10 results, got ${report.results.size}")
        assertTrue(report.results.any { it.scenarioId == "s1" })
        assertTrue(report.results.any { it.scenarioId == "s2" })
    }

    @Test
    fun `skips non-fidelity non-strategic scenarios`() {
        val formatScenario = ScenarioBuilder.create("format_skip") {
            name = "Format scenario"
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            hand(HandStrengthTier.NOTHING, "nothing")
            board(Street.RIVER, BoardWetness.DRY)
            pot(size = 100, betToCall = 100)
            correct(ActionType.FOLD, weight = 1.0)
        }

        val report = OracleValidator.validate(listOf(formatScenario), instinctSweep = 1..10)

        assertTrue(report.results.isEmpty(), "FORMAT_COMPLIANCE should be skipped by oracle")
    }


}
