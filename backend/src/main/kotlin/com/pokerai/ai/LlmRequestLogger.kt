package com.pokerai.ai

import com.pokerai.model.Action
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LlmRequestLogger {

    var enabled: Boolean = false

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val fileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun log(
        playerName: String,
        handNumber: Int,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        rawResponse: String,
        action: Action,
        reasoning: String?,
        elapsedMs: Long,
        dir: File = File("llm_logs")
    ) {
        if (!enabled) return
        dir.mkdirs()
        val now = LocalDateTime.now()
        val timestamp = now.format(fileFormatter)
        val file = File(dir, "llm_${playerName}_hand${handNumber}_$timestamp.txt")

        val sb = StringBuilder()
        sb.appendLine("LLM Request — $playerName (Hand #$handNumber)")
        sb.appendLine(now.format(formatter))
        sb.appendLine("Model: $model")
        sb.appendLine("Elapsed: ${elapsedMs}ms")
        sb.appendLine()
        sb.appendLine("=== SYSTEM PROMPT ===")
        sb.appendLine(systemPrompt)
        sb.appendLine()
        sb.appendLine("=== USER PROMPT ===")
        sb.appendLine(userPrompt)
        sb.appendLine()
        sb.appendLine("=== RESPONSE ===")
        sb.appendLine(rawResponse)
        sb.appendLine()
        sb.appendLine("=== PARSED ===")
        sb.appendLine("Action: ${action.describe()}")
        if (reasoning != null) {
            sb.appendLine("Reasoning: $reasoning")
        }

        file.writeText(sb.toString())
    }
}
