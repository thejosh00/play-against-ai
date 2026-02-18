package com.pokerai.ai

import com.pokerai.model.Difficulty
import com.pokerai.model.GameConfig
import com.pokerai.model.GameState
import com.pokerai.model.TournamentState

data class GameContext(
    val isTournament: Boolean,
    val difficulty: Difficulty,
    val antesActive: Boolean,
    val rakeEnabled: Boolean,
    val tournamentStage: TournamentStage? = null
) {
    companion object {
        val NEUTRAL = GameContext(
            isTournament = false,
            difficulty = Difficulty.MEDIUM,
            antesActive = false,
            rakeEnabled = false,
            tournamentStage = null
        )

        fun from(config: GameConfig?, state: GameState, tournamentState: TournamentState? = null): GameContext {
            if (config == null) return NEUTRAL
            return when (config) {
                is GameConfig.CashGame -> GameContext(
                    isTournament = false,
                    difficulty = config.difficulty,
                    antesActive = state.ante > 0,
                    rakeEnabled = config.rakeEnabled
                )
                is GameConfig.Tournament -> {
                    val stage = if (tournamentState != null) {
                        TournamentStage.derive(
                            remainingPlayers = tournamentState.remainingPlayers,
                            totalPlayers = tournamentState.totalPlayers,
                            tableSize = config.tableSize
                        )
                    } else null
                    GameContext(
                        isTournament = true,
                        difficulty = config.difficulty,
                        antesActive = state.ante > 0,
                        rakeEnabled = false,
                        tournamentStage = stage
                    )
                }
            }
        }
    }
}
