package com.pokerai.ai.strategy

import com.pokerai.ai.DecisionContext
import com.pokerai.model.Action

/**
 * The result of a coded strategy decision.
 *
 * @param action the poker action to take, using the existing Action model
 * @param confidence 0.0 to 1.0 — how confident the coded strategy is in this decision.
 *        High confidence (>= threshold) means use the coded decision.
 *        Low confidence (< threshold) means fall back to LLM.
 * @param reasoning optional human-readable explanation (for debugging and LLM fallback context)
 */
data class ActionDecision(
    val action: Action,
    val confidence: Double,
    val reasoning: String? = null
)

/**
 * Interface for coded postflop strategies.
 * Each archetype can provide an implementation.
 */
interface ArchetypeStrategy {
    fun decide(ctx: DecisionContext): ActionDecision
}
