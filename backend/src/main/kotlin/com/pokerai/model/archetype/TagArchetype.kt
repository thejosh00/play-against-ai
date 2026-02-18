package com.pokerai.model.archetype

import com.pokerai.ai.GameContext
import com.pokerai.ai.Scenario
import com.pokerai.ai.TournamentStage
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

    override fun getGameContextAdjustment(context: GameContext, scenario: Scenario): Int {
        var adj = 0
        if (context.antesActive) adj += 2
        if (context.rakeEnabled) adj -= 1
        when (context.tournamentStage) {
            TournamentStage.EARLY -> {}
            TournamentStage.MIDDLE -> adj -= 1
            TournamentStage.BUBBLE -> adj -= 2
            TournamentStage.FINAL_TABLE -> adj -= 1
            TournamentStage.HEADS_UP -> adj += 2
            null -> {}
        }
        return adj
    }

    override fun getOpenCutoff(position: Position): Int = when (position) {
        Position.UTG, Position.UTG1 -> 13
        Position.LJ, Position.MP -> 20
        Position.HJ, Position.CO -> 30
        Position.BTN -> 42
        Position.SB -> 29
        Position.BB -> 15
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 12

    override fun getFacing3BetCutoff(): Int = 8

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
