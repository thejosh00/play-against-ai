package com.pokerai.plugins

import com.pokerai.ai.AiDecisionService
import com.pokerai.ai.OllamaLlmClient
import com.pokerai.session.GameSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Application.configureRouting(ollamaClient: OllamaLlmClient, aiService: AiDecisionService) {
    routing {
        get("/health") {
            val ollamaConnected = ollamaClient.isAvailable()
            call.respondText(
                buildJsonObject {
                    put("status", "ok")
                    put("ollamaConnected", ollamaConnected)
                }.toString(),
                ContentType.Application.Json
            )
        }

        webSocket("/game") {
            val session = GameSession(wsSession = this, aiService = aiService)
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> launch {
                            session.handleMessage(frame.readText())
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error: ${e.message}")
            }
        }
    }
}
