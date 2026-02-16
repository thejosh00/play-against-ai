package com.pokerai.dto

import com.pokerai.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ServerMessage {
    @Serializable
    @SerialName("game_state")
    data class GameStateUpdate(
        val phase: GamePhase,
        val communityCards: List<CardDto>,
        val pot: Int,
        val players: List<PlayerDto>,
        val dealerIndex: Int,
        val currentPlayerIndex: Int,
        val isUserTurn: Boolean,
        val minimumRaise: Int,
        val callAmount: Int,
        val handNumber: Int,
        val showAiCards: Boolean,
        val showPlayerTypes: Boolean,
        val ante: Int = 0,
        val gameLabel: String? = null
    ) : ServerMessage()

    @Serializable
    @SerialName("action_performed")
    data class ActionPerformed(
        val playerIndex: Int,
        val playerName: String,
        val action: String,
        val phase: GamePhase
    ) : ServerMessage()

    @Serializable
    @SerialName("hand_result")
    data class HandResult(
        val winners: List<WinnerDto>,
        val allHoleCards: List<HoleCardsDto>,
        val summary: String
    ) : ServerMessage()

    @Serializable
    @SerialName("player_eliminated")
    data class PlayerEliminated(val playerIndex: Int, val playerName: String) : ServerMessage()

    @Serializable
    @SerialName("player_reloaded")
    data class PlayerReloaded(val playerIndex: Int, val playerName: String, val chips: Int) : ServerMessage()

    @Serializable
    @SerialName("player_joined")
    data class PlayerJoined(val playerIndex: Int, val playerName: String, val chips: Int) : ServerMessage()

    @Serializable
    @SerialName("tournament_update")
    data class TournamentUpdate(
        val remainingPlayers: Int,
        val totalPlayers: Int,
        val blindLevel: Int,
        val smallBlind: Int,
        val bigBlind: Int,
        val ante: Int,
        val handsUntilNextLevel: Int
    ) : ServerMessage()

    @Serializable
    @SerialName("tournament_finished")
    data class TournamentFinished(val finishPosition: Int, val totalPlayers: Int) : ServerMessage()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ServerMessage()
}

@Serializable
data class CardDto(val rank: String, val suit: String) {
    companion object {
        fun from(card: Card) = CardDto(card.rank.symbol, card.suit.symbol)
    }
}

@Serializable
data class PlayerDto(
    val index: Int,
    val name: String,
    val chips: Int,
    val currentBet: Int,
    val isFolded: Boolean,
    val isAllIn: Boolean,
    val isSittingOut: Boolean,
    val isDealer: Boolean,
    val holeCards: List<CardDto>?,
    val lastAction: String?,
    val playerType: String?,
    val position: String,
    val isThinking: Boolean = false
)

@Serializable
data class WinnerDto(
    val playerIndex: Int,
    val playerName: String,
    val amount: Int,
    val handDescription: String
)

@Serializable
data class HoleCardsDto(
    val playerIndex: Int,
    val cards: List<CardDto>,
    val mucked: Boolean = false
)

fun GameState.toUpdate(userIndex: Int = 0, gameLabel: String? = null): ServerMessage.GameStateUpdate {
    val userCallAmount = if (currentPlayerIndex == userIndex && currentPlayerIndex >= 0) {
        val player = players[userIndex]
        minOf(currentBetLevel - player.currentBet, player.chips).coerceAtLeast(0)
    } else 0

    return ServerMessage.GameStateUpdate(
        phase = phase,
        communityCards = communityCards.map { CardDto.from(it) },
        pot = pot,
        players = players.map { player ->
            val showCards = when {
                player.isHuman -> true
                showAiCards -> true
                phase == GamePhase.SHOWDOWN || phase == GamePhase.HAND_COMPLETE ->
                    !player.isFolded
                else -> false
            }
            PlayerDto(
                index = player.index,
                name = player.name,
                chips = player.chips,
                currentBet = player.currentBet,
                isFolded = player.isFolded,
                isAllIn = player.isAllIn,
                isSittingOut = player.isSittingOut,
                isDealer = player.index == dealerIndex,
                holeCards = if (showCards && player.holeCards != null) {
                    player.holeCards!!.toList().map { CardDto.from(it) }
                } else null,
                lastAction = player.lastAction?.describe(),
                playerType = if (showPlayerTypes && !player.isHuman) {
                    player.profile?.archetype?.displayName
                } else null,
                position = player.position.label
            )
        },
        dealerIndex = dealerIndex,
        currentPlayerIndex = currentPlayerIndex,
        isUserTurn = currentPlayerIndex == userIndex && phase != GamePhase.WAITING && phase != GamePhase.HAND_COMPLETE && phase != GamePhase.SHOWDOWN,
        minimumRaise = currentBetLevel + minRaise,
        callAmount = userCallAmount,
        handNumber = handNumber,
        showAiCards = showAiCards,
        showPlayerTypes = showPlayerTypes,
        ante = ante,
        gameLabel = gameLabel
    )
}
