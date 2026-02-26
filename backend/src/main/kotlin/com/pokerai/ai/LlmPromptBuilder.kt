package com.pokerai.ai

import com.pokerai.ai.strategy.ActionDecision
import com.pokerai.model.*
import kotlin.random.Random

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
            .joinToString(", ") {
                val label = if (it.playerIndex == player.index) "You" else it.playerName
                "$label: ${it.action.describe()}"
            }
            .ifEmpty { "No action yet" }

        val activePlayers = state.players
            .filter { !it.isFolded && !it.isSittingOut }
            .joinToString(", ") {
                val label = if (it.index == player.index) "You" else it.name
                "$label (${it.chips} chips, pos ${it.position.label})"
            }

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
            - Instinct: ${Random.nextInt(1, 101)}

            What is your action?
        """.trimIndent()
    }

    fun buildEnrichedUserPrompt(
        player: Player,
        state: GameState,
        ctx: DecisionContext,
        codedSuggestion: ActionDecision? = null
    ): String {
        val holeCards = player.holeCards?.let {
            "${it.card1.notation} ${it.card2.notation}"
        } ?: "unknown"

        val community = if (state.communityCards.isEmpty()) "None"
        else state.communityCards.joinToString(" ") { it.notation }

        val drawDescription = if (ctx.hand.draws.isEmpty()) "No draws"
        else ctx.hand.draws.joinToString(", ") { draw ->
            "${draw.type.name.lowercase().replace('_', ' ')} (${draw.outs} outs${if (draw.isNut) ", to the nuts" else ""})"
        }

        val suggestedAction = codedSuggestion?.let { suggestion ->
            val actionStr = when (suggestion.action.type) {
                ActionType.FOLD -> "fold"
                ActionType.CHECK -> "check"
                ActionType.CALL -> "call"
                ActionType.RAISE -> "raise to ${suggestion.action.amount}"
                ActionType.ALL_IN -> "all-in"
            }
            "\n        - Instinct suggests: $actionStr (${suggestion.reasoning ?: "no specific reason"})"
        } ?: ""

        return """
            Current game state:

            YOUR HAND:
            - Hole cards: $holeCards
            - Hand strength: ${ctx.hand.tier.name} — ${ctx.hand.madeHandDescription}
            - Draws: $drawDescription
            - Total outs: ${ctx.hand.totalOuts}

            BOARD:
            - Community cards: $community
            - Street: ${ctx.street.name}
            - Board texture: ${ctx.board.description}
            ${buildBoardThreats(ctx)}
            POT & SIZING:
            - Pot size: ${ctx.potSize}
            - Current bet to call: ${ctx.betToCall}
            - Pot odds: ${String.format("%.1f%%", ctx.potOdds * 100)}
            - Bet as fraction of pot: ${String.format("%.0f%%", ctx.betAsFractionOfPot * 100)}
            - Stack-to-pot ratio: ${String.format("%.1f", ctx.spr)}
            - Effective stack: ${ctx.effectiveStack}
            - Suggested sizes: 1/3 pot = ${ctx.suggestedSizes.thirdPot}, 1/2 pot = ${ctx.suggestedSizes.halfPot}, 2/3 pot = ${ctx.suggestedSizes.twoThirdsPot}, pot = ${ctx.suggestedSizes.fullPot}
            - Minimum raise to: ${ctx.suggestedSizes.minRaise}

            YOUR SITUATION:
            - Position: ${ctx.position.label}
            - You have initiative: ${if (ctx.isInitiator) "Yes (you were the aggressor on the previous street)" else "No"}
            - Facing a bet: ${ctx.facingBet}
            - Facing a raise: ${ctx.facingRaise}
            - Pot type: ${ctx.potType.name} (${ctx.actions.numPlayersInPot} players)
            - Bets this street: ${ctx.numBetsThisStreet}

            ACTION HISTORY:
            - Preflop: ${ctx.actions.preflopNarrative}
            - Flop: ${ctx.actions.flopNarrative}
            - Turn: ${ctx.actions.turnNarrative}
            - River: ${ctx.actions.riverNarrative}

            ${buildSessionSection(ctx)}${buildOpponentSection(ctx)}INSTINCT: ${ctx.instinct}$suggestedAction

            What is your action?
        """.trimIndent()
    }

    private fun buildBoardThreats(ctx: DecisionContext): String {
        val board = ctx.board

        val isRiver = ctx.street == Street.RIVER

        val flush = when {
            board.flushCompletedThisStreet -> "Flush completed this street"
            board.flushPossible -> "Flush possible"
            !isRiver && board.flushDrawPossible -> "Flush draw possible"
            else -> "Flush not possible"
        }

        val straight = when {
            board.straightCompletedThisStreet -> "Straight completed this street"
            board.straightPossible -> "Straight possible"
            !isRiver && board.straightDrawHeavy -> "OESD possible"
            !isRiver && board.connected -> "Gutshot straight draw possible"
            else -> "Straight not possible"
        }

        val fullHouse = if (board.fullHousePossible) "Full house possible" else "Full house not possible"

        val pairing = when {
            board.boardPairedThisStreet -> ", Board paired this street"
            board.paired -> ", Board paired"
            else -> ""
        }

        return "- Board threats: $flush, $straight, $fullHouse$pairing\n"
    }

    private fun buildSessionSection(ctx: DecisionContext): String {
        return ctx.sessionStats?.let { stats ->
            val trend = when {
                stats.resultBB > 10 -> "winning (up ${String.format("%.1f", stats.resultBB)} BB)"
                stats.resultBB < -10 -> "losing (down ${String.format("%.1f", -stats.resultBB)} BB)"
                else -> "roughly even"
            }
            val recentEvent = stats.recentShowdowns.firstOrNull()?.let { memory ->
                when (memory.event) {
                    ShowdownEvent.GOT_BLUFFED -> "You were bluffed ${memory.handsAgo} hands ago by ${memory.opponentName}"
                    ShowdownEvent.CALLED_AND_LOST -> "You called and lost ${memory.handsAgo} hands ago"
                    ShowdownEvent.CALLED_AND_WON -> "You called and won ${memory.handsAgo} hands ago"
                    ShowdownEvent.SAW_OPPONENT_BLUFF -> "${memory.opponentName} was caught bluffing ${memory.handsAgo} hands ago"
                    ShowdownEvent.SAW_BIG_POT_LOSS -> "A big pot was lost ${memory.handsAgo} hands ago"
                }
            } ?: "No recent notable showdowns"

            """SESSION:
            - Session trend: $trend over ${stats.handsPlayed} hands
            - Recent event: $recentEvent

            """
        } ?: ""
    }

    private fun buildOpponentSection(ctx: DecisionContext): String {
        if (ctx.opponents.isEmpty()) return ""

        val opponentLines = ctx.opponents.joinToString("\n            ") { opp ->
            val typeStr = if (opp.playerType != OpponentType.UNKNOWN) {
                " — ${opp.playerType.name.lowercase().replace('_', ' ')}"
            } else {
                " — unknown style"
            }
            val notable = opp.recentNotableAction?.let { " ($it)" } ?: ""
            "- ${opp.position.label} (${opp.playerName}): ${opp.stack} chips$typeStr$notable"
        }

        return """OPPONENTS:
            $opponentLines

            """
    }
}
