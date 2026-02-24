package com.pokerai.ai

import com.pokerai.model.*

/**
 * Player type classification based on observed behavior.
 */
enum class OpponentType {
    LOOSE_AGGRESSIVE,
    LOOSE_PASSIVE,
    TIGHT_AGGRESSIVE,
    TIGHT_PASSIVE,
    UNKNOWN
}

/**
 * Summary of what we know about an opponent.
 */
data class OpponentRead(
    val playerIndex: Int,
    val playerName: String,
    val position: Position,
    val stack: Int,
    val playerType: OpponentType,
    val vpip: Double,
    val pfr: Double,
    val aggressionFrequency: Double,
    val handsObserved: Int,
    val recentNotableAction: String?
)

/**
 * Tracks opponent behavior across hands to build player type reads.
 *
 * Create one instance per game session. Call [recordNewHand] for every hand
 * and [recordAction] for every action.
 */
class OpponentModeler(
    private val minHandsForClassification: Int = 15
) {
    private data class PlayerStats(
        var handsDealt: Int = 0,
        var handsVoluntarilyPlayed: Int = 0,
        var preflopRaises: Int = 0,
        var postflopBets: Int = 0,
        var postflopCalls: Int = 0,
        var postflopChecks: Int = 0,
        var recentNotableAction: String? = null
    )

    private val stats = mutableMapOf<Int, PlayerStats>()

    fun recordNewHand(players: List<Player>) {
        for (player in players) {
            if (!player.isSittingOut) {
                val s = stats.getOrPut(player.index) { PlayerStats() }
                s.handsDealt++
            }
        }
    }

    fun recordAction(playerIndex: Int, action: Action, phase: GamePhase) {
        val s = stats.getOrPut(playerIndex) { PlayerStats() }

        when (phase) {
            GamePhase.PRE_FLOP -> {
                when (action.type) {
                    ActionType.CALL -> s.handsVoluntarilyPlayed++
                    ActionType.RAISE -> {
                        s.handsVoluntarilyPlayed++
                        s.preflopRaises++
                    }
                    ActionType.ALL_IN -> {
                        s.handsVoluntarilyPlayed++
                        s.preflopRaises++
                    }
                    else -> {}
                }
            }
            GamePhase.FLOP, GamePhase.TURN, GamePhase.RIVER -> {
                when (action.type) {
                    ActionType.RAISE, ActionType.ALL_IN -> s.postflopBets++
                    ActionType.CALL -> s.postflopCalls++
                    ActionType.CHECK -> s.postflopChecks++
                    ActionType.FOLD -> {}
                }
            }
            else -> {}
        }
    }

    fun recordNotableAction(playerIndex: Int, description: String) {
        val s = stats.getOrPut(playerIndex) { PlayerStats() }
        s.recentNotableAction = description
    }

    fun getRead(player: Player): OpponentRead {
        val s = stats[player.index]
        val handsObserved = s?.handsDealt ?: 0

        val vpip = if (s != null && s.handsDealt > 0) {
            s.handsVoluntarilyPlayed.toDouble() / s.handsDealt
        } else 0.0

        val pfr = if (s != null && s.handsDealt > 0) {
            s.preflopRaises.toDouble() / s.handsDealt
        } else 0.0

        val totalPostflopActions = (s?.postflopBets ?: 0) + (s?.postflopCalls ?: 0) + (s?.postflopChecks ?: 0)
        val aggressionFrequency = if (totalPostflopActions > 0) {
            (s?.postflopBets ?: 0).toDouble() / totalPostflopActions
        } else 0.0

        val playerType = classifyPlayer(vpip, pfr, aggressionFrequency, handsObserved)

        return OpponentRead(
            playerIndex = player.index,
            playerName = player.name,
            position = player.position,
            stack = player.chips,
            playerType = playerType,
            vpip = vpip,
            pfr = pfr,
            aggressionFrequency = aggressionFrequency,
            handsObserved = handsObserved,
            recentNotableAction = s?.recentNotableAction
        )
    }

    fun getOpponentReads(
        allPlayers: List<Player>,
        excludePlayerIndex: Int
    ): List<OpponentRead> {
        return allPlayers
            .filter { it.index != excludePlayerIndex && !it.isSittingOut && !it.isFolded }
            .map { getRead(it) }
    }

    private fun classifyPlayer(
        vpip: Double,
        pfr: Double,
        aggressionFrequency: Double,
        handsObserved: Int
    ): OpponentType {
        if (handsObserved < minHandsForClassification) return OpponentType.UNKNOWN

        val isLoose = vpip > 0.30
        val isAggressive = aggressionFrequency > 0.40 || pfr > 0.20

        return when {
            isLoose && isAggressive -> OpponentType.LOOSE_AGGRESSIVE
            isLoose && !isAggressive -> OpponentType.LOOSE_PASSIVE
            !isLoose && isAggressive -> OpponentType.TIGHT_AGGRESSIVE
            else -> OpponentType.TIGHT_PASSIVE
        }
    }
}
