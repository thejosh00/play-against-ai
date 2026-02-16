package com.pokerai

import com.pokerai.ai.AiDecisionService
import com.pokerai.ai.OllamaLlmClient
import com.pokerai.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val ollamaBaseUrl = environment.config.property("ollama.baseUrl").getString()
    val ollamaModel = environment.config.property("ollama.model").getString()
    val ollamaClient = OllamaLlmClient(baseUrl = ollamaBaseUrl, model = ollamaModel)
    val aiService = AiDecisionService(llmClient = ollamaClient)

    configureSerialization()
    configureWebSockets()
    configureCors()
    configureRouting(ollamaClient, aiService)
}
