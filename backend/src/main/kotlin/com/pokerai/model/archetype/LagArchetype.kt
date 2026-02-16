package com.pokerai.model.archetype

import com.pokerai.model.PlayerProfile
import com.pokerai.model.Position

data object LagArchetype : PlayerArchetype() {
    override val displayName = "Loose-Aggressive"
    override val weight = 15
    override val aiNames = listOf("Luna", "Blaze", "Ricky", "Dash")

    override fun createProfile(): PlayerProfile = PlayerProfile(
        archetype = this,
        openRaiseProb = randomBetween(0.85, 0.95),
        threeBetProb = randomBetween(0.40, 0.50),
        fourBetProb = randomBetween(0.30, 0.40),
        rangeFuzzProb = randomBetween(0.06, 0.10),
        openRaiseSizeMin = randomBetween(2.3, 2.7),
        openRaiseSizeMax = randomBetween(3.0, 3.8),
        threeBetSizeMin = randomBetween(2.8, 3.2),
        threeBetSizeMax = randomBetween(3.2, 3.8),
        fourBetSizeMin = 2.3,
        fourBetSizeMax = 2.7,
        postFlopFoldProb = randomBetween(0.10, 0.20),
        postFlopCallCeiling = randomBetween(0.35, 0.45),
        postFlopCheckProb = randomBetween(0.20, 0.30),
        betSizePotFraction = randomBetween(0.65, 0.85),
        raiseMultiplier = randomBetween(2.5, 3.5)
    )

    override fun getOpenRange(position: Position): Set<String> = when (position) {
        Position.UTG, Position.UTG1 -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77",
            "AKs", "AQs", "AJs", "ATs", "A9s", "KQs", "KJs", "QJs", "JTs", "T9s",
            "AKo", "AQo", "AJo", "KQo"
        )
        Position.LJ, Position.MP -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s",
            "KQs", "KJs", "KTs", "K9s", "QJs", "QTs", "JTs", "T9s", "98s",
            "AKo", "AQo", "AJo", "ATo", "KQo", "KJo"
        )
        Position.HJ, Position.CO -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44", "33",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s",
            "KQs", "KJs", "KTs", "K9s", "K8s", "QJs", "QTs", "Q9s",
            "JTs", "J9s", "T9s", "T8s", "98s", "97s", "87s", "86s", "76s",
            "AKo", "AQo", "AJo", "ATo", "A9o", "KQo", "KJo", "QJo", "JTo"
        )
        Position.BTN -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44", "33", "22",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s", "A2s",
            "KQs", "KJs", "KTs", "K9s", "K8s", "K7s", "K6s",
            "QJs", "QTs", "Q9s", "Q8s", "JTs", "J9s", "J8s",
            "T9s", "T8s", "98s", "97s", "87s", "86s", "76s", "75s", "65s", "54s",
            "AKo", "AQo", "AJo", "ATo", "A9o", "A8o", "A7o",
            "KQo", "KJo", "KTo", "QJo", "QTo", "JTo", "T9o"
        )
        Position.SB -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44", "33",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s", "A2s",
            "KQs", "KJs", "KTs", "K9s", "K8s", "QJs", "QTs", "Q9s",
            "JTs", "J9s", "T9s", "T8s", "98s", "97s", "87s", "76s",
            "AKo", "AQo", "AJo", "ATo", "A9o", "KQo", "KJo", "QJo"
        )
        Position.BB -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77",
            "AKs", "AQs", "AJs", "ATs", "A9s", "KQs", "KJs", "QJs", "JTs",
            "AKo", "AQo", "AJo", "KQo"
        )
    }

    override fun getFacingRaiseRange(position: Position): Set<String> = setOf(
        "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77",
        "AKs", "AQs", "AJs", "ATs", "A9s", "A5s", "A4s",
        "KQs", "KJs", "QJs", "JTs", "T9s", "98s",
        "AKo", "AQo", "AJo", "KQo"
    )

    override fun getFacing3BetRange(): Set<String> = setOf(
        "AA", "KK", "QQ", "JJ", "TT", "99",
        "AKs", "AQs", "AJs", "A5s", "A4s",
        "KQs",
        "AKo", "AQo"
    )

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a loose-aggressive poker player. Your strategy:
        - You play a wide range of hands and apply constant pressure
        - You frequently raise and 3-bet to put opponents to difficult decisions
        - You bluff often, especially on boards that favor your perceived range
        - You bet large - ${String.format("%.0f", profile.betSizePotFraction * 100)}%-100% pot sizing is normal for you
        - You attack weakness: if opponents check to you, you almost always bet
        - You are willing to make large bluffs with nothing if the story makes sense
        - You semi-bluff aggressively with any draw
        - Aggression factor: very high. You bet/raise about 4-5x more often than you call.
        - You enjoy putting opponents in uncomfortable spots with large raises.
    """.trimIndent()
}
