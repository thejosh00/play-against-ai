package com.pokerai.ai

import com.pokerai.model.*

class HybridDecisionEngine(
    private val llmClient: LlmClient,
    private val confidenceThreshold: Double = 0.45
) {
    suspend fun decide(player: Player, state: GameState): Action {
        val profile = player.profile
            ?: return llmClient.getDecision(player, state)

        val strategy = profile.archetype.getStrategy()
            ?: return llmClient.getDecision(player, state)

        val ctx = DecisionContextBuilder.build(player, state)
        val decision = strategy.decide(ctx)

        if (decision.confidence >= confidenceThreshold) {
            return decision.action
        }

        return llmClient.getEnrichedDecision(player, state, ctx, decision)
    }
}
