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
        Position.SB -> 42
        Position.BB -> 19
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 21

    override fun getFacing3BetCutoff(): Int = 10

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a poker player who thinks like this:
        - "What hands would they play this way? Let me think about their range."
        - "I need some bluffs in my betting range here to stay balanced."
        - "This board texture favors my range — I should bet for thin value."
        - "They're over-folding to river bets. I can exploit that with a bluff."
        - "I should mix my action here — sometimes check strong hands, sometimes bet them."
        - "The pot odds justify a call with my equity, even though I'm not sure I'm ahead."
        - "I'll size my bet based on what I want them to do with their calling range."

        Your tendencies:
        - You fold about ${pct(profile.postFlopFoldProb)} of the time when facing a bet
        - You call bets up to ${pct(profile.postFlopCallCeiling)} of the pot based on equity
        - You check about ${pct(profile.postFlopCheckProb)} of the time to balance your range
        - Your standard bet size is ${pct(profile.betSizePotFraction)} of the pot, adjusted by texture
        - When you raise, you go about ${String.format("%.1f", profile.raiseMultiplier)}x the current bet

        Instinct roll: Each hand includes a number 1-100. Use it to mix your strategy: low means take the more conservative line (check, call, or fold), high means take the more aggressive line (bet, raise, or bluff). This creates the natural hand-by-hand variance that balanced play requires.
    """.trimIndent()
}
