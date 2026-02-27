package com.pokerai.ai

import com.pokerai.model.*
import org.slf4j.LoggerFactory

class HybridDecisionEngine(
    private val llmClient: LlmClient,
    private val confidenceThreshold: Double = 0.45,
    private val sessionTracker: SessionTracker? = null,
    private val opponentModeler: OpponentModeler? = null
) {
    private val logger = LoggerFactory.getLogger(HybridDecisionEngine::class.java)

    suspend fun decide(player: Player, state: GameState, difficulty: Difficulty? = null): AiDecision {
        val profile = player.profile
        if (profile == null) {
            logger.info("[${player.name}] No profile, sending to LLM (basic)")
            return llmClient.getDecision(player, state)
        }

        val strategy = profile.archetype.getStrategy()
        if (strategy == null) {
            logger.info("[${player.name}] No coded strategy for ${profile.archetype}, sending to LLM (basic)")
            return llmClient.getDecision(player, state)
        }

        val ctx = DecisionContextBuilder.build(
            player, state,
            sessionTracker = sessionTracker,
            opponentModeler = opponentModeler,
            difficulty = difficulty
        )
        val decision = strategy.decide(ctx)

        if (decision.confidence >= confidenceThreshold) {
            logger.debug("[${player.name}] Coded decision: ${decision.action.type} (confidence ${String.format("%.2f", decision.confidence)})")
            return AiDecision(decision.action, decision.reasoning, "coded")
        }

        logger.info("[${player.name}] Low confidence (${String.format("%.2f", decision.confidence)}), sending to LLM (enriched)")
        return llmClient.getEnrichedDecision(player, state, ctx, decision)
    }
}
