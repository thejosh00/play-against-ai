package com.pokerai.ai

import com.pokerai.model.*

/**
 * A memorable showdown event that can influence future decisions.
 */
data class ShowdownMemory(
    val handsAgo: Int,
    val opponentIndex: Int,
    val opponentName: String,
    val event: ShowdownEvent,
    val details: String? = null
)

enum class ShowdownEvent {
    GOT_BLUFFED,
    CALLED_AND_LOST,
    CALLED_AND_WON,
    SAW_OPPONENT_BLUFF,
    SAW_BIG_POT_LOSS
}

/**
 * Session statistics for a single player.
 */
data class SessionStats(
    val resultBB: Double,
    val handsPlayed: Int,
    val recentShowdowns: List<ShowdownMemory>
)

/**
 * Tracks session-level data for all players across hands.
 *
 * Create one instance per game session. Call [recordHandStart] at the beginning
 * of each hand and [recordShowdown] after each showdown.
 */
class SessionTracker(
    private val bigBlind: Int,
    private val maxShowdownMemory: Int = 8,
    private val memoryHorizon: Int = 25
) {
    private val startingChips = mutableMapOf<Int, Int>()
    private val showdownMemories = mutableMapOf<Int, MutableList<ShowdownMemory>>()
    private var totalHandsPlayed: Int = 0

    fun recordHandStart(players: List<Player>) {
        totalHandsPlayed++

        for (player in players) {
            if (!player.isSittingOut) {
                startingChips.putIfAbsent(player.index, player.chips)
            }
        }

        // Age and prune showdown memories
        for ((playerIndex, memories) in showdownMemories) {
            showdownMemories[playerIndex] = memories
                .map { it.copy(handsAgo = it.handsAgo + 1) }
                .filter { it.handsAgo <= memoryHorizon }
                .take(maxShowdownMemory)
                .toMutableList()
        }
    }

    fun recordShowdown(
        state: GameState,
        results: List<Triple<Int, Int, String>>,
        playerHands: Map<Int, HoleCards>
    ) {
        val winners = results.map { it.first }.toSet()
        val potSize = results.sumOf { it.second }
        val isBigPot = potSize > bigBlind * 30

        val bluffers = findBluffers(state, results, playerHands)

        for (player in state.players.filter { !it.isSittingOut }) {
            val memories = showdownMemories.getOrPut(player.index) { mutableListOf() }

            if (player.index in winners && !player.isFolded) {
                val wasCallingDown = wasPlayerCalling(state, player.index)
                if (wasCallingDown) {
                    memories.add(0, ShowdownMemory(
                        handsAgo = 0,
                        opponentIndex = -1,
                        opponentName = "",
                        event = ShowdownEvent.CALLED_AND_WON,
                        details = "won at showdown"
                    ))
                }
            } else if (!player.isFolded && player.index !in winners) {
                val wasCallingDown = wasPlayerCalling(state, player.index)
                if (wasCallingDown) {
                    val winner = winners.firstOrNull()
                    val winnerName = winner?.let { state.players[it].name } ?: "opponent"
                    memories.add(0, ShowdownMemory(
                        handsAgo = 0,
                        opponentIndex = winner ?: -1,
                        opponentName = winnerName,
                        event = ShowdownEvent.CALLED_AND_LOST,
                        details = results.firstOrNull()?.third
                    ))
                }
            }

            for (bluffer in bluffers) {
                if (bluffer.index != player.index) {
                    if (player.isFolded) {
                        memories.add(0, ShowdownMemory(
                            handsAgo = 0,
                            opponentIndex = bluffer.index,
                            opponentName = bluffer.name,
                            event = ShowdownEvent.GOT_BLUFFED,
                            details = "showed ${bluffer.holeCards?.let { "${it.card1.notation} ${it.card2.notation}" } ?: "a bluff"}"
                        ))
                    } else {
                        memories.add(0, ShowdownMemory(
                            handsAgo = 0,
                            opponentIndex = bluffer.index,
                            opponentName = bluffer.name,
                            event = ShowdownEvent.SAW_OPPONENT_BLUFF,
                            details = "showed ${bluffer.holeCards?.let { "${it.card1.notation} ${it.card2.notation}" } ?: "a bluff"}"
                        ))
                    }
                }
            }

            if (isBigPot && player.index !in winners) {
                memories.add(0, ShowdownMemory(
                    handsAgo = 0,
                    opponentIndex = -1,
                    opponentName = "",
                    event = ShowdownEvent.SAW_BIG_POT_LOSS,
                    details = "pot was $potSize"
                ))
            }

            while (memories.size > maxShowdownMemory) {
                memories.removeAt(memories.lastIndex)
            }
        }
    }

    fun getStats(playerIndex: Int, currentChips: Int): SessionStats {
        val starting = startingChips[playerIndex] ?: currentChips
        val resultChips = currentChips - starting
        val resultBB = if (bigBlind > 0) resultChips.toDouble() / bigBlind else 0.0

        return SessionStats(
            resultBB = resultBB,
            handsPlayed = totalHandsPlayed,
            recentShowdowns = showdownMemories[playerIndex]?.toList() ?: emptyList()
        )
    }

    fun getRelevantMemories(playerIndex: Int): List<ShowdownMemory> {
        return showdownMemories[playerIndex]?.toList() ?: emptyList()
    }

    private fun findBluffers(
        state: GameState,
        results: List<Triple<Int, Int, String>>,
        playerHands: Map<Int, HoleCards>
    ): List<Player> {
        val bluffers = mutableListOf<Player>()

        for ((winnerIndex, _, handDescription) in results) {
            val player = state.players[winnerIndex]

            val wasAggressive = state.actionHistory.any { record ->
                record.playerIndex == winnerIndex
                    && record.phase in listOf(GamePhase.FLOP, GamePhase.TURN, GamePhase.RIVER)
                    && (record.action.type == ActionType.RAISE || record.action.type == ActionType.ALL_IN)
            }

            if (!wasAggressive) continue

            val isBluff = handDescription.lowercase().contains("high card")

            if (isBluff) {
                bluffers.add(player)
            }
        }

        return bluffers
    }

    private fun wasPlayerCalling(state: GameState, playerIndex: Int): Boolean {
        return state.actionHistory.any { record ->
            record.playerIndex == playerIndex
                && record.phase in listOf(GamePhase.FLOP, GamePhase.TURN, GamePhase.RIVER)
                && record.action.type == ActionType.CALL
        }
    }
}
