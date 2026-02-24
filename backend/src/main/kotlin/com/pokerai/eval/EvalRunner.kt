package com.pokerai.eval

import com.pokerai.model.*
import com.pokerai.model.archetype.*
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

/**
 * Orchestrates the full evaluation pipeline.
 *
 * Runs scenarios against LLM adapters, scores responses, and produces reports.
 */
class EvalRunner(
    private val adapters: List<LlmAdapter>,
    private val scenarios: List<EvalScenario>,
    private val repetitions: Int = 5,
    private val temperature: Double = 0.7,
    private val delayBetweenCallsMs: Long = 100
) {
    private val fidelityArchetypes: List<PlayerArchetype> = listOf(
        NitArchetype, CallingStationArchetype, LagArchetype, TagArchetype, SharkArchetype
    )

    /**
     * Run the full evaluation suite across all adapters and scenarios.
     */
    suspend fun runFullEval(): EvalReport {
        val modelReports = adapters.map { adapter ->
            println("━━━ Evaluating: ${adapter.modelName} (${adapter.provider}) ━━━")
            runModelEval(adapter)
        }

        return EvalReport(
            timestamp = System.currentTimeMillis(),
            models = modelReports,
            scenarioCount = scenarios.size,
            archetypesTested = fidelityArchetypes.map { it.displayName },
            recommendation = generateRecommendation(modelReports)
        )
    }

    /**
     * Run all scenarios for a single model.
     */
    suspend fun runModelEval(adapter: LlmAdapter): ModelReport {
        val allScores = mutableListOf<ScenarioScore>()
        val consistencyResults = mutableListOf<ConsistencyResult>()

        // ── Strategic correctness + format compliance ──
        val strategicScenarios = scenarios.filter {
            it.category == ScenarioCategory.STRATEGIC_CORRECTNESS ||
            it.category == ScenarioCategory.FORMAT_COMPLIANCE
        }

        for (scenario in strategicScenarios) {
            print("  ${scenario.id}: ${scenario.name}... ")
            val archetype = scenario.context.profile.archetype
            val score = runSingleScenario(adapter, scenario, archetype)
            allScores.add(score)
            println("format=${score.formatScore} strategy=${score.strategyScore} reasoning=${score.reasoningScore}")
            delay(delayBetweenCallsMs)
        }

        // ── Archetype fidelity ──
        val fidelityScenarios = scenarios.filter {
            it.category == ScenarioCategory.ARCHETYPE_FIDELITY
        }

        for (scenario in fidelityScenarios) {
            for (archetype in fidelityArchetypes) {
                print("  ${scenario.id}/${archetype.displayName}... ")
                val archetypeScenario = scenario.copy(
                    context = scenario.context.copy(
                        profile = archetype.createProfile()
                    )
                )
                val score = runSingleScenario(adapter, archetypeScenario, archetype)
                allScores.add(score)
                println("${score.action?.type ?: "FAIL"}")
                delay(delayBetweenCallsMs)
            }
        }

        // ── Behavioral consistency ──
        val consistencyScenarios = scenarios.filter {
            it.category == ScenarioCategory.BEHAVIORAL_CONSISTENCY
        }

        for (scenario in consistencyScenarios) {
            print("  ${scenario.id} (${repetitions}x)... ")
            val archetype = scenario.context.profile.archetype
            val result = runConsistencyTest(adapter, scenario, archetype, repetitions)
            consistencyResults.add(result)
            val dominant = result.dominantAction?.name ?: "NONE"
            println("dominant=$dominant (${String.format("%.0f", result.dominantActionPct * 100)}%) score=${result.consistencyScore}")
        }

        return buildModelReport(adapter, allScores, consistencyResults)
    }

    /**
     * Run a single scenario once and score the response.
     */
    suspend fun runSingleScenario(
        adapter: LlmAdapter,
        scenario: EvalScenario,
        archetype: PlayerArchetype
    ): ScenarioScore {
        val systemPrompt = EvalPromptBuilder.buildSystemPrompt(archetype)
        val userPrompt = EvalPromptBuilder.buildUserPrompt(scenario)

        var rawResponse: String
        val latencyMs = measureTimeMillis {
            rawResponse = adapter.complete(systemPrompt, userPrompt, temperature)
        }

        val parseResult = EvalResponseParser.parse(rawResponse)

        val action = when (parseResult) {
            is ParseResult.Success -> parseResult.action
            is ParseResult.PartialSuccess -> parseResult.action
            is ParseResult.Failure -> null
        }

        val reasoning = when (parseResult) {
            is ParseResult.Success -> parseResult.reasoning
            is ParseResult.PartialSuccess -> parseResult.reasoning
            is ParseResult.Failure -> null
        }

        return ScenarioScore(
            scenarioId = scenario.id,
            modelName = adapter.modelName,
            archetypeName = archetype.displayName,
            formatScore = Scorer.scoreFormat(parseResult),
            strategyScore = Scorer.scoreStrategy(action, scenario),
            reasoningScore = Scorer.scoreReasoning(reasoning, scenario, action),
            action = action,
            reasoning = reasoning,
            rawResponse = rawResponse,
            parseResult = parseResult,
            latencyMs = latencyMs
        )
    }

    /**
     * Run a scenario multiple times and evaluate behavioral consistency.
     */
    suspend fun runConsistencyTest(
        adapter: LlmAdapter,
        scenario: EvalScenario,
        archetype: PlayerArchetype,
        reps: Int
    ): ConsistencyResult {
        val actionCounts = mutableMapOf<ActionType, Int>()
        var totalLatency = 0L

        for (i in 1..reps) {
            val systemPrompt = EvalPromptBuilder.buildSystemPrompt(archetype)
            val userPrompt = EvalPromptBuilder.buildUserPrompt(scenario)

            val latencyMs = measureTimeMillis {
                val rawResponse = adapter.complete(systemPrompt, userPrompt, temperature)
                val parseResult = EvalResponseParser.parse(rawResponse)

                val actionType = when (parseResult) {
                    is ParseResult.Success -> parseResult.action.type
                    is ParseResult.PartialSuccess -> parseResult.action.type
                    is ParseResult.Failure -> null
                }

                if (actionType != null) {
                    actionCounts[actionType] = (actionCounts[actionType] ?: 0) + 1
                }
            }
            totalLatency += latencyMs

            delay(delayBetweenCallsMs)
        }

        val result = Scorer.scoreConsistency(actionCounts, reps, scenario)
        return result.copy(
            modelName = adapter.modelName,
            avgLatencyMs = if (reps > 0) totalLatency / reps else 0
        )
    }

    // ── Report building ──────────────────────────────────

    private fun buildModelReport(
        adapter: LlmAdapter,
        scores: List<ScenarioScore>,
        consistencyResults: List<ConsistencyResult>
    ): ModelReport {
        val totalScenarios = scores.map { it.scenarioId }.distinct().size

        // Aggregate format score
        val avgFormat = scores.map { it.formatScore }.average()
        val formatPct = avgFormat / 3.0 * 100

        // Aggregate strategy score
        val strategyScores = scores.filter { it.action != null }
        val avgStrategy = if (strategyScores.isNotEmpty()) strategyScores.map { it.strategyScore }.average() else 0.0
        val strategyPct = avgStrategy / 4.0 * 100

        // Aggregate reasoning score
        val avgReasoning = scores.map { it.reasoningScore }.average()
        val reasoningPct = avgReasoning / 3.0 * 100

        // Aggregate consistency
        val avgConsistency = if (consistencyResults.isNotEmpty()) {
            consistencyResults.map { it.consistencyScore }.average()
        } else 0.0
        val consistencyPct = avgConsistency / 3.0 * 100

        // Fidelity
        val fidelityPct = calculateFidelityScore(scores)

        // Weighted total: format 15%, fidelity 25%, strategy 30%, reasoning 15%, consistency 15%
        val weightedTotal = formatPct * 0.15 + fidelityPct * 0.25 + strategyPct * 0.30 +
                           reasoningPct * 0.15 + consistencyPct * 0.15

        // Per-archetype breakdown
        val archetypeScores = scores.groupBy { it.archetypeName }.mapValues { (name, archetypeScores) ->
            ArchetypeScore(
                archetypeName = name,
                avgFormatScore = archetypeScores.map { it.formatScore }.average(),
                avgStrategyScore = archetypeScores.map { it.strategyScore }.average(),
                avgReasoningScore = archetypeScores.map { it.reasoningScore }.average(),
                fidelityScore = 0.0,
                avgConsistencyScore = consistencyResults
                    .filter { it.archetypeName == name }
                    .map { it.consistencyScore }
                    .average()
                    .takeIf { !it.isNaN() } ?: 0.0
            )
        }

        // Per-difficulty breakdown
        val difficultyScores = scores.groupBy { s ->
            scenarios.find { it.id == s.scenarioId }?.difficulty ?: EvalDifficulty.MEDIUM
        }.mapValues { (_, diffScores) ->
            diffScores.map { it.strategyScore }.average() / 4.0 * 100
        }

        // Failure analysis
        val parseFailures = scores.count { it.parseResult is ParseResult.Failure }
        val parseFailureRate = if (scores.isNotEmpty()) parseFailures.toDouble() / scores.size else 0.0

        val catastrophicErrors = scores.count { score ->
            val scenario = scenarios.find { it.id == score.scenarioId }
            score.action?.type != null && scenario?.wrongActions?.contains(score.action.type) == true
        }
        val catastrophicErrorRate = if (scores.isNotEmpty()) catastrophicErrors.toDouble() / scores.size else 0.0

        val commonFailures = mutableListOf<String>()
        if (parseFailureRate > 0.05) {
            commonFailures.add("Parse failure rate: ${String.format("%.1f", parseFailureRate * 100)}%")
        }
        if (catastrophicErrorRate > 0.01) {
            commonFailures.add("Catastrophic error rate: ${String.format("%.1f", catastrophicErrorRate * 100)}%")
        }
        val contradictions = scores.count { score ->
            score.reasoning != null && score.action != null &&
            hasContradiction(score.reasoning, score.action)
        }
        if (contradictions > 0) {
            commonFailures.add("Action-reasoning contradictions: $contradictions")
        }

        return ModelReport(
            modelName = adapter.modelName,
            provider = adapter.provider,
            temperature = temperature,
            totalScenarios = totalScenarios,
            totalRuns = scores.size + consistencyResults.sumOf { it.totalRuns },
            formatScore = formatPct,
            fidelityScore = fidelityPct,
            strategyScore = strategyPct,
            reasoningScore = reasoningPct,
            consistencyScore = consistencyPct,
            weightedTotal = weightedTotal,
            archetypeScores = archetypeScores,
            difficultyScores = difficultyScores,
            parseFailureRate = parseFailureRate,
            catastrophicErrorRate = catastrophicErrorRate,
            commonFailures = commonFailures,
            scenarioScores = scores,
            consistencyResults = consistencyResults
        )
    }

    /**
     * Calculate fidelity score: how distinguishable are the archetypes?
     */
    private fun calculateFidelityScore(scores: List<ScenarioScore>): Double {
        val fidelityScores = scores
            .filter { it.action != null }
            .groupBy { it.scenarioId }
            .filter { (_, group) -> group.map { it.archetypeName }.distinct().size >= 3 }
            .map { (_, group) ->
                val actionsByArchetype = group.associate { it.archetypeName to it.action!!.type }
                val uniqueActions = actionsByArchetype.values.distinct().size
                val totalArchetypes = actionsByArchetype.size.toDouble()
                (uniqueActions / totalArchetypes).coerceAtMost(1.0)
            }

        return if (fidelityScores.isNotEmpty()) {
            fidelityScores.average() * 100
        } else 50.0
    }

    private fun hasContradiction(reasoning: String, action: Action): Boolean {
        val lower = reasoning.lowercase()
        return when (action.type) {
            ActionType.FOLD -> lower.contains("should raise") || lower.contains("should call") ||
                              lower.contains("i'll raise") || lower.contains("i'll call")
            ActionType.CALL -> lower.contains("should fold") || lower.contains("i'll fold") ||
                              lower.contains("too weak to call")
            ActionType.RAISE -> lower.contains("should fold") || lower.contains("i'll fold") ||
                               lower.contains("just call")
            else -> false
        }
    }

    private fun generateRecommendation(reports: List<ModelReport>): String {
        if (reports.isEmpty()) return "No models evaluated."

        val sorted = reports.sortedByDescending { it.weightedTotal }
        val best = sorted.first()

        val sb = StringBuilder()
        sb.appendLine("RANKING:")
        sorted.forEachIndexed { i, report ->
            sb.appendLine("  ${i + 1}. ${report.modelName} (${report.provider}) — " +
                "score: ${String.format("%.1f", report.weightedTotal)}/100")
        }
        sb.appendLine()

        if (best.weightedTotal >= 80) {
            sb.appendLine("RECOMMENDATION: ${best.modelName} is suitable for all archetypes.")
        } else if (best.weightedTotal >= 60) {
            sb.appendLine("RECOMMENDATION: ${best.modelName} is usable but may struggle with complex archetypes (LAG/Shark).")
        } else {
            sb.appendLine("WARNING: No model scored above 60. Consider using larger models or improving prompts.")
        }

        if (best.parseFailureRate > 0.1) {
            sb.appendLine("NOTE: Even the best model has ${String.format("%.0f", best.parseFailureRate * 100)}% parse failures. " +
                "Consider improving format instructions or response parsing.")
        }

        return sb.toString()
    }
}
