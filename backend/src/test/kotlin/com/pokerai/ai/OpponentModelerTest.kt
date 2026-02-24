package com.pokerai.ai

import com.pokerai.model.*
import kotlin.test.*

class OpponentModelerTest {

    private fun player(
        index: Int,
        chips: Int = 1000,
        name: String = "Player$index",
        isFolded: Boolean = false,
        isSittingOut: Boolean = false
    ): Player = Player(
        index = index,
        name = name,
        isHuman = false,
        profile = null,
        chips = chips
    ).apply {
        this.isFolded = isFolded
        this.isSittingOut = isSittingOut
    }

    // ── Classification with insufficient data ─────────────────

    @Test
    fun `unknown with insufficient data`() {
        val modeler = OpponentModeler(minHandsForClassification = 15)
        val p = player(0)

        // Record only 5 hands
        repeat(5) {
            modeler.recordNewHand(listOf(p))
            modeler.recordAction(0, Action.call(10), GamePhase.PRE_FLOP)
        }

        val read = modeler.getRead(p)
        assertEquals(OpponentType.UNKNOWN, read.playerType)
        assertEquals(5, read.handsObserved)
    }

    // ── LOOSE_AGGRESSIVE ──────────────────────────────────────

    @Test
    fun `classifies LOOSE_AGGRESSIVE correctly`() {
        val modeler = OpponentModeler(minHandsForClassification = 15)
        val p = player(0)

        // 20 hands: voluntarily plays 12 (VPIP = 60%), raises preflop 6 (PFR = 30%)
        repeat(20) { hand ->
            modeler.recordNewHand(listOf(p))
            if (hand < 6) {
                modeler.recordAction(0, Action.raise(30), GamePhase.PRE_FLOP)
            } else if (hand < 12) {
                modeler.recordAction(0, Action.call(10), GamePhase.PRE_FLOP)
            } else {
                modeler.recordAction(0, Action.fold(), GamePhase.PRE_FLOP)
            }
        }

        // Postflop: 10 bets, 5 calls, 3 checks (aggression = 10/18 = 55%)
        repeat(10) { modeler.recordAction(0, Action.raise(50), GamePhase.FLOP) }
        repeat(5) { modeler.recordAction(0, Action.call(50), GamePhase.FLOP) }
        repeat(3) { modeler.recordAction(0, Action.check(), GamePhase.FLOP) }

        val read = modeler.getRead(p)
        assertEquals(OpponentType.LOOSE_AGGRESSIVE, read.playerType)
        assertEquals(0.6, read.vpip, 0.01)
        assertEquals(0.3, read.pfr, 0.01)
    }

    // ── LOOSE_PASSIVE (calling station) ───────────────────────

    @Test
    fun `classifies LOOSE_PASSIVE correctly`() {
        val modeler = OpponentModeler(minHandsForClassification = 15)
        val p = player(0)

        // 20 hands: voluntarily plays 14 (VPIP = 70%), raises preflop 2 (PFR = 10%)
        repeat(20) { hand ->
            modeler.recordNewHand(listOf(p))
            if (hand < 2) {
                modeler.recordAction(0, Action.raise(30), GamePhase.PRE_FLOP)
            } else if (hand < 14) {
                modeler.recordAction(0, Action.call(10), GamePhase.PRE_FLOP)
            } else {
                modeler.recordAction(0, Action.fold(), GamePhase.PRE_FLOP)
            }
        }

        // Postflop: 2 bets, 15 calls, 5 checks (aggression = 2/22 = 9%)
        repeat(2) { modeler.recordAction(0, Action.raise(50), GamePhase.FLOP) }
        repeat(15) { modeler.recordAction(0, Action.call(50), GamePhase.TURN) }
        repeat(5) { modeler.recordAction(0, Action.check(), GamePhase.RIVER) }

        val read = modeler.getRead(p)
        assertEquals(OpponentType.LOOSE_PASSIVE, read.playerType)
        assertTrue(read.vpip > 0.30, "VPIP should be > 30%, got ${read.vpip}")
        assertTrue(read.aggressionFrequency < 0.40, "Aggression should be < 40%, got ${read.aggressionFrequency}")
    }

    // ── TIGHT_AGGRESSIVE ──────────────────────────────────────

    @Test
    fun `classifies TIGHT_AGGRESSIVE correctly`() {
        val modeler = OpponentModeler(minHandsForClassification = 15)
        val p = player(0)

        // 20 hands: voluntarily plays 4 (VPIP = 20%), raises preflop 3 (PFR = 15%)
        repeat(20) { hand ->
            modeler.recordNewHand(listOf(p))
            if (hand < 3) {
                modeler.recordAction(0, Action.raise(30), GamePhase.PRE_FLOP)
            } else if (hand < 4) {
                modeler.recordAction(0, Action.call(10), GamePhase.PRE_FLOP)
            } else {
                modeler.recordAction(0, Action.fold(), GamePhase.PRE_FLOP)
            }
        }

        // Postflop: 5 bets, 2 calls, 1 check (aggression = 5/8 = 62%)
        repeat(5) { modeler.recordAction(0, Action.raise(50), GamePhase.FLOP) }
        repeat(2) { modeler.recordAction(0, Action.call(50), GamePhase.TURN) }
        repeat(1) { modeler.recordAction(0, Action.check(), GamePhase.RIVER) }

        val read = modeler.getRead(p)
        assertEquals(OpponentType.TIGHT_AGGRESSIVE, read.playerType)
        assertTrue(read.vpip <= 0.30, "VPIP should be <= 30%, got ${read.vpip}")
    }

    // ── TIGHT_PASSIVE (nit) ───────────────────────────────────

    @Test
    fun `classifies TIGHT_PASSIVE correctly`() {
        val modeler = OpponentModeler(minHandsForClassification = 15)
        val p = player(0)

        // 20 hands: voluntarily plays 3 (VPIP = 15%), raises preflop 1 (PFR = 5%)
        repeat(20) { hand ->
            modeler.recordNewHand(listOf(p))
            if (hand < 1) {
                modeler.recordAction(0, Action.raise(30), GamePhase.PRE_FLOP)
            } else if (hand < 3) {
                modeler.recordAction(0, Action.call(10), GamePhase.PRE_FLOP)
            } else {
                modeler.recordAction(0, Action.fold(), GamePhase.PRE_FLOP)
            }
        }

        // Postflop: 1 bet, 3 calls, 4 checks (aggression = 1/8 = 12%)
        repeat(1) { modeler.recordAction(0, Action.raise(50), GamePhase.FLOP) }
        repeat(3) { modeler.recordAction(0, Action.call(50), GamePhase.TURN) }
        repeat(4) { modeler.recordAction(0, Action.check(), GamePhase.RIVER) }

        val read = modeler.getRead(p)
        assertEquals(OpponentType.TIGHT_PASSIVE, read.playerType)
        assertTrue(read.vpip <= 0.30, "VPIP should be <= 30%, got ${read.vpip}")
        assertTrue(read.aggressionFrequency <= 0.40, "Aggression should be <= 40%, got ${read.aggressionFrequency}")
    }

    // ── VPIP counting ─────────────────────────────────────────

    @Test
    fun `VPIP counts calls and raises not folds`() {
        val modeler = OpponentModeler(minHandsForClassification = 15)
        val p = player(0)

        repeat(20) { hand ->
            modeler.recordNewHand(listOf(p))
            when {
                hand < 3 -> modeler.recordAction(0, Action.call(10), GamePhase.PRE_FLOP)
                hand < 5 -> modeler.recordAction(0, Action.raise(30), GamePhase.PRE_FLOP)
                else -> modeler.recordAction(0, Action.fold(), GamePhase.PRE_FLOP)
            }
        }

        val read = modeler.getRead(p)
        assertEquals(0.25, read.vpip, 0.01) // 5/20 = 25%
        assertEquals(0.10, read.pfr, 0.01)  // 2/20 = 10%
    }

    @Test
    fun `BB check option does not count as voluntary`() {
        val modeler = OpponentModeler(minHandsForClassification = 15)
        val p = player(0)

        repeat(20) { hand ->
            modeler.recordNewHand(listOf(p))
            if (hand < 3) {
                // BB checks their option — CHECK in PRE_FLOP
                modeler.recordAction(0, Action.check(), GamePhase.PRE_FLOP)
            } else {
                modeler.recordAction(0, Action.fold(), GamePhase.PRE_FLOP)
            }
        }

        val read = modeler.getRead(p)
        assertEquals(0.0, read.vpip, 0.01) // Checks don't count as VPIP
    }

    // ── Notable actions ───────────────────────────────────────

    @Test
    fun `records notable actions`() {
        val modeler = OpponentModeler()
        val p = player(0)
        modeler.recordNewHand(listOf(p))

        modeler.recordNotableAction(0, "showed a bluff")

        val read = modeler.getRead(p)
        assertEquals("showed a bluff", read.recentNotableAction)
    }

    // ── getOpponentReads filtering ────────────────────────────

    @Test
    fun `getOpponentReads excludes the decision-making player`() {
        val modeler = OpponentModeler()
        val players = listOf(player(0), player(1), player(2))
        modeler.recordNewHand(players)

        val reads = modeler.getOpponentReads(players, excludePlayerIndex = 0)
        assertEquals(2, reads.size)
        assertTrue(reads.none { it.playerIndex == 0 })
    }

    @Test
    fun `getOpponentReads excludes folded and sitting out players`() {
        val modeler = OpponentModeler()
        val players = listOf(
            player(0),
            player(1, isFolded = true),
            player(2, isSittingOut = true),
            player(3)
        )
        modeler.recordNewHand(players)

        val reads = modeler.getOpponentReads(players, excludePlayerIndex = 0)
        assertEquals(1, reads.size)
        assertEquals(3, reads.first().playerIndex)
    }

    // ── Edge cases ────────────────────────────────────────────

    @Test
    fun `getRead for untracked player returns defaults`() {
        val modeler = OpponentModeler()
        val p = player(0)

        val read = modeler.getRead(p)
        assertEquals(OpponentType.UNKNOWN, read.playerType)
        assertEquals(0, read.handsObserved)
        assertEquals(0.0, read.vpip)
        assertEquals(0.0, read.pfr)
        assertEquals(0.0, read.aggressionFrequency)
        assertNull(read.recentNotableAction)
    }

    @Test
    fun `all-in preflop counts as voluntary play and raise`() {
        val modeler = OpponentModeler(minHandsForClassification = 15)
        val p = player(0)

        repeat(20) { hand ->
            modeler.recordNewHand(listOf(p))
            if (hand < 5) {
                modeler.recordAction(0, Action.allIn(1000), GamePhase.PRE_FLOP)
            } else {
                modeler.recordAction(0, Action.fold(), GamePhase.PRE_FLOP)
            }
        }

        val read = modeler.getRead(p)
        assertEquals(0.25, read.vpip, 0.01) // 5/20
        assertEquals(0.25, read.pfr, 0.01)  // 5/20
    }

    @Test
    fun `postflop all-in counts as aggression`() {
        val modeler = OpponentModeler()
        val p = player(0)
        modeler.recordNewHand(listOf(p))

        modeler.recordAction(0, Action.allIn(500), GamePhase.FLOP)
        modeler.recordAction(0, Action.call(50), GamePhase.TURN)

        val read = modeler.getRead(p)
        assertEquals(0.5, read.aggressionFrequency, 0.01) // 1 bet / 2 total
    }
}
