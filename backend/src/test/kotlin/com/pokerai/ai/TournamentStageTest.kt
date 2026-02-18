package com.pokerai.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TournamentStageTest {

    // --- Boundary tests at percentage thresholds ---

    @Test
    fun `61 percent remaining is EARLY`() {
        // 61 of 100 = 61%
        assertEquals(TournamentStage.EARLY, TournamentStage.derive(61, 100, 6))
    }

    @Test
    fun `60 percent remaining is MIDDLE`() {
        // 60 of 100 = 60%
        assertEquals(TournamentStage.MIDDLE, TournamentStage.derive(60, 100, 6))
    }

    @Test
    fun `31 percent remaining is MIDDLE`() {
        // 31 of 100 = 31%
        assertEquals(TournamentStage.MIDDLE, TournamentStage.derive(31, 100, 6))
    }

    @Test
    fun `30 percent remaining is BUBBLE`() {
        // 30 of 100 = 30%
        assertEquals(TournamentStage.BUBBLE, TournamentStage.derive(30, 100, 6))
    }

    @Test
    fun `16 percent remaining is BUBBLE`() {
        // 16 of 100 = 16%
        assertEquals(TournamentStage.BUBBLE, TournamentStage.derive(16, 100, 6))
    }

    @Test
    fun `15 percent remaining is FINAL_TABLE`() {
        // 15 of 100 = 15%
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(15, 100, 6))
    }

    // --- HEADS_UP always wins for exactly 2 players ---

    @Test
    fun `2 players is always HEADS_UP regardless of total`() {
        assertEquals(TournamentStage.HEADS_UP, TournamentStage.derive(2, 6, 6))
        assertEquals(TournamentStage.HEADS_UP, TournamentStage.derive(2, 180, 6))
        assertEquals(TournamentStage.HEADS_UP, TournamentStage.derive(2, 1000, 9))
    }

    // --- tableSize triggers FINAL_TABLE ---

    @Test
    fun `remaining at tableSize is FINAL_TABLE`() {
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(6, 180, 6))
    }

    @Test
    fun `remaining below tableSize but above 2 is FINAL_TABLE`() {
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(4, 180, 6))
    }

    // --- 6-player SNG starts at FINAL_TABLE ---

    @Test
    fun `6-player SNG starts at FINAL_TABLE`() {
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(6, 6, 6))
    }

    @Test
    fun `6-player SNG with 5 remaining is still FINAL_TABLE`() {
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(5, 6, 6))
    }

    @Test
    fun `6-player SNG with 3 remaining is FINAL_TABLE`() {
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(3, 6, 6))
    }

    // --- 45-player tournament progression ---

    @Test
    fun `45-player tournament progression`() {
        val tableSize = 6
        val total = 45

        // 45 of 45 = 100% -> EARLY
        assertEquals(TournamentStage.EARLY, TournamentStage.derive(45, total, tableSize))

        // 28 of 45 = 62% -> EARLY
        assertEquals(TournamentStage.EARLY, TournamentStage.derive(28, total, tableSize))

        // 27 of 45 = 60% -> MIDDLE
        assertEquals(TournamentStage.MIDDLE, TournamentStage.derive(27, total, tableSize))

        // 14 of 45 = 31% -> MIDDLE
        assertEquals(TournamentStage.MIDDLE, TournamentStage.derive(14, total, tableSize))

        // 13 of 45 = 28.9% -> BUBBLE
        assertEquals(TournamentStage.BUBBLE, TournamentStage.derive(13, total, tableSize))

        // 7 of 45 = 15.6% -> BUBBLE
        assertEquals(TournamentStage.BUBBLE, TournamentStage.derive(7, total, tableSize))

        // 6 of 45 -> <=tableSize -> FINAL_TABLE
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(6, total, tableSize))

        // 3 of 45 -> FINAL_TABLE
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(3, total, tableSize))

        // 2 of 45 -> HEADS_UP
        assertEquals(TournamentStage.HEADS_UP, TournamentStage.derive(2, total, tableSize))
    }

    // --- Validation ---

    @Test
    fun `remaining greater than total throws`() {
        assertFailsWith<IllegalArgumentException> {
            TournamentStage.derive(10, 5, 6)
        }
    }

    @Test
    fun `0 remaining throws`() {
        assertFailsWith<IllegalArgumentException> {
            TournamentStage.derive(0, 100, 6)
        }
    }

    @Test
    fun `negative remaining throws`() {
        assertFailsWith<IllegalArgumentException> {
            TournamentStage.derive(-1, 100, 6)
        }
    }

    @Test
    fun `1 remaining is valid`() {
        // Edge case: single player left (tournament won)
        // 1 <= tableSize(6) but not ==2, so FINAL_TABLE
        assertEquals(TournamentStage.FINAL_TABLE, TournamentStage.derive(1, 100, 6))
    }
}
