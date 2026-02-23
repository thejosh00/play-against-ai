package com.pokerai.analysis

import com.pokerai.model.ActionType
import com.pokerai.model.Position

data class StreetAction(
    val playerIndex: Int,
    val playerName: String,
    val position: Position,
    val actionType: ActionType,
    val amount: Int,
    val amountAsPotFraction: Double,
    val isAggressive: Boolean
)

data class ActionAnalysis(
    val preflopActions: List<StreetAction>,
    val flopActions: List<StreetAction>,
    val turnActions: List<StreetAction>,
    val riverActions: List<StreetAction>,

    val preflopAggressor: Int?,
    val flopAggressor: Int?,
    val turnAggressor: Int?,
    val riverAggressor: Int?,
    val currentStreetAggressor: Int?,

    val initiativeHolder: Int?,

    val potType: PotType,
    val numPlayersInPot: Int,

    val numBetsCurrentStreet: Int,

    val preflopNarrative: String,
    val flopNarrative: String,
    val turnNarrative: String,
    val riverNarrative: String,
    val currentStreetNarrative: String
)

enum class PotType {
    HEADS_UP,
    THREE_WAY,
    MULTIWAY
}
