package com.pokerai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CashStakes(val smallBlind: Int, val bigBlind: Int, val startingChips: Int, val rakeCap: Int, val label: String) {
    ONE_TWO(1, 2, 200, 3, "$1/$2"),
    TWO_FIVE(2, 5, 500, 5, "$2/$5"),
    FIVE_TEN(5, 10, 1000, 10, "$5/$10")
}

@Serializable
enum class TournamentBuyin(val amount: Int, val handsPerLevel: Int, val label: String) {
    HUNDRED(100, 8, "$100"),
    FIVE_HUNDRED(500, 12, "$500"),
    FIFTEEN_HUNDRED(1500, 15, "$1500")
}

@Serializable
enum class Difficulty { LOW, MEDIUM, HIGH }

@Serializable
sealed class GameConfig {
    abstract val difficulty: Difficulty
    abstract val tableSize: Int

    @Serializable
    @SerialName("cash")
    data class CashGame(
        val stakes: CashStakes,
        val rakeEnabled: Boolean = false,
        override val tableSize: Int = 6
    ) : GameConfig() {
        init {
            require(tableSize in setOf(6, 9)) { "Invalid table size: $tableSize" }
        }

        override val difficulty get() = when (stakes) {
            CashStakes.ONE_TWO -> Difficulty.LOW
            CashStakes.TWO_FIVE -> Difficulty.MEDIUM
            CashStakes.FIVE_TEN -> Difficulty.HIGH
        }
    }

    @Serializable
    @SerialName("tournament")
    data class Tournament(
        val buyin: TournamentBuyin,
        val playerCount: Int,
        val antesEnabled: Boolean = false,
        override val tableSize: Int = 6
    ) : GameConfig() {
        init {
            require(playerCount in setOf(6, 45, 180, 1000)) { "Invalid tournament size: $playerCount" }
            require(tableSize in setOf(6, 9)) { "Invalid table size: $tableSize" }
        }

        override val difficulty get() = when (buyin) {
            TournamentBuyin.HUNDRED -> Difficulty.LOW
            TournamentBuyin.FIVE_HUNDRED -> Difficulty.MEDIUM
            TournamentBuyin.FIFTEEN_HUNDRED -> Difficulty.HIGH
        }
    }
}
