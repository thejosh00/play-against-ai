package com.pokerai.model.archetype

import com.pokerai.model.PlayerProfile
import com.pokerai.model.Position

data object NitArchetype : PlayerArchetype() {
    override val displayName = "Nit"
    override val weight = 20
    override val aiNames = listOf("Gerald", "Edna", "Herb", "Mildred")

    override fun createProfile(): PlayerProfile = PlayerProfile(
        archetype = this,
        openRaiseProb = randomBetween(0.35, 0.45),
        threeBetProb = randomBetween(0.10, 0.20),
        fourBetProb = randomBetween(0.08, 0.15),
        rangeFuzzProb = randomBetween(0.00, 0.05),
        openRaiseSizeMin = 2.8,
        openRaiseSizeMax = 3.2,
        threeBetSizeMin = 2.8,
        threeBetSizeMax = 3.2,
        fourBetSizeMin = 2.2,
        fourBetSizeMax = 2.4,
        postFlopFoldProb = randomBetween(0.45, 0.55),
        postFlopCallCeiling = randomBetween(0.80, 0.90),
        postFlopCheckProb = randomBetween(0.65, 0.75),
        betSizePotFraction = randomBetween(0.40, 0.55),
        raiseMultiplier = randomBetween(2.0, 2.5)
    )

    override fun getOpenRange(position: Position): Set<String> = when (position) {
        Position.UTG, Position.UTG1 -> setOf(
            "AA", "KK", "QQ", "JJ", "AKs", "AQs", "AKo"
        )
        Position.LJ, Position.MP -> setOf(
            "AA", "KK", "QQ", "JJ", "TT",
            "AKs", "AQs", "AJs", "KQs",
            "AKo", "AQo"
        )
        Position.HJ, Position.CO -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99",
            "AKs", "AQs", "AJs", "ATs", "KQs", "KJs",
            "AKo", "AQo", "AJo"
        )
        Position.BTN -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88",
            "AKs", "AQs", "AJs", "ATs", "KQs", "KJs", "KTs", "QJs",
            "AKo", "AQo", "AJo", "KQo"
        )
        Position.SB -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99",
            "AKs", "AQs", "AJs", "ATs", "KQs", "KJs",
            "AKo", "AQo", "AJo"
        )
        Position.BB -> setOf(
            "AA", "KK", "QQ", "JJ",
            "AKs", "AQs",
            "AKo"
        )
    }

    override fun getFacingRaiseRange(position: Position): Set<String> = setOf(
        "AA", "KK", "QQ", "JJ",
        "AKs", "AQs",
        "AKo"
    )

    override fun getFacing3BetRange(): Set<String> = setOf(
        "AA", "KK", "QQ",
        "AKs", "AKo"
    )

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a tight-passive poker player. Your strategy:
        - You play an extremely narrow range of premium hands only
        - Even when you play a hand, you prefer to call rather than raise
        - You are very risk-averse: if there is significant action, you assume you are beaten
        - You fold to aggression easily unless you have a very strong hand
        - You rarely bluff - almost never
        - When you do raise, you have a near-unbeatable hand
        - You are comfortable folding for long stretches and waiting for premium cards
        - You check when you could bet, and call when you could raise
        - Aggression factor: low. You call slightly more than you bet/raise.
        - You significantly overvalue the risk of losing chips. Preservation is your priority.
        - You fold about ${String.format("%.0f", profile.postFlopFoldProb * 100)}% of the time when facing a bet.
    """.trimIndent()
}
