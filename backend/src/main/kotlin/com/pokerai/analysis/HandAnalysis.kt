package com.pokerai.analysis

enum class HandStrengthTier {
    MONSTER,
    STRONG,
    MEDIUM,
    WEAK,
    NOTHING
}

enum class DrawType {
    NUT_FLUSH_DRAW,
    FLUSH_DRAW,
    OESD,
    GUTSHOT,
    BACKDOOR_FLUSH,
    BACKDOOR_STRAIGHT,
    OVERCARDS
}

data class DrawInfo(
    val type: DrawType,
    val outs: Int,
    val isNut: Boolean
)

data class HandAnalysis(
    val tier: HandStrengthTier,
    val madeHandDescription: String,
    val draws: List<DrawInfo>,
    val totalOuts: Int,
    val madeHand: Boolean,
    val hasNutAdvantage: Boolean
)
