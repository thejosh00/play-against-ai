package com.pokerai.model.archetype

import com.pokerai.ai.GameContext
import com.pokerai.ai.Scenario
import com.pokerai.ai.TournamentStage
import com.pokerai.model.Difficulty
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

    override fun getGameContextAdjustment(context: GameContext, scenario: Scenario): Int {
        var adj = 0
        if (context.rakeEnabled) adj -= 1
        when (context.tournamentStage) {
            TournamentStage.EARLY -> {}
            TournamentStage.MIDDLE -> adj -= 2
            TournamentStage.BUBBLE -> adj -= 3
            TournamentStage.FINAL_TABLE -> adj -= 2
            TournamentStage.HEADS_UP -> adj += 1
            null -> {}
        }
        when (context.difficulty) {
            Difficulty.LOW -> adj -= 1
            Difficulty.HIGH -> adj += 1
            Difficulty.MEDIUM -> {}
        }
        return adj
    }

    override fun getOpenCutoff(position: Position): Int = when (position) {
        Position.UTG, Position.UTG1 -> 7
        Position.LJ, Position.MP -> 11
        Position.HJ, Position.CO -> 15
        Position.BTN -> 19
        Position.SB -> 15
        Position.BB -> 7
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 7

    override fun getFacing3BetCutoff(): Int = 5

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
