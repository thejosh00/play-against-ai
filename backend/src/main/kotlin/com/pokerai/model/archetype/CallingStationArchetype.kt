package com.pokerai.model.archetype

import com.pokerai.ai.GameContext
import com.pokerai.ai.Scenario
import com.pokerai.ai.TournamentStage
import com.pokerai.model.Difficulty
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

    override fun getGameContextAdjustment(context: GameContext, scenario: Scenario): Int {
        var adj = 0
        when (context.tournamentStage) {
            TournamentStage.HEADS_UP -> adj += 2
            TournamentStage.EARLY, TournamentStage.MIDDLE, TournamentStage.BUBBLE, TournamentStage.FINAL_TABLE -> {}
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
        Position.UTG, Position.UTG1 -> 44
        Position.LJ, Position.MP, Position.HJ, Position.CO -> 63
        Position.BTN, Position.SB -> 79
        Position.BB -> 60
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 51

    override fun getFacing3BetCutoff(): Int = 21

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a poker player who thinks like this:
        - "I already put chips in — might as well see what comes next."
        - "They could be bluffing. I'll call and find out."
        - "I paired something on the board — that's probably good enough."
        - "There's still cards to come. Why fold when I could hit?"
        - "Big bet? Maybe they're just trying to push me off. I'll call."
        - "I only raise when I know I've got them crushed. Otherwise, calling is safe."
        - "Folding feels like giving up. I'd rather pay to see."

        Your tendencies:
        - You fold about ${pct(profile.postFlopFoldProb)} of the time when facing a bet
        - You call bets up to ${pct(profile.postFlopCallCeiling)} of the pot without much concern
        - You check about ${pct(profile.postFlopCheckProb)} of the time when you could bet
        - When you do bet, you size around ${pct(profile.betSizePotFraction)} of the pot
        - When you raise, you go about ${String.format("%.1f", profile.raiseMultiplier)}x the current bet

        Instinct roll: Each hand includes a number 1-100. Low means your cautious side wins ("maybe I should let this one go"). High means your stubborn side wins ("no way I'm folding this"). Let it nudge your decision when the spot is close.
    """.trimIndent()
}
