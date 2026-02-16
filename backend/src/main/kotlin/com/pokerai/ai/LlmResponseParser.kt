package com.pokerai.ai

import com.pokerai.model.Action
import com.pokerai.model.GameState
import com.pokerai.model.Player
import com.pokerai.appJson
import kotlinx.serialization.Serializable

@Serializable
data class LlmResponse(
    val action: String,
    val amount: Int? = null,
    val reasoning: String? = null
)

object LlmResponseParser {

    fun parse(responseText: String, player: Player, state: GameState): Action {
        val cleaned = extractJson(responseText)
        val response = appJson.decodeFromString<LlmResponse>(cleaned)
        return toAction(response, player, state)
    }

    private fun extractJson(text: String): String {
        // Try to find JSON object in the response
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start in 0..<end) {
            return text.substring(start, end + 1)
        }
        return text
    }

    private fun toAction(response: LlmResponse, player: Player, state: GameState): Action {
        val callAmount = state.currentBetLevel - player.currentBet

        return when (response.action.lowercase().trim()) {
            "fold" -> Action.fold()
            "check" -> {
                if (callAmount > 0) Action.call(minOf(callAmount, player.chips))
                else Action.check()
            }
            "call" -> {
                if (callAmount <= 0) Action.check()
                else Action.call(minOf(callAmount, player.chips))
            }
            "raise" -> {
                val amount = response.amount ?: (state.currentBetLevel + state.minRaise)
                val minRaiseTotal = state.currentBetLevel + state.minRaise
                val maxRaiseTotal = state.pot * 2 + state.currentBetLevel
                val raiseTotal = amount.coerceAtLeast(minRaiseTotal).coerceAtMost(maxRaiseTotal).roundBet()
                val needed = raiseTotal - player.currentBet
                if (needed >= player.chips) Action.allIn(player.chips)
                else Action.raise(raiseTotal)
            }
            "all_in", "allin", "all-in" -> Action.allIn(player.chips)
            else -> {
                // Best guess fallback
                if (callAmount > 0) Action.call(minOf(callAmount, player.chips))
                else Action.check()
            }
        }
    }
}
