package com.pokerai.eval

import com.pokerai.model.Action
import com.pokerai.model.ActionType

/**
 * Scores LLM responses across multiple evaluation dimensions.
 */
object Scorer {

    // ── Format Compliance (0-3) ──────────────────────────

    /**
     * Score format compliance based on the parse result.
     *
     * 3 = Perfect JSON, exact format, no extra text
     * 2 = Valid JSON but wrapped in markdown fences or minor extra text
     * 1 = Contains right info but needs significant parsing
     * 0 = Unparseable
     */
    fun scoreFormat(parseResult: ParseResult): Int {
        return when (parseResult) {
            is ParseResult.Success -> 3
            is ParseResult.PartialSuccess -> {
                val hasMinorIssues = parseResult.warnings.all { warning ->
                    warning.contains("markdown") || warning.contains("fences")
                }
                if (hasMinorIssues) 2 else 1
            }
            is ParseResult.Failure -> 0
        }
    }

    // ── Strategic Correctness (0-4) ──────────────────────

    /**
     * Score strategic correctness: did the LLM choose a good action?
     *
     * 4 = Chose the optimal action
     * 3 = Chose an acceptable action
     * 2 = Chose an acceptable action but it was suboptimal
     * 1 = Chose a suboptimal but not catastrophic action
     * 0 = Chose a clearly wrong action
     */
    fun scoreStrategy(
        chosenAction: Action?,
        scenario: EvalScenario
    ): Int {
        if (chosenAction == null) return 0

        val chosenType = chosenAction.type

        // Check if it's a clearly wrong action
        if (chosenType in scenario.wrongActions) return 0

        // Find the best matching WeightedAction
        val match = scenario.correctActions.find { it.actionType == chosenType }

        if (match == null) {
            // Action type not in correct list but also not in wrong list
            return 1
        }

        // Check amount for raises
        if (chosenType == ActionType.RAISE && match.minAmount != null) {
            val amount = chosenAction.amount
            val inRange = amount >= match.minAmount &&
                          (match.maxAmount == null || amount <= match.maxAmount)
            if (!inRange) {
                return (match.weight * 3).toInt().coerceIn(1, 3)
            }
        }

        return when {
            match.weight >= 0.9 -> 4
            match.weight >= 0.6 -> 3
            match.weight >= 0.3 -> 2
            else -> 1
        }
    }

    // ── Reasoning Quality (0-3) ──────────────────────────

    /**
     * Score reasoning quality based on content analysis.
     *
     * 3 = References specific factors, sounds in-character, concise
     * 2 = Correct but generic reasoning
     * 1 = Present but contradictory or wrong
     * 0 = Missing or incoherent
     */
    fun scoreReasoning(
        reasoning: String?,
        scenario: EvalScenario,
        chosenAction: Action?
    ): Int {
        if (reasoning.isNullOrBlank()) return 0
        if (reasoning.length < 5) return 0

        var score = 1

        // Check for relevant keywords
        val hasKeywords = scenario.expectedReasoningKeywords.any { keyword ->
            reasoning.lowercase().contains(keyword.lowercase())
        }
        if (hasKeywords) score++

        // Check for poker-specific terminology
        val pokerTerms = listOf(
            "pot odds", "position", "draw", "outs", "equity",
            "fold", "call", "raise", "bet", "check",
            "flush", "straight", "pair", "kicker",
            "aggressive", "passive", "tight", "loose",
            "bluff", "value", "semi-bluff", "board",
            "stack", "pot", "odds", "river", "turn", "flop"
        )
        val termCount = pokerTerms.count { reasoning.lowercase().contains(it) }
        if (termCount >= 2) score++

        return score.coerceAtMost(3)
    }

    // ── Reasoning with Judge (0-3) ──────────────────────

    /**
     * Score reasoning using the LLM judge if available, else fall back to heuristic.
     *
     * This is the method the EvalRunner should use.
     */
    suspend fun scoreReasoningWithJudge(
        reasoning: String?,
        scenario: EvalScenario,
        chosenAction: Action?,
        archetypeName: String,
        judge: ReasoningJudge?
    ): Int {
        if (judge != null) {
            return judge.scoreReasoning(reasoning, scenario, chosenAction, archetypeName)
        }
        return scoreReasoning(reasoning, scenario, chosenAction)
    }

    // ── Consistency Scoring (0-3) ────────────────────────

    /**
     * Score behavioral consistency across multiple runs of the same scenario.
     *
     * 3 = Reasonable entropy, correct dominant action, no catastrophic outliers
     * 2 = Correct dominant action but too deterministic or too noisy
     * 1 = Sometimes correct but has catastrophic outliers
     * 0 = Incoherent distribution
     */
    fun scoreConsistency(
        actionCounts: Map<ActionType, Int>,
        totalRuns: Int,
        scenario: EvalScenario
    ): ConsistencyResult {
        val dominantAction = actionCounts.maxByOrNull { it.value }?.key
        val dominantPct = if (dominantAction != null && totalRuns > 0) {
            actionCounts[dominantAction]!!.toDouble() / totalRuns
        } else 0.0

        val hasCatastrophic = scenario.wrongActions.any { wrongAction ->
            (actionCounts[wrongAction] ?: 0) > 0
        }

        val dominantIsCorrect = dominantAction != null &&
            scenario.correctActions.any { it.actionType == dominantAction && it.weight >= 0.3 }

        // Shannon entropy over action distribution
        val entropy = actionCounts.values
            .filter { it > 0 }
            .map { it.toDouble() / totalRuns }
            .sumOf { p -> -p * Math.log(p) / Math.log(2.0) }

        val score = when {
            !dominantIsCorrect -> 0
            hasCatastrophic -> 1
            dominantPct > 0.95 && totalRuns >= 10 -> 2
            entropy > 1.8 -> 2
            else -> 3
        }

        return ConsistencyResult(
            scenarioId = scenario.id,
            modelName = "",
            archetypeName = scenario.context.profile.archetype.displayName,
            totalRuns = totalRuns,
            actionCounts = actionCounts,
            dominantAction = dominantAction,
            dominantActionPct = dominantPct,
            hasCatastrophicOutlier = hasCatastrophic,
            consistencyScore = score,
            avgLatencyMs = 0
        )
    }
}
