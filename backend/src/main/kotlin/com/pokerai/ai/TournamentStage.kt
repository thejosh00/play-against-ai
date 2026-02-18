package com.pokerai.ai

enum class TournamentStage {
    EARLY,
    MIDDLE,
    BUBBLE,
    FINAL_TABLE,
    HEADS_UP;

    companion object {
        fun derive(remainingPlayers: Int, totalPlayers: Int, tableSize: Int): TournamentStage {
            require(remainingPlayers > 0) { "remainingPlayers must be positive, got $remainingPlayers" }
            require(remainingPlayers <= totalPlayers) { "remainingPlayers ($remainingPlayers) exceeds totalPlayers ($totalPlayers)" }

            if (remainingPlayers == 2) return HEADS_UP
            if (remainingPlayers <= tableSize) return FINAL_TABLE

            val pctRemaining = remainingPlayers.toDouble() / totalPlayers
            return when {
                pctRemaining > 0.60 -> EARLY
                pctRemaining > 0.30 -> MIDDLE
                pctRemaining > 0.15 -> BUBBLE
                else -> FINAL_TABLE
            }
        }
    }
}
