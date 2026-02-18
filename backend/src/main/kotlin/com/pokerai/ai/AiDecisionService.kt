package com.pokerai.ai

import com.pokerai.model.*

class AiDecisionService(
    private val preFlopStrategy: PreFlopStrategy = PreFlopStrategy(),
    private val llmClient: LlmClient
) {
    suspend fun decide(player: Player, state: GameState, config: GameConfig? = null, tournamentState: TournamentState? = null): Action {
        val action = if (state.phase == GamePhase.PRE_FLOP) {
            preFlopStrategy.decide(player, state, config, tournamentState)
        } else {
            llmClient.getDecision(player, state)
        }

        return sanitizeAction(action, player, state)
    }

    private fun sanitizeAction(action: Action, player: Player, state: GameState): Action {
        val callAmount = state.currentBetLevel - player.currentBet
        val isFacingBet = callAmount > 0

        return when (action.type) {
            ActionType.FOLD -> {
                if (!isFacingBet) Action.check() else action
            }

            ActionType.CHECK -> {
                if (isFacingBet) Action.call(minOf(callAmount, player.chips)) else action
            }

            ActionType.CALL -> {
                val amount = minOf(callAmount, player.chips)
                if (amount <= 0 && !isFacingBet) Action.check()
                else if (amount >= player.chips) Action.allIn(player.chips)
                else Action.call(amount)
            }

            ActionType.RAISE -> {
                val minRaise = state.currentBetLevel + state.minRaise
                val raiseTotal = action.amount.coerceAtLeast(minRaise)
                val needed = raiseTotal - player.currentBet
                if (needed >= player.chips) {
                    Action.allIn(player.chips)
                } else {
                    Action.raise(raiseTotal)
                }
            }

            ActionType.ALL_IN -> Action.allIn(player.chips)
        }
    }
}
