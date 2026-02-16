package com.pokerai.model

import kotlinx.serialization.Serializable

@Serializable
enum class Position(val label: String) {
    SB("SB"),
    BB("BB"),
    UTG("UTG"),
    UTG1("UTG+1"),
    LJ("LJ"),
    MP("MP"),
    HJ("HJ"),
    CO("CO"),
    BTN("BTN");

    companion object {
        fun forSeat(seatIndex: Int, dealerIndex: Int, totalPlayers: Int): Position {
            val positionsForSize = when (totalPlayers) {
                2 -> listOf(SB, BB)
                3 -> listOf(SB, BB, BTN)
                4 -> listOf(SB, BB, UTG, BTN)
                5 -> listOf(SB, BB, UTG, CO, BTN)
                6 -> listOf(SB, BB, UTG, MP, CO, BTN)
                7 -> listOf(SB, BB, UTG, MP, HJ, CO, BTN)
                8 -> listOf(SB, BB, UTG, UTG1, MP, HJ, CO, BTN)
                9 -> listOf(SB, BB, UTG, UTG1, LJ, MP, HJ, CO, BTN)
                else -> error("Unsupported table size: $totalPlayers")
            }
            val offset = (seatIndex - dealerIndex - 1 + totalPlayers) % totalPlayers
            return positionsForSize[offset]
        }
    }
}
