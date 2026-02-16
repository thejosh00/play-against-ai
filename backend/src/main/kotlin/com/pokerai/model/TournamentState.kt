package com.pokerai.model

import kotlinx.serialization.Serializable

@Serializable
data class BlindLevel(
    val level: Int,
    val smallBlind: Int,
    val bigBlind: Int,
    val ante: Int
)

data class TournamentState(
    val totalPlayers: Int,
    var remainingPlayers: Int,
    val blindStructure: List<BlindLevel>,
    var currentBlindLevelIndex: Int = 0,
    var handsAtCurrentLevel: Int = 0,
    val handsPerLevel: Int,
    val antesEnabled: Boolean
) {
    val currentBlindLevel: BlindLevel get() = blindStructure[currentBlindLevelIndex]

    val handsUntilNextLevel: Int get() =
        if (currentBlindLevelIndex >= blindStructure.lastIndex) 0
        else handsPerLevel - handsAtCurrentLevel

    fun advanceHand() {
        handsAtCurrentLevel++
        if (handsAtCurrentLevel >= handsPerLevel && currentBlindLevelIndex < blindStructure.lastIndex) {
            currentBlindLevelIndex++
            handsAtCurrentLevel = 0
        }
    }

    companion object {
        private val BASE_LEVELS = listOf(
            25 to 50,
            50 to 100,
            75 to 150,
            100 to 200,
            150 to 300,
            200 to 400,
            300 to 600,
            400 to 800,
            500 to 1000,
            600 to 1200,
            800 to 1600,
            1000 to 2000,
            1500 to 3000,
            2000 to 4000,
            3000 to 6000
        )

        fun create(config: GameConfig.Tournament): TournamentState {
            val blindStructure = BASE_LEVELS.mapIndexed { index, (sb, bb) ->
                val ante = if (config.antesEnabled && index >= 3) bb / 10 else 0
                BlindLevel(level = index + 1, smallBlind = sb, bigBlind = bb, ante = ante)
            }

            return TournamentState(
                totalPlayers = config.playerCount,
                remainingPlayers = config.playerCount,
                blindStructure = blindStructure,
                handsPerLevel = config.buyin.handsPerLevel,
                antesEnabled = config.antesEnabled
            )
        }
    }
}
