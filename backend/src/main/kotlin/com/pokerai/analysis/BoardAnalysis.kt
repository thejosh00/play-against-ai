package com.pokerai.analysis

import com.pokerai.model.Rank
import com.pokerai.model.Suit

enum class BoardWetness {
    DRY,
    SEMI_WET,
    WET,
    VERY_WET
}

data class BoardAnalysis(
    val wetness: BoardWetness,

    val monotone: Boolean,
    val twoTone: Boolean,
    val rainbow: Boolean,
    val flushPossible: Boolean,
    val flushDrawPossible: Boolean,
    val dominantSuit: Suit?,

    val paired: Boolean,
    val doublePaired: Boolean,
    val trips: Boolean,
    val connected: Boolean,
    val highlyConnected: Boolean,
    val highCard: Rank,
    val lowCard: Rank,

    val straightPossible: Boolean,
    val straightDrawHeavy: Boolean,
    val fullHousePossible: Boolean,

    val flushCompletedThisStreet: Boolean,
    val straightCompletedThisStreet: Boolean,
    val boardPairedThisStreet: Boolean,

    val description: String
)
