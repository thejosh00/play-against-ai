package com.pokerai.ai

import com.pokerai.model.*

object LlmPromptBuilder {

    fun buildSystemPrompt(player: Player): String {
        val profile = player.profile ?: throw IllegalStateException("AI player has no profile")
        val personality = profile.archetype.buildSystemPrompt(profile)

        return """
            You are an AI poker player in a Texas Hold'em cash game.

            $personality

            You must respond with ONLY a JSON object in this exact format:
            {"action": "fold", "amount": null, "reasoning": "brief thought"}

            Valid actions: "fold", "check", "call", "raise", "all_in"
            - "amount" is only needed for "raise" (the total raise-to amount). Set to null for other actions.
            - "reasoning" is your brief internal thought (1-2 sentences max).

            IMPORTANT: Only output the JSON object. No other text.
        """.trimIndent()
    }

    fun buildUserPrompt(player: Player, state: GameState): String {
        val holeCards = player.holeCards?.let {
            "${it.card1.notation} ${it.card2.notation}"
        } ?: "unknown"

        val community = if (state.communityCards.isEmpty()) "None"
        else state.communityCards.joinToString(" ") { it.notation }

        val callAmount = state.currentBetLevel - player.currentBet
        val potOdds = if (callAmount > 0 && state.pot > 0) {
            val odds = callAmount.toDouble() / (state.pot + callAmount)
            String.format("%.1f%%", odds * 100)
        } else "N/A"

        val actionsThisStreet = state.actionHistory
            .filter { it.phase == state.phase }
            .joinToString(", ") { "${it.playerName}: ${it.action.describe()}" }
            .ifEmpty { "No action yet" }

        val activePlayers = state.players
            .filter { !it.isFolded && !it.isSittingOut }
            .joinToString(", ") { "${it.name} (${it.chips} chips, pos ${it.position.label})" }

        return """
            Current game state:
            - Your hole cards: $holeCards
            - Community cards: $community
            - Pot size: ${state.pot}
            - Your chips: ${player.chips}
            - Your position: ${player.position.label}
            - Current bet to call: $callAmount
            - Minimum raise to: ${state.currentBetLevel + state.minRaise}
            - Pot odds: $potOdds
            - Players in hand: $activePlayers
            - Action this street: $actionsThisStreet
            - Suggested bet sizes: 1/3 pot = ${state.pot / 3}, 1/2 pot = ${state.pot / 2}, 2/3 pot = ${state.pot * 2 / 3}, pot = ${state.pot}

            What is your action?
        """.trimIndent()
    }
}
