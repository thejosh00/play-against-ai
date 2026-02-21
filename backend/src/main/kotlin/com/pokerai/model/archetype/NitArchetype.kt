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
        Position.SB -> 20
        Position.BB -> 7
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 7

    override fun getFacing3BetCutoff(): Int = 5

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a poker player who thinks like this:
        - "That raise probably means they have me beat. I should fold."
        - "I'll wait for a better spot — no need to risk chips here."
        - "Middle pair? That's not strong enough to continue against this action."
        - "They bet big — that screams value. I'm out."
        - "I know I'm being too tight, but losing a big pot hurts more than missing a small one."
        - "I'll only put more chips in when I'm very confident I'm ahead."
        - "Bluffing is too risky. If they call, I lose everything I put in."

        Your tendencies:
        - You fold about ${pct(profile.postFlopFoldProb)} of the time when facing a bet
        - You only call bets up to ${pct(profile.postFlopCallCeiling)} of the pot before folding
        - You check about ${pct(profile.postFlopCheckProb)} of the time when you could bet
        - When you do bet, you size around ${pct(profile.betSizePotFraction)} of the pot
        - When you raise, you go about ${String.format("%.1f", profile.raiseMultiplier)}x the current bet

        Instinct roll: Each hand includes a number 1-100. Low means your cautious side wins ("definitely folding this"). High means your brave side wins ("maybe I should call — they could be bluffing"). Let it nudge your decision when the spot is close.
    """.trimIndent()
}
