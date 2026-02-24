package com.pokerai.eval

import com.pokerai.ai.LlmPromptBuilder
import com.pokerai.ai.Street
import com.pokerai.ai.strategy.ActionDecision
import com.pokerai.model.*
import com.pokerai.model.archetype.PlayerArchetype

/**
 * Builds LLM prompts for evaluation scenarios.
 *
 * Produces the exact same prompt format as the production system
 * so we're testing the LLM under realistic conditions.
 */
object EvalPromptBuilder {

    /**
     * Build a system prompt for a given archetype.
     * Uses the production LlmPromptBuilder internally.
     */
    fun buildSystemPrompt(archetype: PlayerArchetype): String {
        val profile = archetype.createProfile()
        val fakePlayer = createFakePlayer(profile)
        return LlmPromptBuilder.buildSystemPrompt(fakePlayer)
    }

    /**
     * Build a user prompt from an EvalScenario's DecisionContext.
     * Uses the enriched prompt format (same as the hybrid engine fallback).
     */
    fun buildUserPrompt(scenario: EvalScenario): String {
        val profile = scenario.context.profile
        val fakePlayer = createFakePlayer(profile)
        val fakeState = createMinimalGameState(scenario.context)

        return LlmPromptBuilder.buildEnrichedUserPrompt(
            player = fakePlayer,
            state = fakeState,
            ctx = scenario.context,
            codedSuggestion = null
        )
    }

    /**
     * Build a user prompt with a coded suggestion included.
     * Used for testing the "fallback with hint" scenario.
     */
    fun buildUserPromptWithSuggestion(
        scenario: EvalScenario,
        codedSuggestion: ActionDecision
    ): String {
        val profile = scenario.context.profile
        val fakePlayer = createFakePlayer(profile)
        val fakeState = createMinimalGameState(scenario.context)

        return LlmPromptBuilder.buildEnrichedUserPrompt(
            player = fakePlayer,
            state = fakeState,
            ctx = scenario.context,
            codedSuggestion = codedSuggestion
        )
    }

    private fun createFakePlayer(profile: PlayerProfile): Player {
        return Player(
            index = 0,
            name = "Eval Player",
            isHuman = false,
            profile = profile,
            chips = 1000
        )
    }

    private fun createMinimalGameState(ctx: com.pokerai.ai.DecisionContext): GameState {
        return GameState(
            players = emptyList(),
            pot = ctx.potSize,
            phase = when (ctx.street) {
                Street.PREFLOP -> GamePhase.PRE_FLOP
                Street.FLOP -> GamePhase.FLOP
                Street.TURN -> GamePhase.TURN
                Street.RIVER -> GamePhase.RIVER
            },
            bigBlind = 10
        )
    }
}
