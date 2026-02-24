package com.pokerai.eval

import com.pokerai.ai.OllamaChatResponse
import com.pokerai.ai.OllamaMessage
import com.pokerai.appJson
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * Ollama-specific chat request that includes temperature options.
 * Extends beyond the production OllamaChatRequest to support eval-specific needs.
 */
@Serializable
private data class OllamaEvalChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaEvalOptions? = null
)

@Serializable
private data class OllamaEvalOptions(
    val temperature: Double? = null,
    val num_predict: Int? = null
)

/**
 * Eval adapter that calls a local Ollama instance.
 *
 * @param httpClient shared Ktor HttpClient
 * @param baseUrl Ollama API base URL (default: http://localhost:11434)
 * @param modelName the Ollama model name (e.g., "llama3.1:8b", "mistral:7b")
 */
class OllamaEvalAdapter(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:11434",
    override val modelName: String
) : LlmAdapter {

    override val provider: String = "ollama"

    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double
    ): String {
        val response = httpClient.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(OllamaEvalChatRequest(
                model = modelName,
                messages = listOf(
                    OllamaMessage("system", systemPrompt),
                    OllamaMessage("user", userPrompt)
                ),
                stream = false,
                options = OllamaEvalOptions(temperature = temperature, num_predict = 200)
            ))
        }

        val responseText = response.bodyAsText()
        val parsed = appJson.decodeFromString<OllamaChatResponse>(responseText)
        return parsed.message?.content ?: ""
    }
}
