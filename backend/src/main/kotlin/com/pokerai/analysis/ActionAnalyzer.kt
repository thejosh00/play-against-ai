package com.pokerai.analysis

import com.pokerai.model.*

object ActionAnalyzer {

    fun analyze(state: GameState, playerIndex: Int): ActionAnalysis {
        val totalPlayers = state.playerCount()
        val dealerIndex = state.dealerIndex

        val preflopRecords = state.actionHistory.filter { it.phase == GamePhase.PRE_FLOP }
        val flopRecords = state.actionHistory.filter { it.phase == GamePhase.FLOP }
        val turnRecords = state.actionHistory.filter { it.phase == GamePhase.TURN }
        val riverRecords = state.actionHistory.filter { it.phase == GamePhase.RIVER }

        var runningPot = state.smallBlind + state.bigBlind + (state.ante * totalPlayers)
        val preflopPlayerBets = findBlindBets(state)

        val (preflopActions, postPreflopPot) = buildStreetActions(
            preflopRecords, dealerIndex, totalPlayers, runningPot, state.bigBlind, preflopPlayerBets
        )
        runningPot = postPreflopPot

        val (flopActions, postFlopPot) = buildStreetActions(
            flopRecords, dealerIndex, totalPlayers, runningPot, 0
        )
        runningPot = postFlopPot

        val (turnActions, postTurnPot) = buildStreetActions(
            turnRecords, dealerIndex, totalPlayers, runningPot, 0
        )
        runningPot = postTurnPot

        val (riverActions, _) = buildStreetActions(
            riverRecords, dealerIndex, totalPlayers, runningPot, 0
        )

        val preflopAggressor = findAggressor(preflopActions)
        val flopAggressor = findAggressor(flopActions)
        val turnAggressor = findAggressor(turnActions)
        val riverAggressor = findAggressor(riverActions)

        val currentStreetAggressor = when (state.phase) {
            GamePhase.PRE_FLOP -> preflopAggressor
            GamePhase.FLOP -> flopAggressor
            GamePhase.TURN -> turnAggressor
            GamePhase.RIVER -> riverAggressor
            else -> riverAggressor ?: turnAggressor ?: flopAggressor ?: preflopAggressor
        }

        val initiativeHolder = findInitiativeHolder(
            state.phase, preflopAggressor, flopAggressor, turnAggressor, riverAggressor
        )

        val foldedPlayers = state.actionHistory
            .filter { it.action.type == ActionType.FOLD }
            .map { it.playerIndex }
            .toSet()
        val numPlayersInPot = totalPlayers - foldedPlayers.size

        val potType = when {
            numPlayersInPot <= 2 -> PotType.HEADS_UP
            numPlayersInPot == 3 -> PotType.THREE_WAY
            else -> PotType.MULTIWAY
        }

        val currentStreetActions = when (state.phase) {
            GamePhase.PRE_FLOP -> preflopActions
            GamePhase.FLOP -> flopActions
            GamePhase.TURN -> turnActions
            GamePhase.RIVER -> riverActions
            else -> emptyList()
        }
        val numBetsCurrentStreet = currentStreetActions.count { it.isAggressive }

        val preflopNarrative = formatNarrative(preflopActions, playerIndex, isPreflop = true)
        val flopNarrative = formatNarrative(flopActions, playerIndex, isPreflop = false)
        val turnNarrative = formatNarrative(turnActions, playerIndex, isPreflop = false)
        val riverNarrative = formatNarrative(riverActions, playerIndex, isPreflop = false)

        val currentStreetNarrative = when (state.phase) {
            GamePhase.PRE_FLOP -> preflopNarrative
            GamePhase.FLOP -> flopNarrative
            GamePhase.TURN -> turnNarrative
            GamePhase.RIVER -> riverNarrative
            else -> ""
        }

        return ActionAnalysis(
            preflopActions = preflopActions,
            flopActions = flopActions,
            turnActions = turnActions,
            riverActions = riverActions,
            preflopAggressor = preflopAggressor,
            flopAggressor = flopAggressor,
            turnAggressor = turnAggressor,
            riverAggressor = riverAggressor,
            currentStreetAggressor = currentStreetAggressor,
            initiativeHolder = initiativeHolder,
            potType = potType,
            numPlayersInPot = numPlayersInPot,
            numBetsCurrentStreet = numBetsCurrentStreet,
            preflopNarrative = preflopNarrative,
            flopNarrative = flopNarrative,
            turnNarrative = turnNarrative,
            riverNarrative = riverNarrative,
            currentStreetNarrative = currentStreetNarrative
        )
    }

    private fun findBlindBets(state: GameState): Map<Int, Int> {
        val totalPlayers = state.playerCount()
        val activePlayers = state.players.filter { !it.isSittingOut }
        val bets = mutableMapOf<Int, Int>()
        for (player in activePlayers) {
            val position = Position.forSeat(player.index, state.dealerIndex, totalPlayers)
            when (position) {
                Position.SB -> bets[player.index] = state.smallBlind
                Position.BB -> bets[player.index] = state.bigBlind
                else -> {}
            }
        }
        return bets
    }

    private fun buildStreetActions(
        records: List<ActionRecord>,
        dealerIndex: Int,
        totalPlayers: Int,
        initialPot: Int,
        initialBetLevel: Int,
        initialPlayerBets: Map<Int, Int> = emptyMap()
    ): Pair<List<StreetAction>, Int> {
        var runningPot = initialPot
        var betLevel = initialBetLevel
        val playerStreetBets = initialPlayerBets.toMutableMap()
        val actions = mutableListOf<StreetAction>()

        for (record in records) {
            val position = Position.forSeat(record.playerIndex, dealerIndex, totalPlayers)
            val action = record.action
            val playerPreviousBet = playerStreetBets[record.playerIndex] ?: 0

            val isAggressive: Boolean
            val amount: Int
            val potFraction: Double

            when (action.type) {
                ActionType.RAISE -> {
                    isAggressive = true
                    amount = action.amount
                    val raiseSize = amount - betLevel
                    potFraction = if (runningPot > 0) raiseSize.toDouble() / runningPot else 0.0
                    val increment = amount - playerPreviousBet
                    runningPot += increment
                    betLevel = amount
                    playerStreetBets[record.playerIndex] = amount
                }
                ActionType.CALL -> {
                    isAggressive = false
                    amount = action.amount
                    potFraction = if (runningPot > 0) amount.toDouble() / runningPot else 0.0
                    runningPot += amount
                    playerStreetBets[record.playerIndex] = playerPreviousBet + amount
                }
                ActionType.ALL_IN -> {
                    amount = action.amount
                    val totalBet = playerPreviousBet + amount
                    isAggressive = totalBet > betLevel
                    if (isAggressive) {
                        val raiseSize = totalBet - betLevel
                        potFraction = if (runningPot > 0) raiseSize.toDouble() / runningPot else 0.0
                        betLevel = totalBet
                    } else {
                        potFraction = if (runningPot > 0) amount.toDouble() / runningPot else 0.0
                    }
                    runningPot += amount
                    playerStreetBets[record.playerIndex] = totalBet
                }
                ActionType.CHECK, ActionType.FOLD -> {
                    isAggressive = false
                    amount = 0
                    potFraction = 0.0
                }
            }

            actions.add(
                StreetAction(
                    playerIndex = record.playerIndex,
                    playerName = record.playerName,
                    position = position,
                    actionType = action.type,
                    amount = amount,
                    amountAsPotFraction = potFraction,
                    isAggressive = isAggressive
                )
            )
        }

        return Pair(actions, runningPot)
    }

    private fun findAggressor(actions: List<StreetAction>): Int? {
        return actions.lastOrNull { it.isAggressive }?.playerIndex
    }

    private fun findInitiativeHolder(
        currentPhase: GamePhase,
        preflopAggressor: Int?,
        flopAggressor: Int?,
        turnAggressor: Int?,
        riverAggressor: Int?
    ): Int? {
        return when (currentPhase) {
            GamePhase.PRE_FLOP -> null
            GamePhase.FLOP -> preflopAggressor
            GamePhase.TURN -> flopAggressor ?: preflopAggressor
            GamePhase.RIVER -> turnAggressor ?: flopAggressor ?: preflopAggressor
            else -> riverAggressor ?: turnAggressor ?: flopAggressor ?: preflopAggressor
        }
    }

    private fun formatNarrative(
        actions: List<StreetAction>,
        playerIndex: Int,
        isPreflop: Boolean
    ): String {
        if (actions.isEmpty()) return ""

        var aggressiveCount = 0
        val parts = actions.map { action ->
            val youLabel = if (action.playerIndex == playerIndex) " (You)" else ""
            val posLabel = "${action.position.label}$youLabel"

            when (action.actionType) {
                ActionType.FOLD -> "$posLabel folds"
                ActionType.CHECK -> "$posLabel checks"
                ActionType.CALL -> "$posLabel calls \$${action.amount}"
                ActionType.RAISE -> {
                    val verb = if (isPreflop || aggressiveCount > 0) "raises to" else "bets"
                    aggressiveCount++
                    "$posLabel $verb \$${action.amount}"
                }
                ActionType.ALL_IN -> {
                    if (action.isAggressive) {
                        aggressiveCount++
                        "$posLabel goes all-in \$${action.amount}"
                    } else {
                        "$posLabel calls all-in \$${action.amount}"
                    }
                }
            }
        }

        return parts.joinToString(", ")
    }
}
