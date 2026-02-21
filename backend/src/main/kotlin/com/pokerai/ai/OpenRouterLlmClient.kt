package com.pokerai.ai

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
import kotlinx.serialization.SerialName
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
            requestTimeout = 120_000
        }
    }

    override suspend fun getDecision(player: Player, state: GameState): Action {
        val systemPrompt = LlmPromptBuilder.buildSystemPrompt(player)
        val userPrompt = LlmPromptBuilder.buildUserPrompt(player, state)

        val request = OpenRouterChatRequest(
            model = model,
            messages = listOf(
                OpenRouterMessage("system", systemPrompt),
                OpenRouterMessage("user", userPrompt)
            ),
            stream = false
        )

        return try {
            val content = chatCompletion(request)
            logger.debug("LLM response for ${player.name}: $content")
            LlmResponseParser.parse(content, player, state)
        } catch (e: Exception) {
            logger.warn("LLM call failed for ${player.name}: ${e.message}, retrying with simple prompt")
            retrySimple(player, state)
        }
    }

    private suspend fun chatCompletion(request: OpenRouterChatRequest): String {
        val response = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(request)
        }

        val responseText = response.bodyAsText()
        val parsed = appJson.decodeFromString<OpenRouterChatResponse>(responseText)

        return parsed.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from OpenRouter")
    }

    private suspend fun retrySimple(player: Player, state: GameState): Action {
        val callAmount = state.currentBetLevel - player.currentBet
        val prompt = if (callAmount > 0) {
            "Poker: You have ${player.holeCards?.notation ?: "??"}. Board: ${state.communityCards.joinToString(" ") { it.notation }}. " +
            "Pot: ${state.pot}. Call amount: $callAmount. " +
            "Reply with ONLY one word: fold, call, or raise"
        } else {
            "Poker: You have ${player.holeCards?.notation ?: "??"}. Board: ${state.communityCards.joinToString(" ") { it.notation }}. " +
            "Pot: ${state.pot}. " +
            "Reply with ONLY one word: check or raise"
        }

        return try {
            val request = OpenRouterChatRequest(
                model = model,
                messages = listOf(OpenRouterMessage("user", prompt)),
                stream = false
            )

            val content = chatCompletion(request).lowercase().trim()

            when {
                "fold" in content -> Action.fold()
                "raise" in content -> Action.raise(state.currentBetLevel + state.minRaise)
                "call" in content -> Action.call(minOf(callAmount, player.chips))
                "check" in content -> Action.check()
                else -> if (callAmount > 0) Action.call(minOf(callAmount, player.chips)) else Action.check()
            }
        } catch (e: Exception) {
            logger.error("LLM retry also failed for ${player.name}: ${e.message}")
            if (callAmount > 0) Action.call(minOf(callAmount, player.chips)) else Action.check()
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
