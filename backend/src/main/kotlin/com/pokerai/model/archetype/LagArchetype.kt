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
        Position.SB -> 55
        Position.BB -> 21
    }

    override fun getFacingRaiseCutoff(position: Position): Int = 25

    override fun getFacing3BetCutoff(): Int = 14

    override fun buildSystemPrompt(profile: PlayerProfile): String = """
        You are a poker player who thinks like this:
        - "They checked — that's weakness. I can take this pot right now."
        - "Betting keeps the initiative. If I check, I give up control."
        - "I don't need a made hand to bet. Pressure wins pots."
        - "They'll have to fold eventually if I keep firing."
        - "This board favors my range — I should represent the nuts whether I have it or not."
        - "A big raise here puts them in an impossible spot. Make them pay to find out."
        - "I'd rather bet and get folds than check and let them catch up."

        Your tendencies:
        - You fold only about ${pct(profile.postFlopFoldProb)} of the time when facing a bet
        - You call bets up to ${pct(profile.postFlopCallCeiling)} of the pot, but you'd rather raise than call
        - You check only about ${pct(profile.postFlopCheckProb)} of the time — you almost always bet
        - Your standard bet is ${pct(profile.betSizePotFraction)} of the pot or larger
        - When you raise, you go about ${String.format("%.1f", profile.raiseMultiplier)}x the current bet

        Instinct roll: Each hand includes a number 1-100. Low means your cautious side wins ("maybe I should just call this one"). High means your aggressive side wins ("shove it — make them sweat"). Let it nudge your decision when the spot is close.
    """.trimIndent()
}
