package com.pokerai.eval

/**
 * Adapter for calling an LLM during evaluation.
 *
 * Unlike the production LlmClient, this interface exposes the raw
 * system/user prompts and raw response strings so the eval framework
 * can score format compliance and reasoning quality.
 */
interface LlmAdapter {
    val modelName: String
    val provider: String

    /**
     * Send a system prompt + user prompt to the LLM and return the raw response string.
     *
     * @param systemPrompt the archetype personality + JSON format instructions
     * @param userPrompt the game state description
     * @param temperature sampling temperature (default 0.7)
     * @return the raw string response from the LLM
     */
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.7
    ): String
}
