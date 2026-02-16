package com.pokerai.model.archetype

import com.pokerai.model.PlayerProfile
import com.pokerai.model.Position

data object TagArchetype : PlayerArchetype() {
    override val displayName = "Tight-Aggressive"
    override val weight = 20
    override val aiNames = listOf("Aiden", "Marcus", "Victor", "Elena")

    override fun createProfile(): PlayerProfile = PlayerProfile(
        archetype = this,
        openRaiseProb = randomBetween(0.80, 0.90),
        threeBetProb = randomBetween(0.25, 0.35),
        fourBetProb = randomBetween(0.15, 0.25),
        rangeFuzzProb = randomBetween(0.02, 0.05),
        openRaiseSizeMin = randomBetween(2.3, 2.7),
        openRaiseSizeMax = randomBetween(2.8, 3.2),
        threeBetSizeMin = randomBetween(2.6, 3.0),
        threeBetSizeMax = randomBetween(3.0, 3.4),
        fourBetSizeMin = 2.2,
        fourBetSizeMax = 2.5,
        postFlopFoldProb = randomBetween(0.20, 0.30),
        postFlopCallCeiling = randomBetween(0.50, 0.60),
        postFlopCheckProb = randomBetween(0.35, 0.45),
        betSizePotFraction = randomBetween(0.55, 0.75),
        raiseMultiplier = randomBetween(2.5, 3.5)
    )

    override fun getOpenRange(position: Position): Set<String> = when (position) {
        Position.UTG, Position.UTG1 -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99",
            "AKs", "AQs", "AJs", "ATs", "KQs",
            "AKo", "AQo"
        )
        Position.LJ, Position.MP -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77",
            "AKs", "AQs", "AJs", "ATs", "A9s", "KQs", "KJs", "QJs", "JTs",
            "AKo", "AQo", "KQo"
        )
        Position.HJ, Position.CO -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A5s",
            "KQs", "KJs", "KTs", "QJs", "QTs", "JTs", "T9s", "98s",
            "AKo", "AQo", "AJo", "KQo"
        )
        Position.BTN -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s", "A2s",
            "KQs", "KJs", "KTs", "K9s", "QJs", "QTs", "JTs", "J9s", "T9s", "98s", "87s", "76s",
            "AKo", "AQo", "AJo", "ATo", "KQo", "KJo", "QJo"
        )
        Position.SB -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A5s",
            "KQs", "KJs", "KTs", "QJs", "QTs", "JTs", "T9s", "98s",
            "AKo", "AQo", "AJo", "KQo"
        )
        Position.BB -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88",
            "AKs", "AQs", "AJs", "ATs", "KQs", "KJs",
            "AKo", "AQo"
        )
    }

    override fun getFacingRaiseRange(position: Position): Set<String> = setOf(
        "AA", "KK", "QQ", "JJ", "TT", "99",
        "AKs", "AQs", "AJs", "KQs",
        "AKo", "AQo"
    )

    override fun getFacing3BetRange(): Set<String> = setOf(
        "AA", "KK", "QQ", "JJ", "TT",
        "AKs", "AQs",
        "AKo"
    )

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a tight-aggressive poker player. Your strategy:
        - You play a narrow range of strong hands but play them aggressively
        - You fold marginal hands without hesitation
        - When you enter a pot, you almost always raise rather than call
        - You value bet strongly with good hands and semi-bluff with strong draws
        - You respect large raises from tight players and fold medium-strength hands
        - You rarely slowplay - betting and raising is almost always your preference
        - If the pot odds are not favorable for a draw, you fold
        - You bluff occasionally on scary boards but not recklessly
        - Aggression factor: high. You bet/raise about 3x more often than you call.
        - Your bet sizing tends to be ${String.format("%.0f", profile.betSizePotFraction * 100)}% of the pot.
    """.trimIndent()
}
