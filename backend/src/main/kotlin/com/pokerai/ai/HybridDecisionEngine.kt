package com.pokerai.ai

import com.pokerai.model.*

class HybridDecisionEngine(
    private val llmClient: LlmClient,
    private val confidenceThreshold: Double = 0.45,
    private val sessionTracker: SessionTracker? = null,
    private val opponentModeler: OpponentModeler? = null
) {
    suspend fun decide(player: Player, state: GameState): AiDecision {
        val profile = player.profile
            ?: return llmClient.getDecision(player, state)

        val strategy = profile.archetype.getStrategy()
            ?: return llmClient.getDecision(player, state)

        val ctx = DecisionContextBuilder.build(
            player, state,
            sessionTracker = sessionTracker,
            opponentModeler = opponentModeler
        )
        val decision = strategy.decide(ctx)

        if (decision.confidence >= confidenceThreshold) {
            return AiDecision(decision.action, decision.reasoning, "coded")
        }

        return llmClient.getEnrichedDecision(player, state, ctx, decision)
    }
}
