package com.pokerai.eval

import com.pokerai.ai.OpenRouterChatResponse
import com.pokerai.ai.OpenRouterMessage
import com.pokerai.appJson
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * OpenRouter-specific chat request that includes temperature and max_tokens.
 * Extends beyond the production OpenRouterChatRequest to support eval-specific needs.
 */
@Serializable
private data class OpenRouterEvalChatRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    val max_tokens: Int? = null
)

/**
 * Eval adapter that calls OpenRouter's API.
 *
 * @param httpClient shared Ktor HttpClient
 * @param apiKey OpenRouter API key
 * @param modelName OpenRouter model identifier (e.g., "meta-llama/llama-3.1-8b-instruct")
 */
class OpenRouterEvalAdapter(
    private val httpClient: HttpClient,
    private val apiKey: String,
    override val modelName: String
) : LlmAdapter {

    override val provider: String = "openrouter"

    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double
    ): String {
        val response = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(OpenRouterEvalChatRequest(
                model = modelName,
                messages = listOf(
                    OpenRouterMessage("system", systemPrompt),
                    OpenRouterMessage("user", userPrompt)
                ),
                temperature = temperature,
                max_tokens = 8192
            ))
        }

        val responseText = response.bodyAsText()
        val parsed = appJson.decodeFromString<OpenRouterChatResponse>(responseText)
        return parsed.choices.firstOrNull()?.message?.content ?: ""
    }
}
