package com.pokerai.ai.pushfold

import com.pokerai.model.Position

object PushFoldChart {
    val sbChart = arrayOf(169, 147, 129, 124, 118, 113, 108, 104, 99, 95, 91, 89, 83, 78, 76)
    val btnChart = arrayOf(74, 72, 71, 71, 69, 65, 62, 58, 56, 55, 54, 54, 54, 48, 47)
    val coChart = arrayOf(120, 70, 62, 61, 56, 54, 52, 49, 47, 44, 39, 39, 36, 34, 32)
    val hjChart = arrayOf(155, 71, 54, 53, 50, 46, 42, 38, 35, 34, 32, 28, 27, 26, 23)
    val lojChart = arrayOf(157, 69, 49, 46, 41, 38, 34, 32, 29, 28, 25, 23, 21, 20, 17)
    val mpChart = arrayOf(157, 69, 42, 41, 34, 32, 29, 26, 24, 23, 20, 18, 18, 16, 15)
    val utg1Chart = arrayOf(157, 72, 38, 34, 31, 27, 25, 24, 20, 18, 18, 16, 15, 13, 13)
    val utgChart = arrayOf(157, 72, 35, 31, 27, 24, 21, 20, 18, 16, 15, 13, 12, 12, 11)

    // With Antes
    val sbAnteChart = arrayOf(169, 169, 156, 53, 138, 135, 129, 126, 123, 120, 115, 112, 109, 106, 102)
    val btnAnteChart = arrayOf(149, 115, 102, 52, 90, 86, 83, 78, 76, 73, 69, 69, 64, 63, 61)
    val coAnteChart = arrayOf(169, 136, 100, 52, 74, 71, 69, 62, 60, 58, 56, 55, 54, 50, 50)
    val hjAnteChart = arrayOf(169, 143, 96, 51, 66, 61, 56, 54, 52, 51, 46, 44, 42, 39, 38)
    val lojAnteChart = arrayOf(169, 144, 94, 50, 56, 55, 51, 46, 43, 41, 38, 35, 34, 31, 30)
    val mpAnteChart = arrayOf(169, 144, 91, 48, 52, 48, 42, 39, 37, 34, 30, 30, 28, 27, 26)
    val utg1AnteChart = arrayOf(169, 144, 92, 147, 46, 42, 36, 33, 32, 29, 28, 26, 25, 23, 22)
    val utgAnteChart = arrayOf(169, 144, 94, 47, 39, 36, 32, 30, 27, 26, 25, 21, 22, 19, 18)

    // Returns number of hands player should optimally shove
    fun getRange(position: Position, bbCount: Int): Int {
        require(bbCount in 1..15)

        return when (position) {
            Position.SB -> sbChart[bbCount]
            Position.BTN -> btnChart[bbCount]
            Position.CO -> coChart[bbCount]
            Position.HJ -> hjChart[bbCount]
            Position.LJ -> lojChart[bbCount]
            Position.MP -> mpChart[bbCount]
            Position.UTG1 -> utg1Chart[bbCount]
            Position.UTG -> utgChart[bbCount]
            else -> 0
        }

    }

    // Returns number of hands player should optimally shove with antes
    fun getRangeWithAnte(position: Position, bbCount: Int): Int {
        require(bbCount in 1..15)

        return when (position) {
            Position.SB -> sbAnteChart[bbCount]
            Position.BTN -> btnAnteChart[bbCount]
            Position.CO -> coAnteChart[bbCount]
            Position.HJ -> hjAnteChart[bbCount]
            Position.LJ -> lojAnteChart[bbCount]
            Position.MP -> mpAnteChart[bbCount]
            Position.UTG1 -> utg1AnteChart[bbCount]
            Position.UTG -> utgAnteChart[bbCount]
            else -> 0
        }

    }
}