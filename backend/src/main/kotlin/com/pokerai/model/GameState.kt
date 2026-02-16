package com.pokerai.model

import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase {
    WAITING,
    PRE_FLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN,
    HAND_COMPLETE
}

data class GameState(
    val players: List<Player>,
    val communityCards: MutableList<Card> = mutableListOf(),
    var pot: Int = 0,
    val sidePots: MutableList<SidePot> = mutableListOf(),
    var phase: GamePhase = GamePhase.WAITING,
    var dealerIndex: Int = 0,
    var currentPlayerIndex: Int = -1,
    var lastRaiserIndex: Int = -1,
    var currentBetLevel: Int = 0,
    var minRaise: Int = 0,
    var smallBlind: Int = 5,
    var bigBlind: Int = 10,
    var ante: Int = 0,
    var handNumber: Int = 0,
    var showAiCards: Boolean = false,
    var showPlayerTypes: Boolean = false,
    val deck: Deck = Deck(),
    val actionHistory: MutableList<ActionRecord> = mutableListOf()
) {
    val activePlayers: List<Player> get() = players.filter { !it.isFolded && !it.isSittingOut }
    val playersInHand: List<Player> get() = players.filter { !it.isSittingOut }
    val bettingPlayers: List<Player> get() = players.filter { it.isActive }

    fun playerCount(): Int = players.count { !it.isSittingOut }
}

data class SidePot(
    val amount: Int,
    val eligiblePlayerIndices: List<Int>
)

data class ActionRecord(
    val playerIndex: Int,
    val playerName: String,
    val action: Action,
    val phase: GamePhase
)
