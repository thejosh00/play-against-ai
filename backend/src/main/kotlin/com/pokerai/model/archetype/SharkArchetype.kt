package com.pokerai.model.archetype

import com.pokerai.ai.GameContext
import com.pokerai.ai.Scenario
import com.pokerai.ai.TournamentStage
import com.pokerai.model.PlayerProfile
import com.pokerai.model.Position

data object SharkArchetype : PlayerArchetype() {
    override val displayName = "Shark"
    override val weight = 10
    override val aiNames = listOf("Sophia", "Alex", "Ivy", "Nolan")

    override fun createProfile(): PlayerProfile = PlayerProfile(
        archetype = this,
        openRaiseProb = randomBetween(0.75, 0.85),
        threeBetProb = randomBetween(0.30, 0.40),
        fourBetProb = randomBetween(0.20, 0.30),
        rangeFuzzProb = randomBetween(0.03, 0.07),
        openRaiseSizeMin = randomBetween(2.0, 2.4),
        openRaiseSizeMax = randomBetween(2.6, 3.0),
        threeBetSizeMin = randomBetween(2.5, 2.9),
        threeBetSizeMax = randomBetween(3.0, 3.6),
        fourBetSizeMin = 2.2,
        fourBetSizeMax = 2.6,
        postFlopFoldProb = randomBetween(0.25, 0.35),
        postFlopCallCeiling = randomBetween(0.55, 0.65),
        postFlopCheckProb = randomBetween(0.40, 0.50),
        betSizePotFraction = randomBetween(0.50, 0.75),
        raiseMultiplier = randomBetween(2.5, 3.5)
    )

    override fun getGameContextAdjustment(context: GameContext, scenario: Scenario): Int {
        var adj = 0
        if (context.antesActive) adj += 3
        if (context.rakeEnabled) adj -= 1
        when (context.tournamentStage) {
            TournamentStage.EARLY -> {}
            TournamentStage.MIDDLE -> adj -= 1
            TournamentStage.BUBBLE -> adj += 2
            TournamentStage.FINAL_TABLE -> {}
            TournamentStage.HEADS_UP -> adj += 3
            null -> {}
        }
        return adj
    }

    override fun getOpenCutoff(position: Position): Int = when (position) {
        Position.UTG, Position.UTG1 -> 14
        Position.LJ, Position.MP -> 22
        Position.HJ, Position.CO -> 36
        Position.BTN -> 53
        Position.SB -> 34
        Position.BB -> 19
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 21

    override fun getFacing3BetCutoff(): Int = 10

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a balanced, GTO-oriented poker player. Your strategy:
        - You play a well-constructed range that is difficult to exploit
        - You mix your actions: sometimes you bet strong hands, sometimes you check them
        - You bluff at a theoretically correct frequency (roughly 1 bluff for every 2 value bets)
        - You consider blockers, board texture, and opponent tendencies
        - You size your bets based on the board texture and range advantage
        - You defend against aggression appropriately - not over-folding, not over-calling
        - You occasionally make thin value bets and well-timed bluffs
        - You adjust to exploit obvious patterns in opponents but default to balanced play
        - Aggression factor: moderate-high. Balanced between betting, raising, and calling.
        - You think about ranges, not just your specific hand.
        - Your typical bet size is around ${String.format("%.0f", profile.betSizePotFraction * 100)}% of the pot.
    """.trimIndent()
}
