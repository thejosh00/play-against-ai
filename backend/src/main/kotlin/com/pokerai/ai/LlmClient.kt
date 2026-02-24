package com.pokerai.ai

import com.pokerai.ai.strategy.ActionDecision
import com.pokerai.model.Action
import com.pokerai.model.GameState
import com.pokerai.model.Player

interface LlmClient {
    suspend fun getDecision(player: Player, state: GameState): Action
    suspend fun isAvailable(): Boolean

    suspend fun getEnrichedDecision(
        player: Player,
        state: GameState,
        ctx: DecisionContext,
        codedSuggestion: ActionDecision
    ): Action
}
