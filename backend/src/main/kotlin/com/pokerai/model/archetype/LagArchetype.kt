package com.pokerai.model.archetype

import com.pokerai.ai.GameContext
import com.pokerai.ai.Scenario
import com.pokerai.ai.TournamentStage
import com.pokerai.model.Difficulty
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

    override fun getGameContextAdjustment(context: GameContext, scenario: Scenario): Int {
        var adj = 0
        if (context.antesActive) adj += 2
        when (context.tournamentStage) {
            TournamentStage.EARLY -> {}
            TournamentStage.MIDDLE -> {}
            TournamentStage.BUBBLE -> adj -= 1
            TournamentStage.FINAL_TABLE -> adj += 1
            TournamentStage.HEADS_UP -> adj += 4
            null -> {}
        }
        when (context.difficulty) {
            Difficulty.LOW -> adj += 2
            Difficulty.HIGH -> adj -= 1
            Difficulty.MEDIUM -> {}
        }
        return adj
    }

    override fun getOpenCutoff(position: Position): Int = when (position) {
        Position.UTG, Position.UTG1 -> 22
        Position.LJ, Position.MP -> 33
        Position.HJ, Position.CO -> 49
        Position.BTN -> 63
        Position.SB -> 48
        Position.BB -> 21
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 25

    override fun getFacing3BetCutoff(): Int = 14

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
