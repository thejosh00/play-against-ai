package com.pokerai.dto

import com.pokerai.model.ActionType
import com.pokerai.model.GameConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ClientMessage {
    @Serializable
    @SerialName("start_game")
    data class StartGame(
        val playerName: String = "Player",
        val startingChips: Int = 1000,
        val smallBlind: Int = 5,
        val bigBlind: Int = 10,
        val config: GameConfig? = null
    ) : ClientMessage()

    @Serializable
    @SerialName("player_action")
    data class PlayerAction(
        val action: ActionType,
        val amount: Int? = null
    ) : ClientMessage()

    @Serializable
    @SerialName("deal_next_hand")
    data object DealNextHand : ClientMessage()

    @Serializable
    @SerialName("toggle_setting")
    data class ToggleSetting(
        val setting: String,
        val value: Boolean
    ) : ClientMessage()
}
