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
import kotlinx.coroutines.withTimeout
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
        val userPrompt = LlmPromptBuilder.buildEnrichedUserPrompt(player, state, ctx)

        val request = OllamaChatRequest(
            model = model,
            messages = listOf(
                OllamaMessage("system", systemPrompt),
                OllamaMessage("user", userPrompt)
            ),
            stream = false
        )

        val start = System.currentTimeMillis()
        return try {
            logger.info("Calling Ollama for ${player.name} enriched decision (model=$model)...")
            withTimeout(60_000L) {
                val response = httpClient.post("$baseUrl/api/chat") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                val responseText = response.bodyAsText()
                val elapsed = System.currentTimeMillis() - start
                logger.info("Ollama responded for ${player.name} in ${elapsed}ms")
                val ollamaResponse = appJson.decodeFromString<OllamaChatResponse>(responseText)

                val content = ollamaResponse.message?.content
                    ?: throw Exception("Empty response from Ollama")

                logger.debug("LLM enriched response for ${player.name}: $content")

                val (action, reasoning) = LlmResponseParser.parseWithReasoning(content, player, state)
                LlmRequestLogger.log(
                    playerName = player.name,
                    handNumber = state.handNumber,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    rawResponse = content,
                    action = action,
                    reasoning = reasoning,
                    elapsedMs = elapsed
                )
                AiDecision(action, reasoning, "llm")
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            logger.warn("Enriched LLM call failed for ${player.name} after ${elapsed}ms: ${e.message}, falling back to coded suggestion")
            AiDecision(codedSuggestion.action, null, "llm-fallback")
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
