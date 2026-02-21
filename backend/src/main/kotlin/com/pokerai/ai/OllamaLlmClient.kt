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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class OllamaMessage(val role: String, val content: String)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

@Serializable
data class OllamaChatResponse(
    val message: OllamaMessage? = null,
    val done: Boolean = false
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
            requestTimeout = 120_000
        }
    }

    override suspend fun getDecision(player: Player, state: GameState): Action {
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

            LlmResponseParser.parse(content, player, state)
        } catch (e: Exception) {
            logger.warn("LLM call failed for ${player.name}: ${e.message}, retrying with simple prompt")
            retrySimple(player, state)
        }
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
            val request = OllamaChatRequest(
                model = model,
                messages = listOf(OllamaMessage("user", prompt)),
                stream = false
            )

            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseText = response.bodyAsText()
            val ollamaResponse = appJson.decodeFromString<OllamaChatResponse>(responseText)

            val content = ollamaResponse.message?.content?.lowercase()?.trim() ?: ""

            when {
                "fold" in content -> Action.fold()
                "raise" in content -> Action.raise(state.currentBetLevel + state.minRaise)
                "call" in content -> Action.call(minOf(callAmount, player.chips))
                "check" in content -> Action.check()
                else -> if (callAmount > 0) Action.call(minOf(callAmount, player.chips)) else Action.check()
            }
        } catch (e: Exception) {
            logger.error("LLM retry also failed for ${player.name}: ${e.message}")
            // Ultimate fallback
            if (callAmount > 0) Action.call(minOf(callAmount, player.chips)) else Action.check()
        }
    }

    suspend fun isAvailable(): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/api/tags")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }
}
