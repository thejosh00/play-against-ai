package com.pokerai.ai

import com.pokerai.model.*
import com.pokerai.model.Difficulty

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
    val recentNotableAction: String?,
    val readSentence: String = ""
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
    private data class HandRecord(
        var voluntarilyPlayed: Boolean = false,
        var preflopRaised: Boolean = false
    )

    private data class PlayerStats(
        var handsDealt: Int = 0,
        var handsVoluntarilyPlayed: Int = 0,
        var preflopRaises: Int = 0,
        var postflopBets: Int = 0,
        var postflopCalls: Int = 0,
        var postflopChecks: Int = 0,
        var recentNotableAction: String? = null,
        val handRecords: MutableList<HandRecord> = mutableListOf()
    )

    private val stats = mutableMapOf<Int, PlayerStats>()

    fun recordNewHand(players: List<Player>) {
        for (player in players) {
            if (!player.isSittingOut) {
                val s = stats.getOrPut(player.index) { PlayerStats() }
                s.handsDealt++
                s.handRecords.add(HandRecord())
            }
        }
    }

    fun recordAction(playerIndex: Int, action: Action, phase: GamePhase) {
        val s = stats.getOrPut(playerIndex) { PlayerStats() }

        when (phase) {
            GamePhase.PRE_FLOP -> {
                when (action.type) {
                    ActionType.CALL -> {
                        s.handsVoluntarilyPlayed++
                        s.handRecords.lastOrNull()?.voluntarilyPlayed = true
                    }
                    ActionType.RAISE -> {
                        s.handsVoluntarilyPlayed++
                        s.preflopRaises++
                        s.handRecords.lastOrNull()?.let {
                            it.voluntarilyPlayed = true
                            it.preflopRaised = true
                        }
                    }
                    ActionType.ALL_IN -> {
                        s.handsVoluntarilyPlayed++
                        s.preflopRaises++
                        s.handRecords.lastOrNull()?.let {
                            it.voluntarilyPlayed = true
                            it.preflopRaised = true
                        }
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

    fun getRead(player: Player, difficulty: Difficulty): OpponentRead {
        val base = getRead(player)
        val s = stats[player.index] ?: return base

        val window = when (difficulty) {
            Difficulty.LOW -> 15
            Difficulty.MEDIUM -> 25
            Difficulty.HIGH -> s.handRecords.size
        }
        val recent = s.handRecords.takeLast(window)
        if (recent.isEmpty()) return base

        val windowVpip = recent.count { it.voluntarilyPlayed }.toDouble() / recent.size
        val windowPfr = recent.count { it.preflopRaised }.toDouble() / recent.size
        val sentence = buildReadSentence(windowVpip, windowPfr, recent.size, difficulty)

        return base.copy(readSentence = sentence)
    }

    fun getOpponentReads(
        allPlayers: List<Player>,
        excludePlayerIndex: Int,
        difficulty: Difficulty?
    ): List<OpponentRead> {
        return allPlayers
            .filter { it.index != excludePlayerIndex && !it.isSittingOut && !it.isFolded }
            .map { if (difficulty != null) getRead(it, difficulty) else getRead(it) }
    }

    private fun buildReadSentence(vpip: Double, pfr: Double, sampleSize: Int, difficulty: Difficulty): String {
        if (sampleSize < 5) return ""

        val vpipPct = (vpip * 100).toInt()
        val pfrPct = (pfr * 100).toInt()

        return when (difficulty) {
            Difficulty.HIGH -> {
                "Plays $vpipPct% of hands and raises preflop $pfrPct% of the time."
            }
            Difficulty.MEDIUM -> {
                val looseness = when {
                    vpip > 0.50 -> "very loose"
                    vpip > 0.30 -> "loose"
                    vpip > 0.20 -> "moderate"
                    vpip > 0.12 -> "tight"
                    else -> "very tight"
                }
                val pfrDesc = when {
                    pfr > 0.25 -> "raises frequently preflop"
                    pfr > 0.15 -> "raises fairly often preflop"
                    pfr > 0.08 -> "raises occasionally preflop"
                    else -> "rarely raises preflop"
                }
                "Seems like a $looseness player who $pfrDesc."
            }
            Difficulty.LOW -> {
                val looseness = when {
                    vpip > 0.50 -> "plays a lot of hands"
                    vpip > 0.30 -> "plays a lot of hands"
                    vpip > 0.20 -> "plays a moderate number of hands"
                    vpip > 0.12 -> "doesn't play many hands"
                    else -> "plays very few hands"
                }
                val raiseNote = when {
                    pfr > 0.25 -> " and raises a lot"
                    pfr > 0.15 -> " and raises pretty often"
                    else -> " and rarely raises preflop"
                }
                "Seems like they $looseness$raiseNote."
            }
        }
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
