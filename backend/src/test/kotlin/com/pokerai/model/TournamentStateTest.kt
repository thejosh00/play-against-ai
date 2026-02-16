package com.pokerai.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TournamentStateTest {

    private fun createState(
        buyin: TournamentBuyin = TournamentBuyin.HUNDRED,
        playerCount: Int = 45,
        antesEnabled: Boolean = false
    ): TournamentState {
        val config = GameConfig.Tournament(buyin, playerCount, antesEnabled)
        return TournamentState.create(config)
    }

    @Test
    fun `creates 15 blind levels`() {
        val ts = createState()
        assertEquals(15, ts.blindStructure.size)
    }

    @Test
    fun `first level is 25-50`() {
        val ts = createState()
        val level = ts.blindStructure[0]
        assertEquals(25, level.smallBlind)
        assertEquals(50, level.bigBlind)
        assertEquals(1, level.level)
    }

    @Test
    fun `last level is 3000-6000`() {
        val ts = createState()
        val level = ts.blindStructure.last()
        assertEquals(3000, level.smallBlind)
        assertEquals(6000, level.bigBlind)
        assertEquals(15, level.level)
    }

    @Test
    fun `antes are zero when disabled`() {
        val ts = createState(antesEnabled = false)
        assertTrue(ts.blindStructure.all { it.ante == 0 })
    }

    @Test
    fun `antes are zero for levels 1-3 when enabled`() {
        val ts = createState(antesEnabled = true)
        for (i in 0..2) {
            assertEquals(0, ts.blindStructure[i].ante, "Level ${i + 1} should have no ante")
        }
    }

    @Test
    fun `antes are 10 percent of BB from level 4 when enabled`() {
        val ts = createState(antesEnabled = true)
        for (i in 3 until ts.blindStructure.size) {
            val level = ts.blindStructure[i]
            assertEquals(level.bigBlind / 10, level.ante, "Level ${level.level} ante should be 10% of BB")
        }
    }

    @Test
    fun `advanceHand increments hand count`() {
        val ts = createState()
        assertEquals(0, ts.handsAtCurrentLevel)
        ts.advanceHand()
        assertEquals(1, ts.handsAtCurrentLevel)
    }

    @Test
    fun `advanceHand promotes level at threshold`() {
        val ts = createState(buyin = TournamentBuyin.HUNDRED) // 8 hands per level
        assertEquals(0, ts.currentBlindLevelIndex)

        // Play 7 hands — still at level 0
        repeat(7) { ts.advanceHand() }
        assertEquals(0, ts.currentBlindLevelIndex)

        // 8th hand promotes
        ts.advanceHand()
        assertEquals(1, ts.currentBlindLevelIndex)
        assertEquals(0, ts.handsAtCurrentLevel)
    }

    @Test
    fun `does not advance past last level`() {
        val ts = createState()
        // Jump to last level
        repeat(14) {
            ts.currentBlindLevelIndex = it + 1
        }
        assertEquals(14, ts.currentBlindLevelIndex)

        // Play many hands at last level — should not crash or advance
        repeat(50) { ts.advanceHand() }
        assertEquals(14, ts.currentBlindLevelIndex)
    }

    @Test
    fun `handsUntilNextLevel reports correctly`() {
        val ts = createState(buyin = TournamentBuyin.HUNDRED) // 8 hands per level
        assertEquals(8, ts.handsUntilNextLevel)

        ts.advanceHand()
        assertEquals(7, ts.handsUntilNextLevel)

        repeat(7) { ts.advanceHand() }
        assertEquals(8, ts.handsUntilNextLevel) // Now at level 1 after promotion
    }

    @Test
    fun `handsUntilNextLevel is 0 at last level`() {
        val ts = createState()
        ts.currentBlindLevelIndex = ts.blindStructure.lastIndex
        assertEquals(0, ts.handsUntilNextLevel)
    }

    @Test
    fun `remaining players initialized from config`() {
        val ts = createState(playerCount = 180)
        assertEquals(180, ts.totalPlayers)
        assertEquals(180, ts.remainingPlayers)
    }
}
