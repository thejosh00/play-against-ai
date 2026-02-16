package com.pokerai.model.archetype

import com.pokerai.model.PlayerProfile
import com.pokerai.model.Position

data object CallingStationArchetype : PlayerArchetype() {
    override val displayName = "Calling Station"
    override val weight = 35
    override val aiNames = listOf("Bobby", "Earl", "Doris", "Gus")

    override fun createProfile(): PlayerProfile = PlayerProfile(
        archetype = this,
        openRaiseProb = randomBetween(0.15, 0.25),
        threeBetProb = randomBetween(0.03, 0.08),
        fourBetProb = randomBetween(0.01, 0.05),
        rangeFuzzProb = randomBetween(0.08, 0.12),
        openRaiseSizeMin = 2.0,
        openRaiseSizeMax = 2.5,
        threeBetSizeMin = 2.3,
        threeBetSizeMax = 2.8,
        fourBetSizeMin = 2.0,
        fourBetSizeMax = 2.3,
        postFlopFoldProb = randomBetween(0.05, 0.15),
        postFlopCallCeiling = randomBetween(0.85, 0.95),
        postFlopCheckProb = randomBetween(0.70, 0.80),
        betSizePotFraction = randomBetween(0.40, 0.55),
        raiseMultiplier = randomBetween(2.0, 2.5)
    )

    override fun getOpenRange(position: Position): Set<String> = when (position) {
        Position.UTG, Position.UTG1 -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44", "33", "22",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s", "A2s",
            "KQs", "KJs", "KTs", "K9s", "QJs", "QTs", "JTs", "T9s", "98s", "87s",
            "AKo", "AQo", "AJo", "ATo", "A9o", "KQo", "KJo", "QJo", "JTo"
        )
        Position.LJ, Position.MP, Position.HJ, Position.CO -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44", "33", "22",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s", "A2s",
            "KQs", "KJs", "KTs", "K9s", "K8s", "K7s", "K6s",
            "QJs", "QTs", "Q9s", "Q8s", "JTs", "J9s", "J8s",
            "T9s", "T8s", "98s", "97s", "87s", "86s", "76s", "75s", "65s", "54s",
            "AKo", "AQo", "AJo", "ATo", "A9o", "A8o",
            "KQo", "KJo", "KTo", "K9o", "QJo", "QTo", "JTo", "T9o"
        )
        Position.BTN, Position.SB -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44", "33", "22",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s", "A2s",
            "KQs", "KJs", "KTs", "K9s", "K8s", "K7s", "K6s", "K5s", "K4s",
            "QJs", "QTs", "Q9s", "Q8s", "Q7s", "JTs", "J9s", "J8s", "J7s",
            "T9s", "T8s", "T7s", "98s", "97s", "96s", "87s", "86s", "76s", "75s", "65s", "64s", "54s", "53s", "43s",
            "AKo", "AQo", "AJo", "ATo", "A9o", "A8o", "A7o", "A6o", "A5o",
            "KQo", "KJo", "KTo", "K9o", "K8o", "QJo", "QTo", "Q9o", "JTo", "J9o", "T9o", "98o"
        )
        Position.BB -> setOf(
            "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44", "33", "22",
            "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s", "A2s",
            "KQs", "KJs", "KTs", "K9s", "K8s", "K7s",
            "QJs", "QTs", "Q9s", "Q8s", "JTs", "J9s", "J8s",
            "T9s", "T8s", "98s", "97s", "87s", "86s", "76s", "65s", "54s",
            "AKo", "AQo", "AJo", "ATo", "A9o", "A8o",
            "KQo", "KJo", "KTo", "QJo", "QTo", "JTo", "T9o"
        )
    }

    override fun getFacingRaiseRange(position: Position): Set<String> = setOf(
        "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "66", "55", "44", "33", "22",
        "AKs", "AQs", "AJs", "ATs", "A9s", "A8s", "A7s", "A6s", "A5s", "A4s", "A3s", "A2s",
        "KQs", "KJs", "KTs", "K9s", "K8s",
        "QJs", "QTs", "Q9s", "JTs", "J9s", "T9s", "T8s", "98s", "97s", "87s", "76s", "65s",
        "AKo", "AQo", "AJo", "ATo", "A9o", "KQo", "KJo", "QJo", "JTo"
    )

    override fun getFacing3BetRange(): Set<String> = setOf(
        "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77",
        "AKs", "AQs", "AJs", "ATs", "A9s",
        "KQs", "KJs", "QJs", "JTs",
        "AKo", "AQo", "AJo", "KQo"
    )

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a loose-passive poker player. Your strategy:
        - You like to see flops with a very wide range of hands
        - You hate folding - if there is any chance you could win, you call
        - You rarely raise; you prefer to just call and see what happens
        - You chase draws even when pot odds are unfavorable because you might hit
        - You call large bets with middle pair, bottom pair, or even ace-high
        - You occasionally raise with only the very strongest hands (top pair top kicker or better)
        - You almost never bluff - when you raise, you have a monster
        - You find reasons to stay in hands: your kicker might be good, you could hit runner-runner
        - Aggression factor: very low. You call about 3-4x more often than you bet/raise.
        - You are stubborn about folding. Fold only when you truly have nothing and face a large bet.
        - You fold only about ${String.format("%.0f", profile.postFlopFoldProb * 100)}% of the time when facing a bet.
    """.trimIndent()
}
