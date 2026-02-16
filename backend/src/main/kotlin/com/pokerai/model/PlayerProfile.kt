package com.pokerai.model

import com.pokerai.model.archetype.PlayerArchetype

data class PlayerProfile(
    val archetype: PlayerArchetype,
    // Pre-flop probabilities
    val openRaiseProb: Double,
    val threeBetProb: Double,
    val fourBetProb: Double,
    val rangeFuzzProb: Double,
    // Pre-flop sizing (multipliers)
    val openRaiseSizeMin: Double,
    val openRaiseSizeMax: Double,
    val threeBetSizeMin: Double,
    val threeBetSizeMax: Double,
    val fourBetSizeMin: Double,
    val fourBetSizeMax: Double,
    // Post-flop probabilities
    val postFlopFoldProb: Double,
    val postFlopCallCeiling: Double,
    val postFlopCheckProb: Double,
    // Post-flop sizing
    val betSizePotFraction: Double,
    val raiseMultiplier: Double
)
