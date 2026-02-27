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
data class OpenRouterMessage(val role: String, val content: String)

@Serializable
data class OpenRouterChatRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val stream: Boolean = false
)

@Serializable
data class OpenRouterChoice(
    val message: OpenRouterMessage? = null
)

@Serializable
data class OpenRouterChatResponse(
    val choices: List<OpenRouterChoice> = emptyList()
)

class OpenRouterLlmClient(
    private val apiKey: String,
    private val model: String = "qwen/qwen3.5-397b-a17b"
) : LlmClient {

    private val logger = LoggerFactory.getLogger(OpenRouterLlmClient::class.java)

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

        val request = OpenRouterChatRequest(
            model = model,
            messages = listOf(
                OpenRouterMessage("system", systemPrompt),
                OpenRouterMessage("user", userPrompt)
            ),
            stream = false
        )

        val start = System.currentTimeMillis()
        return try {
            val content = chatCompletion(request)
            val elapsed = System.currentTimeMillis() - start
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
        } catch (e: Exception) {
            logger.warn("Enriched LLM call failed for ${player.name}: ${e.message}, falling back to coded suggestion")
            AiDecision(codedSuggestion.action, null, "llm-fallback")
        }
    }



    private suspend fun chatCompletion(request: OpenRouterChatRequest): String {
        logger.info("Calling OpenRouter (model=${request.model})...")
        val start = System.currentTimeMillis()
        return withTimeout(60_000L) {
            val response = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(request)
            }

            val responseText = response.bodyAsText()
            val elapsed = System.currentTimeMillis() - start
            logger.info("OpenRouter responded in ${elapsed}ms")
            val parsed = appJson.decodeFromString<OpenRouterChatResponse>(responseText)

            parsed.choices.firstOrNull()?.message?.content
                ?: throw Exception("Empty response from OpenRouter")
        }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            val response = httpClient.get("https://openrouter.ai/api/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }
}
