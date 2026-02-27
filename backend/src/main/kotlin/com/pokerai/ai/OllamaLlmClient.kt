package com.pokerai.ai

import com.pokerai.ai.strategy.ActionDecision
import com.pokerai.model.Action
import com.pokerai.model.GameState
import com.pokerai.model.Player
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import com.pokerai.appJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String,
    val thinking: String? = null
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

@Serializable
data class OllamaChatResponse(
    val message: OllamaMessage? = null,
    val done: Boolean = false,
    val done_reason: String? = null
)

class OllamaLlmClient(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama3.1:8b"
) : LlmClient {

    private val logger = LoggerFactory.getLogger(OllamaLlmClient::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        engine {
            requestTimeout = 60_000
        }
    }

    override suspend fun getEnrichedDecision(
        player: Player,
        state: GameState,
        ctx: DecisionContext,
        codedSuggestion: ActionDecision
    ): AiDecision {
        val systemPrompt = LlmPromptBuilder.buildSystemPrompt(player)
        val userPrompt = LlmPromptBuilder.buildEnrichedUserPrompt(player, state, ctx, codedSuggestion)

        val request = OllamaChatRequest(
            model = model,
            messages = listOf(
                OllamaMessage("system", systemPrompt),
                OllamaMessage("user", userPrompt)
            ),
            stream = false
        )

        return try {
            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseText = response.bodyAsText()
            val ollamaResponse = appJson.decodeFromString<OllamaChatResponse>(responseText)

            val content = ollamaResponse.message?.content
                ?: throw Exception("Empty response from Ollama")

            logger.debug("LLM enriched response for ${player.name}: $content")

            val (action, reasoning) = LlmResponseParser.parseWithReasoning(content, player, state)
            AiDecision(action, reasoning, "llm")
        } catch (e: Exception) {
            logger.warn("Enriched LLM call failed for ${player.name}: ${e.message}, falling back to coded suggestion")
            AiDecision(codedSuggestion.action, null, "llm-fallback")
        }
    }

    override suspend fun getDecision(player: Player, state: GameState): AiDecision {
        val systemPrompt = LlmPromptBuilder.buildSystemPrompt(player)
        val userPrompt = LlmPromptBuilder.buildUserPrompt(player, state)

        val request = OllamaChatRequest(
            model = model,
            messages = listOf(
                OllamaMessage("system", systemPrompt),
                OllamaMessage("user", userPrompt)
            ),
            stream = false
        )

        return try {
            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseText = response.bodyAsText()
            val ollamaResponse = appJson.decodeFromString<OllamaChatResponse>(responseText)

            val content = ollamaResponse.message?.content
                ?: throw Exception("Empty response from Ollama")

            logger.debug("LLM response for ${player.name}: $content")

            val (action, reasoning) = LlmResponseParser.parseWithReasoning(content, player, state)
            AiDecision(action, reasoning, "llm")
        } catch (e: Exception) {
            logger.warn("LLM call failed for ${player.name}: ${e.message}, falling back to coded default")
            val callAmount = state.currentBetLevel - player.currentBet
            val action = if (callAmount > 0) Action.call(minOf(callAmount, player.chips)) else Action.check()
            AiDecision(action, null, "llm-fallback")
        }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/api/tags")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }
}
