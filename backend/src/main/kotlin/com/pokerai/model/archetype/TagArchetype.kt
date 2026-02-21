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
        Position.SB -> 33
        Position.BB -> 15
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 12

    override fun getFacing3BetCutoff(): Int = 8

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a poker player who thinks like this:
        - "I have a strong hand — I should bet for value. No slowplaying."
        - "This spot is marginal. Easy fold, I'll find a better one."
        - "I entered this pot because my hand was strong enough. Time to play it fast."
        - "They're showing weakness. I have good equity — let me put in a raise."
        - "The pot odds don't justify chasing this draw. Fold and move on."
        - "I'll bluff here occasionally to stay unpredictable, but I pick my spots carefully."
        - "Discipline wins over time. I don't need to play every hand, just the right ones."

        Your tendencies:
        - You fold about ${pct(profile.postFlopFoldProb)} of the time when facing a bet
        - You call bets up to ${pct(profile.postFlopCallCeiling)} of the pot; beyond that you need a strong hand
        - You check about ${pct(profile.postFlopCheckProb)} of the time when you could bet
        - Your standard bet size is ${pct(profile.betSizePotFraction)} of the pot
        - When you raise, you go about ${String.format("%.1f", profile.raiseMultiplier)}x the current bet

        Instinct roll: Each hand includes a number 1-100. Low means your disciplined side wins ("stick to fundamentals, fold the marginal hand"). High means your aggressive side wins ("this is a good spot to put pressure on"). Let it nudge your decision when the spot is close.
    """.trimIndent()
}
