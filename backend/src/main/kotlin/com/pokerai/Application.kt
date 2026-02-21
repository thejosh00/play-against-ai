package com.pokerai

import com.pokerai.ai.AiDecisionService
import com.pokerai.ai.LlmClient
import com.pokerai.ai.OllamaLlmClient
import com.pokerai.ai.OpenRouterLlmClient
import com.pokerai.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val provider = environment.config.property("llm.provider").getString()

    val llmClient: LlmClient = when (provider) {
        "ollama" -> {
            val baseUrl = environment.config.property("ollama.baseUrl").getString()
            val model = environment.config.property("ollama.model").getString()
            OllamaLlmClient(baseUrl = baseUrl, model = model)
        }
        "openrouter" -> {
            val apiKey = environment.config.property("openrouter.apiKey").getString()
            val model = environment.config.property("openrouter.model").getString()
            OpenRouterLlmClient(apiKey = apiKey, model = model)
        }
        else -> error("Unknown LLM provider: $provider")
    }

    val aiService = AiDecisionService(llmClient = llmClient)

    configureSerialization()
    configureWebSockets()
    configureCors()
    configureRouting(llmClient, aiService)
}
