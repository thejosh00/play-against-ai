package com.pokerai.eval

import com.pokerai.ai.strategy.ArchetypeStrategy
import com.pokerai.model.ActionType
import com.pokerai.model.archetype.*

/**
 * Result of running a scenario through a coded strategy across many instinct values.
 */
data class OracleResult(
    val scenarioId: String,
    val archetypeName: String,
    val actionCounts: Map<ActionType, Int>,
    val totalRuns: Int,
    val dominantAction: ActionType,
    val dominantPct: Double,
    val avgConfidence: Double,
    val distribution: ActionDistribution
)

/**
 * Full oracle validation report.
 */
data class OracleReport(
    val results: List<OracleResult>,
    val calibrationErrors: List<String>,
    val totalScenarios: Int,
    val totalArchetypes: Int
)

/**
 * Runs evaluation scenarios through the coded archetype strategies (the "oracle")
 * to produce a ground-truth action distribution for each scenario x archetype pair.
 *
 * The coded strategies use the `instinct` field (1-100) as a randomizer.
 * By sweeping instinct from 1 to 100, we get the full action distribution
 * that the coded strategy produces.
 *
 * This validates the scenario library and provides a baseline for LLM comparison.
 */
object OracleValidator {

    private val archetypes: List<PlayerArchetype> = listOf(
        NitArchetype, CallingStationArchetype, LagArchetype, TagArchetype, SharkArchetype
    )


    /**
     * Run all scenarios through all coded strategies.
     *
     * @param scenarios the scenarios to validate
     * @param instinctSweep the range of instinct values to test (default 1..100)
     * @return the oracle report with distributions and calibration errors
     */
    fun validate(
        scenarios: List<EvalScenario>,
        instinctSweep: IntRange = 1..100
    ): OracleReport {
        val results = mutableListOf<OracleResult>()
        val calibrationErrors = mutableListOf<String>()

        for (scenario in scenarios) {
            if (scenario.category != ScenarioCategory.ARCHETYPE_FIDELITY &&
                scenario.category != ScenarioCategory.STRATEGIC_CORRECTNESS) continue

            for (archetype in archetypes) {
                val strategy = archetype.getStrategy() ?: continue

                val result = runOracleForArchetype(scenario, archetype, strategy, instinctSweep)
                results.add(result)

                val expectedDist = scenario.archetypeDistributions?.get(archetype)
                if (expectedDist != null) {
                    val errors = checkCalibration(scenario.id, archetype.displayName, result, expectedDist)
                    calibrationErrors.addAll(errors)
                }
            }
        }

        return OracleReport(
            results = results,
            calibrationErrors = calibrationErrors,
            totalScenarios = scenarios.size,
            totalArchetypes = archetypes.size
        )
    }

    /**
     * Run a single scenario through a single coded strategy across all instinct values.
     */
    fun runOracleForArchetype(
        scenario: EvalScenario,
        archetype: PlayerArchetype,
        strategy: ArchetypeStrategy,
        instinctSweep: IntRange
    ): OracleResult {
        val actionCounts = mutableMapOf<ActionType, Int>()
        var totalConfidence = 0.0
        val totalRuns = instinctSweep.count()
        val profile = archetype.createProfile()

        for (instinct in instinctSweep) {
            val ctx = scenario.context.copy(
                instinct = instinct,
                profile = profile
            )

            val decision = strategy.decide(ctx)
            val actionType = decision.action.type
            actionCounts[actionType] = (actionCounts[actionType] ?: 0) + 1
            totalConfidence += decision.confidence
        }

        val dominantEntry = actionCounts.maxByOrNull { it.value }!!
        val dominantPct = dominantEntry.value.toDouble() / totalRuns

        return OracleResult(
            scenarioId = scenario.id,
            archetypeName = archetype.displayName,
            actionCounts = actionCounts,
            totalRuns = totalRuns,
            dominantAction = dominantEntry.key,
            dominantPct = dominantPct,
            avgConfidence = totalConfidence / totalRuns,
            distribution = computeDistribution(actionCounts, totalRuns)
        )
    }

    /**
     * Convert action counts into an ActionDistribution (exact percentages, no range).
     */
    private fun computeDistribution(counts: Map<ActionType, Int>, total: Int): ActionDistribution {
        fun pct(type: ActionType): Double = (counts[type] ?: 0).toDouble() / total

        val foldPct = pct(ActionType.FOLD)
        val checkPct = pct(ActionType.CHECK)
        val callPct = pct(ActionType.CALL)
        val raisePct = pct(ActionType.RAISE) + pct(ActionType.ALL_IN)

        return ActionDistribution(
            foldPct = foldPct..foldPct,
            checkPct = checkPct..checkPct,
            callPct = callPct..callPct,
            raisePct = raisePct..raisePct
        )
    }

    /**
     * Check whether the coded strategy's actual distribution falls within the expected ranges.
     * Returns a list of calibration error messages (empty if all good).
     */
    internal fun checkCalibration(
        scenarioId: String,
        archetypeName: String,
        result: OracleResult,
        expected: ActionDistribution
    ): List<String> {
        val errors = mutableListOf<String>()
        val total = result.totalRuns.toDouble()

        fun checkAction(name: String, actual: Double, expectedRange: ClosedRange<Double>) {
            val tolerance = 0.10
            val lowerBound = (expectedRange.start - tolerance).coerceAtLeast(0.0)
            val upperBound = (expectedRange.endInclusive + tolerance).coerceAtMost(1.0)

            if (actual < lowerBound || actual > upperBound) {
                errors.add(
                    "CALIBRATION: $scenarioId/$archetypeName — $name: " +
                    "coded=${String.format("%.0f", actual * 100)}%, " +
                    "expected=${String.format("%.0f", expectedRange.start * 100)}-" +
                    "${String.format("%.0f", expectedRange.endInclusive * 100)}%"
                )
            }
        }

        val foldPct = (result.actionCounts[ActionType.FOLD] ?: 0) / total
        val checkPct = (result.actionCounts[ActionType.CHECK] ?: 0) / total
        val callPct = (result.actionCounts[ActionType.CALL] ?: 0) / total
        val raisePct = ((result.actionCounts[ActionType.RAISE] ?: 0) +
                       (result.actionCounts[ActionType.ALL_IN] ?: 0)) / total

        checkAction("fold", foldPct, expected.foldPct)
        checkAction("check", checkPct, expected.checkPct)
        checkAction("call", callPct, expected.callPct)
        checkAction("raise", raisePct, expected.raisePct)

        return errors
    }

    /**
     * Print a summary of the oracle validation results.
     */
    fun printReport(report: OracleReport) {
        println()
        println("══════════════════════════════════════════════════════════════")
        println("                  ORACLE VALIDATION REPORT                   ")
        println("══════════════════════════════════════════════════════════════")
        println()
        println("Validated ${report.totalScenarios} scenarios x ${report.totalArchetypes} archetypes")
        println("Oracle runs: ${report.results.size}")
        println()

        val byScenario = report.results.groupBy { it.scenarioId }
        for ((scenarioId, results) in byScenario) {
            println("── $scenarioId ──")
            for (result in results.sortedBy { it.archetypeName }) {
                val dist = result.actionCounts.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                    .joinToString("  ") { (action, count) ->
                        "${action.name}=${String.format("%.0f", count.toDouble() / result.totalRuns * 100)}%"
                    }
                val conf = String.format("%.2f", result.avgConfidence)
                println("  ${result.archetypeName.padEnd(18)} $dist  (avg confidence: $conf)")
            }
            println()
        }

        if (report.calibrationErrors.isEmpty()) {
            println("No calibration errors — all coded strategies match expected distributions.")
        } else {
            println("CALIBRATION ERRORS (${report.calibrationErrors.size}):")
            for (error in report.calibrationErrors) {
                println("  $error")
            }
            println()
            println("These scenarios may need their expected distributions adjusted,")
            println("or the coded strategies may need tuning.")
        }
    }
}
