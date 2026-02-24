package com.pokerai.ai

import com.pokerai.model.*
import kotlin.test.*

class SessionTrackerTest {

    private fun player(
        index: Int,
        chips: Int = 1000,
        isFolded: Boolean = false,
        isSittingOut: Boolean = false,
        name: String = "Player$index",
        holeCards: HoleCards? = null
    ): Player = Player(
        index = index,
        name = name,
        isHuman = false,
        profile = null,
        chips = chips
    ).apply {
        this.isFolded = isFolded
        this.isSittingOut = isSittingOut
        this.holeCards = holeCards
    }

    private fun card(notation: String) = Card.fromNotation(notation)
    private fun hole(c1: String, c2: String) = HoleCards(card(c1), card(c2))

    // ── Chip tracking ─────────────────────────────────────────

    @Test
    fun `tracks chip gains correctly`() {
        val tracker = SessionTracker(bigBlind = 10)
        val players = listOf(player(0, chips = 1000))
        tracker.recordHandStart(players)

        val stats = tracker.getStats(0, currentChips = 1050)
        assertEquals(5.0, stats.resultBB)
    }

    @Test
    fun `tracks chip losses correctly`() {
        val tracker = SessionTracker(bigBlind = 10)
        val players = listOf(player(0, chips = 1000))
        tracker.recordHandStart(players)

        val stats = tracker.getStats(0, currentChips = 850)
        assertEquals(-15.0, stats.resultBB)
    }

    @Test
    fun `multiple players tracked independently`() {
        val tracker = SessionTracker(bigBlind = 10)
        val players = listOf(
            player(0, chips = 1000),
            player(1, chips = 500)
        )
        tracker.recordHandStart(players)

        val stats0 = tracker.getStats(0, currentChips = 1100)
        val stats1 = tracker.getStats(1, currentChips = 400)

        assertEquals(10.0, stats0.resultBB)
        assertEquals(-10.0, stats1.resultBB)
    }

    @Test
    fun `hands played increments each hand`() {
        val tracker = SessionTracker(bigBlind = 10)
        val players = listOf(player(0, chips = 1000))

        tracker.recordHandStart(players)
        tracker.recordHandStart(players)
        tracker.recordHandStart(players)

        val stats = tracker.getStats(0, currentChips = 1000)
        assertEquals(3, stats.handsPlayed)
    }

    @Test
    fun `starting chips recorded only once per player`() {
        val tracker = SessionTracker(bigBlind = 10)

        // First hand: player starts at 1000
        tracker.recordHandStart(listOf(player(0, chips = 1000)))

        // Second hand: player now has 1200 chips, but starting chips should still be 1000
        tracker.recordHandStart(listOf(player(0, chips = 1200)))

        val stats = tracker.getStats(0, currentChips = 1200)
        assertEquals(20.0, stats.resultBB) // 1200 - 1000 = 200 / 10 = 20
    }

    @Test
    fun `unknown player returns zero result`() {
        val tracker = SessionTracker(bigBlind = 10)
        val stats = tracker.getStats(99, currentChips = 500)
        assertEquals(0.0, stats.resultBB)
        assertEquals(0, stats.handsPlayed)
        assertTrue(stats.recentShowdowns.isEmpty())
    }

    // ── Showdown recording ────────────────────────────────────

    @Test
    fun `records called and won event`() {
        val tracker = SessionTracker(bigBlind = 10)
        val p0 = player(0, chips = 1000, name = "Hero")
        val p1 = player(1, chips = 1000, name = "Villain")
        tracker.recordHandStart(listOf(p0, p1))

        val state = GameState(
            players = listOf(p0, p1),
            actionHistory = mutableListOf(
                ActionRecord(0, "Hero", Action.call(50), GamePhase.FLOP)
            )
        )

        val results = listOf(Triple(0, 200, "Pair of Kings"))
        val hands = mapOf(
            0 to hole("Kd", "Qs"),
            1 to hole("Jh", "Th")
        )

        tracker.recordShowdown(state, results, hands)

        val stats = tracker.getStats(0, 1200)
        assertTrue(stats.recentShowdowns.any { it.event == ShowdownEvent.CALLED_AND_WON })
    }

    @Test
    fun `records called and lost event`() {
        val tracker = SessionTracker(bigBlind = 10)
        val p0 = player(0, chips = 1000, name = "Hero")
        val p1 = player(1, chips = 1000, name = "Villain")
        tracker.recordHandStart(listOf(p0, p1))

        val state = GameState(
            players = listOf(p0, p1),
            actionHistory = mutableListOf(
                ActionRecord(0, "Hero", Action.call(50), GamePhase.RIVER)
            )
        )

        val results = listOf(Triple(1, 200, "Pair of Aces"))
        val hands = mapOf(
            0 to hole("Kd", "Qs"),
            1 to hole("Ah", "As")
        )

        tracker.recordShowdown(state, results, hands)

        val stats = tracker.getStats(0, 800)
        assertTrue(stats.recentShowdowns.any { it.event == ShowdownEvent.CALLED_AND_LOST })
    }

    @Test
    fun `records bluff detection — folded player gets GOT_BLUFFED`() {
        val tracker = SessionTracker(bigBlind = 10)
        val p0 = player(0, chips = 1000, name = "Hero", isFolded = true)
        val p1 = player(1, chips = 1000, name = "Bluffer")
        tracker.recordHandStart(listOf(p0, p1))

        // Bluffer raised on the flop and won with high card
        val state = GameState(
            players = listOf(p0, p1),
            actionHistory = mutableListOf(
                ActionRecord(1, "Bluffer", Action.raise(100), GamePhase.FLOP)
            )
        )

        val results = listOf(Triple(1, 150, "High Card Ace"))
        val hands = mapOf(1 to hole("Ad", "2c"))

        tracker.recordShowdown(state, results, hands)

        val stats = tracker.getStats(0, 950)
        val bluffMemory = stats.recentShowdowns.firstOrNull { it.event == ShowdownEvent.GOT_BLUFFED }
        assertNotNull(bluffMemory)
        assertEquals(1, bluffMemory.opponentIndex)
        assertEquals("Bluffer", bluffMemory.opponentName)
    }

    @Test
    fun `non-folded player sees bluff as SAW_OPPONENT_BLUFF`() {
        val tracker = SessionTracker(bigBlind = 10)
        val p0 = player(0, chips = 1000, name = "Observer")
        val p1 = player(1, chips = 1000, name = "Bluffer")
        tracker.recordHandStart(listOf(p0, p1))

        val state = GameState(
            players = listOf(p0, p1),
            actionHistory = mutableListOf(
                ActionRecord(1, "Bluffer", Action.raise(100), GamePhase.TURN)
            )
        )

        // Observer is not folded, bluffer won with high card
        val results = listOf(Triple(1, 150, "High Card Jack"))
        val hands = mapOf(
            0 to hole("Kd", "Qs"),
            1 to hole("Jh", "3c")
        )

        tracker.recordShowdown(state, results, hands)

        val stats = tracker.getStats(0, 850)
        assertTrue(stats.recentShowdowns.any { it.event == ShowdownEvent.SAW_OPPONENT_BLUFF })
    }

    @Test
    fun `records big pot loss`() {
        val tracker = SessionTracker(bigBlind = 10)
        val p0 = player(0, chips = 1000, name = "Loser")
        val p1 = player(1, chips = 1000, name = "Winner")
        tracker.recordHandStart(listOf(p0, p1))

        val state = GameState(
            players = listOf(p0, p1),
            actionHistory = mutableListOf()
        )

        // Big pot: 400 > 30 * 10 = 300
        val results = listOf(Triple(1, 400, "Flush"))
        val hands = mapOf(
            0 to hole("Kd", "Qs"),
            1 to hole("Ah", "Kh")
        )

        tracker.recordShowdown(state, results, hands)

        val stats = tracker.getStats(0, 600)
        assertTrue(stats.recentShowdowns.any { it.event == ShowdownEvent.SAW_BIG_POT_LOSS })
    }

    // ── Memory aging and pruning ──────────────────────────────

    @Test
    fun `memories age each hand`() {
        val tracker = SessionTracker(bigBlind = 10)
        val p0 = player(0, chips = 1000, name = "Hero")
        val p1 = player(1, chips = 1000, name = "Villain")
        tracker.recordHandStart(listOf(p0, p1))

        // Record a showdown
        val state = GameState(
            players = listOf(p0, p1),
            actionHistory = mutableListOf(
                ActionRecord(0, "Hero", Action.call(50), GamePhase.FLOP)
            )
        )
        tracker.recordShowdown(state, listOf(Triple(0, 200, "Pair")), mapOf(0 to hole("Kd", "Qs"), 1 to hole("Jh", "Th")))

        // Verify handsAgo = 0 initially
        val initialStats = tracker.getStats(0, 1000)
        assertEquals(0, initialStats.recentShowdowns.first().handsAgo)

        // Play 3 more hands
        tracker.recordHandStart(listOf(p0, p1))
        tracker.recordHandStart(listOf(p0, p1))
        tracker.recordHandStart(listOf(p0, p1))

        val laterStats = tracker.getStats(0, 1000)
        assertEquals(3, laterStats.recentShowdowns.first().handsAgo)
    }

    @Test
    fun `old memories are pruned after horizon`() {
        val tracker = SessionTracker(bigBlind = 10, memoryHorizon = 5)
        val p0 = player(0, chips = 1000, name = "Hero")
        val p1 = player(1, chips = 1000, name = "Villain")
        tracker.recordHandStart(listOf(p0, p1))

        // Record a showdown
        val state = GameState(
            players = listOf(p0, p1),
            actionHistory = mutableListOf(
                ActionRecord(0, "Hero", Action.call(50), GamePhase.FLOP)
            )
        )
        tracker.recordShowdown(state, listOf(Triple(0, 200, "Pair")), mapOf(0 to hole("Kd", "Qs"), 1 to hole("Jh", "Th")))

        // Play enough hands to exceed horizon
        repeat(6) { tracker.recordHandStart(listOf(p0, p1)) }

        val stats = tracker.getStats(0, 1000)
        assertTrue(stats.recentShowdowns.isEmpty(), "Memory should be pruned after horizon")
    }

    @Test
    fun `excess memories are trimmed to maxShowdownMemory`() {
        val tracker = SessionTracker(bigBlind = 10, maxShowdownMemory = 3)
        val p0 = player(0, chips = 1000, name = "Hero")
        val p1 = player(1, chips = 1000, name = "Villain")

        // Record 5 showdowns
        repeat(5) {
            tracker.recordHandStart(listOf(p0, p1))
            val state = GameState(
                players = listOf(p0, p1),
                actionHistory = mutableListOf(
                    ActionRecord(0, "Hero", Action.call(50), GamePhase.FLOP)
                )
            )
            tracker.recordShowdown(
                state,
                listOf(Triple(0, 100, "Pair")),
                mapOf(0 to hole("Kd", "Qs"), 1 to hole("Jh", "Th"))
            )
        }

        val stats = tracker.getStats(0, 1000)
        assertTrue(stats.recentShowdowns.size <= 3, "Should have at most 3 memories, got ${stats.recentShowdowns.size}")
    }

    // ── Relevant memories ─────────────────────────────────────

    @Test
    fun `getRelevantMemories returns empty for unknown player`() {
        val tracker = SessionTracker(bigBlind = 10)
        assertTrue(tracker.getRelevantMemories(99).isEmpty())
    }

    @Test
    fun `getRelevantMemories returns recorded memories`() {
        val tracker = SessionTracker(bigBlind = 10)
        val p0 = player(0, chips = 1000, isFolded = true, name = "Hero")
        val p1 = player(1, chips = 1000, name = "Bluffer")
        tracker.recordHandStart(listOf(p0, p1))

        val state = GameState(
            players = listOf(p0, p1),
            actionHistory = mutableListOf(
                ActionRecord(1, "Bluffer", Action.raise(100), GamePhase.RIVER)
            )
        )
        tracker.recordShowdown(state, listOf(Triple(1, 200, "High Card")), mapOf(1 to hole("7d", "2c")))

        val memories = tracker.getRelevantMemories(0)
        assertTrue(memories.isNotEmpty())
    }

    // ── Sitting out players ───────────────────────────────────

    @Test
    fun `sitting out players are not tracked`() {
        val tracker = SessionTracker(bigBlind = 10)
        val active = player(0, chips = 1000)
        val sittingOut = player(1, chips = 500, isSittingOut = true)
        tracker.recordHandStart(listOf(active, sittingOut))

        val activeStats = tracker.getStats(0, 1000)
        assertEquals(1, activeStats.handsPlayed)

        // Sitting out player has no starting chips recorded
        val sittingOutStats = tracker.getStats(1, 500)
        assertEquals(0.0, sittingOutStats.resultBB) // no starting chips → 0 result
    }
}
