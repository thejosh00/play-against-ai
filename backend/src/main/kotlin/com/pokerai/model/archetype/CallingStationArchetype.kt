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
