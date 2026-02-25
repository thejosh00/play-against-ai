package com.pokerai.eval

import com.pokerai.ai.DecisionContext
import com.pokerai.analysis.PotType
import com.pokerai.model.*
import com.pokerai.model.archetype.PlayerArchetype

// ── Scenario Definition ──────────────────────────────────

enum class ScenarioCategory {
    FORMAT_COMPLIANCE,
    ARCHETYPE_FIDELITY,
    STRATEGIC_CORRECTNESS,
    BEHAVIORAL_CONSISTENCY
}

enum class EvalDifficulty { EASY, MEDIUM, HARD, EXPERT }

/**
 * An acceptable action with a correctness weight.
 * weight = 1.0 means optimal, 0.5 means acceptable, 0.0 means wrong.
 */
data class WeightedAction(
    val actionType: ActionType,
    val weight: Double,
    val minAmount: Int? = null,
    val maxAmount: Int? = null
)

/**
 * Expected action distribution for a given archetype, expressed as percentage ranges.
 * Used for fidelity and consistency testing.
 */
data class ActionDistribution(
    val foldPct: ClosedRange<Double>,
    val checkPct: ClosedRange<Double>,
    val callPct: ClosedRange<Double>,
    val raisePct: ClosedRange<Double>
)

/**
 * A single poker scenario for evaluation.
 *
 * Contains the game situation, expected outcomes, and metadata.
 * The DecisionContext is the complete poker situation that gets
 * converted into a prompt for the LLM.
 */
data class EvalScenario(
    val id: String,
    val name: String,
    val description: String,
    val category: ScenarioCategory,
    val difficulty: EvalDifficulty,
    val tags: Set<String>,

    // The poker situation
    val context: DecisionContext,

    // Expected outcomes (for strategic correctness scoring)
    val correctActions: List<WeightedAction>,
    val wrongActions: Set<ActionType> = emptySet(),
    val expectedReasoningKeywords: Set<String> = emptySet(),

    // For fidelity/consistency testing: expected distribution per archetype
    val archetypeDistributions: Map<PlayerArchetype, ActionDistribution>? = null
)

// ── Parse Result ─────────────────────────────────────────

/**
 * Result of attempting to parse an LLM response into a poker action.
 */
sealed class ParseResult {
    /** Perfect parse — valid JSON with correct fields */
    data class Success(
        val action: Action,
        val reasoning: String?,
        val rawResponse: String
    ) : ParseResult()

    /** Parseable but with issues (markdown fences, extra text, wrong field names) */
    data class PartialSuccess(
        val action: Action,
        val reasoning: String?,
        val rawResponse: String,
        val warnings: List<String>
    ) : ParseResult()

    /** Completely unparseable */
    data class Failure(
        val rawResponse: String,
        val error: String
    ) : ParseResult()
}

// ── Scoring ──────────────────────────────────────────────

/**
 * Score for a single scenario run.
 */
data class ScenarioScore(
    val scenarioId: String,
    val modelName: String,
    val archetypeName: String,
    val formatScore: Int,
    val strategyScore: Int,
    val reasoningScore: Int,
    val action: Action?,
    val reasoning: String?,
    val rawResponse: String,
    val parseResult: ParseResult,
    val latencyMs: Long
)

/**
 * Aggregated scores across multiple runs of the same scenario (for consistency testing).
 */
data class ConsistencyResult(
    val scenarioId: String,
    val modelName: String,
    val archetypeName: String,
    val totalRuns: Int,
    val actionCounts: Map<ActionType, Int>,
    val dominantAction: ActionType?,
    val dominantActionPct: Double,
    val hasCatastrophicOutlier: Boolean,
    val consistencyScore: Int,
    val avgLatencyMs: Long
)

/**
 * Per-archetype scores for a single model.
 */
data class ArchetypeScore(
    val archetypeName: String,
    val avgFormatScore: Double,
    val avgStrategyScore: Double,
    val avgReasoningScore: Double,
    val fidelityScore: Double,
    val avgConsistencyScore: Double
)

/**
 * Complete evaluation report for a single model.
 */
data class ModelReport(
    val modelName: String,
    val provider: String,
    val temperature: Double,
    val totalScenarios: Int,
    val totalRuns: Int,

    // Aggregate dimension scores (0-100 scale)
    val formatScore: Double,
    val fidelityScore: Double,
    val strategyScore: Double,
    val reasoningScore: Double,
    val consistencyScore: Double,
    val weightedTotal: Double,

    // Per-archetype breakdown
    val archetypeScores: Map<String, ArchetypeScore>,

    // Per-difficulty breakdown
    val difficultyScores: Map<EvalDifficulty, Double>,

    // Failure analysis
    val parseFailureRate: Double,
    val catastrophicErrorRate: Double,
    val commonFailures: List<String>,

    // All individual scores (for detailed analysis)
    val scenarioScores: List<ScenarioScore>,
    val consistencyResults: List<ConsistencyResult>
)

/**
 * Cross-model comparison report.
 */
data class EvalReport(
    val timestamp: Long,
    val models: List<ModelReport>,
    val scenarioCount: Int,
    val archetypesTested: List<String>,
    val recommendation: String
)
