package com.pokerai.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameConfigTest {

    @Test
    fun `CashStakes ONE_TWO resolves correct values`() {
        assertEquals(1, CashStakes.ONE_TWO.smallBlind)
        assertEquals(2, CashStakes.ONE_TWO.bigBlind)
        assertEquals(200, CashStakes.ONE_TWO.startingChips)
        assertEquals(3, CashStakes.ONE_TWO.rakeCap)
    }

    @Test
    fun `CashStakes FIVE_TEN resolves correct values`() {
        assertEquals(5, CashStakes.FIVE_TEN.smallBlind)
        assertEquals(10, CashStakes.FIVE_TEN.bigBlind)
        assertEquals(1000, CashStakes.FIVE_TEN.startingChips)
        assertEquals(10, CashStakes.FIVE_TEN.rakeCap)
    }

    @Test
    fun `CashGame difficulty maps correctly`() {
        assertEquals(Difficulty.LOW, GameConfig.CashGame(CashStakes.ONE_TWO).difficulty)
        assertEquals(Difficulty.MEDIUM, GameConfig.CashGame(CashStakes.TWO_FIVE).difficulty)
        assertEquals(Difficulty.HIGH, GameConfig.CashGame(CashStakes.FIVE_TEN).difficulty)
    }

    @Test
    fun `Tournament difficulty maps correctly`() {
        assertEquals(Difficulty.LOW, GameConfig.Tournament(TournamentBuyin.HUNDRED, 45).difficulty)
        assertEquals(Difficulty.MEDIUM, GameConfig.Tournament(TournamentBuyin.FIVE_HUNDRED, 45).difficulty)
        assertEquals(Difficulty.HIGH, GameConfig.Tournament(TournamentBuyin.FIFTEEN_HUNDRED, 45).difficulty)
    }

    @Test
    fun `Tournament rejects invalid player count`() {
        assertFailsWith<IllegalArgumentException> {
            GameConfig.Tournament(TournamentBuyin.HUNDRED, 50)
        }
    }

    @Test
    fun `Tournament accepts valid player counts`() {
        for (count in listOf(6, 45, 180, 1000)) {
            GameConfig.Tournament(TournamentBuyin.HUNDRED, count)
        }
    }

    @Test
    fun `TournamentBuyin has correct hands per level`() {
        assertEquals(8, TournamentBuyin.HUNDRED.handsPerLevel)
        assertEquals(12, TournamentBuyin.FIVE_HUNDRED.handsPerLevel)
        assertEquals(15, TournamentBuyin.FIFTEEN_HUNDRED.handsPerLevel)
    }

    @Test
    fun `CashGame tableSize defaults to 6`() {
        val config = GameConfig.CashGame(CashStakes.ONE_TWO)
        assertEquals(6, config.tableSize)
    }

    @Test
    fun `Tournament tableSize defaults to 6`() {
        val config = GameConfig.Tournament(TournamentBuyin.HUNDRED, 45)
        assertEquals(6, config.tableSize)
    }

    @Test
    fun `CashGame accepts tableSize 9`() {
        val config = GameConfig.CashGame(CashStakes.TWO_FIVE, tableSize = 9)
        assertEquals(9, config.tableSize)
    }

    @Test
    fun `Tournament accepts tableSize 9`() {
        val config = GameConfig.Tournament(TournamentBuyin.HUNDRED, 45, tableSize = 9)
        assertEquals(9, config.tableSize)
    }

    @Test
    fun `CashGame rejects invalid tableSize`() {
        assertFailsWith<IllegalArgumentException> {
            GameConfig.CashGame(CashStakes.ONE_TWO, tableSize = 3)
        }
    }

    @Test
    fun `Tournament rejects invalid tableSize`() {
        assertFailsWith<IllegalArgumentException> {
            GameConfig.Tournament(TournamentBuyin.HUNDRED, 45, tableSize = 10)
        }
    }
}
