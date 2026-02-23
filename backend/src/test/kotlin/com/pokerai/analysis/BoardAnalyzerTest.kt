package com.pokerai.analysis

import com.pokerai.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BoardAnalyzerTest {

    private fun card(notation: String) = Card.fromNotation(notation)
    private fun board(vararg notations: String) = notations.map { card(it) }

    // ========== Flop Wetness Tests ==========

    @Test
    fun `dry flop - rainbow unconnected`() {
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c"))
        assertEquals(BoardWetness.DRY, result.wetness)
        assertTrue(result.rainbow)
        assertFalse(result.connected)
        assertFalse(result.paired)
    }

    @Test
    fun `dry flop - ace high rainbow unconnected`() {
        val result = BoardAnalyzer.analyze(board("Ad", "8c", "3s"))
        assertEquals(BoardWetness.DRY, result.wetness)
        assertTrue(result.rainbow)
    }

    @Test
    fun `dry flop - queen high rainbow unconnected`() {
        val result = BoardAnalyzer.analyze(board("Qs", "6d", "2c"))
        assertEquals(BoardWetness.DRY, result.wetness)
    }

    @Test
    fun `semi-wet flop - two-tone unconnected`() {
        val result = BoardAnalyzer.analyze(board("Qh", "7s", "2h"))
        assertEquals(BoardWetness.SEMI_WET, result.wetness)
        assertTrue(result.twoTone)
        assertFalse(result.connected)
    }

    @Test
    fun `semi-wet flop - connected rainbow`() {
        val result = BoardAnalyzer.analyze(board("9s", "8d", "3c"))
        assertEquals(BoardWetness.SEMI_WET, result.wetness)
        assertTrue(result.connected)
        assertTrue(result.rainbow)
    }

    @Test
    fun `semi-wet flop - paired rainbow`() {
        val result = BoardAnalyzer.analyze(board("Ks", "Kd", "7c"))
        assertEquals(BoardWetness.SEMI_WET, result.wetness)
        assertTrue(result.paired)
        assertTrue(result.rainbow)
    }

    @Test
    fun `wet flop - two-tone connected`() {
        val result = BoardAnalyzer.analyze(board("9h", "8h", "3c"))
        assertEquals(BoardWetness.WET, result.wetness)
        assertTrue(result.twoTone)
        assertTrue(result.connected)
    }

    @Test
    fun `wet flop - two-tone connected clubs`() {
        val result = BoardAnalyzer.analyze(board("Tc", "9c", "4d"))
        assertEquals(BoardWetness.WET, result.wetness)
        assertTrue(result.twoTone)
        assertTrue(result.connected)
    }

    @Test
    fun `wet flop - highly connected rainbow`() {
        // J-T-8: all within a 5-rank span (8-12), rainbow
        val result = BoardAnalyzer.analyze(board("Js", "Td", "8c"))
        assertEquals(BoardWetness.WET, result.wetness)
        assertTrue(result.highlyConnected)
        assertTrue(result.rainbow)
    }

    @Test
    fun `very wet flop - monotone`() {
        val result = BoardAnalyzer.analyze(board("Ts", "7s", "3s"))
        assertEquals(BoardWetness.VERY_WET, result.wetness)
        assertTrue(result.monotone)
    }

    @Test
    fun `very wet flop - two-tone highly connected`() {
        // 8♥ 9♥ T♣ → twoTone, highlyConnected, flushDrawPossible → VERY_WET
        val result = BoardAnalyzer.analyze(board("8h", "9h", "Tc"))
        assertEquals(BoardWetness.VERY_WET, result.wetness)
    }

    @Test
    fun `very wet flop - monotone and highly connected`() {
        val result = BoardAnalyzer.analyze(board("5d", "6d", "7d"))
        assertEquals(BoardWetness.VERY_WET, result.wetness)
        assertTrue(result.monotone)
        assertTrue(result.highlyConnected)
    }

    @Test
    fun `very wet flop - two-tone highly connected JT9`() {
        val result = BoardAnalyzer.analyze(board("Jh", "Th", "9c"))
        assertEquals(BoardWetness.VERY_WET, result.wetness)
    }

    @Test
    fun `very wet flop - monotone low cards`() {
        val result = BoardAnalyzer.analyze(board("7h", "5h", "3h"))
        assertEquals(BoardWetness.VERY_WET, result.wetness)
        assertTrue(result.monotone)
    }

    // ========== Suit Analysis Tests ==========

    @Test
    fun `suit analysis - rainbow flop`() {
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c"))
        assertTrue(result.rainbow)
        assertFalse(result.twoTone)
        assertFalse(result.monotone)
        assertFalse(result.flushPossible)
        assertFalse(result.flushDrawPossible)
        assertNull(result.dominantSuit)
    }

    @Test
    fun `suit analysis - two-tone flop`() {
        val result = BoardAnalyzer.analyze(board("Qh", "7s", "2h"))
        assertFalse(result.rainbow)
        assertTrue(result.twoTone)
        assertFalse(result.monotone)
        assertFalse(result.flushPossible)
        assertTrue(result.flushDrawPossible)
        assertEquals(Suit.HEARTS, result.dominantSuit)
    }

    @Test
    fun `suit analysis - monotone flop`() {
        val result = BoardAnalyzer.analyze(board("Ts", "7s", "3s"))
        assertFalse(result.rainbow)
        assertFalse(result.twoTone)
        assertTrue(result.monotone)
        assertTrue(result.flushPossible)
        assertTrue(result.flushDrawPossible)
        assertEquals(Suit.SPADES, result.dominantSuit)
    }

    @Test
    fun `suit analysis - turn with 3 of suit is not monotone but flush possible`() {
        val result = BoardAnalyzer.analyze(board("Qh", "7s", "2h", "9h"))
        assertFalse(result.monotone) // need 4 of suit for turn monotone
        assertTrue(result.flushPossible) // 3 hearts
        assertEquals(Suit.HEARTS, result.dominantSuit)
    }

    @Test
    fun `suit analysis - turn monotone with 4 of suit`() {
        val result = BoardAnalyzer.analyze(board("Qh", "7h", "2h", "9h"))
        assertTrue(result.monotone)
        assertTrue(result.flushPossible)
    }

    @Test
    fun `suit analysis - rainbow turn`() {
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c", "9h"))
        assertTrue(result.rainbow)
        assertFalse(result.flushDrawPossible)
        assertNull(result.dominantSuit)
    }

    // ========== Rank Analysis Tests ==========

    @Test
    fun `rank analysis - no pair unconnected`() {
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c"))
        assertFalse(result.paired)
        assertFalse(result.connected) // K=13, 7, 2: no two within 2
        assertEquals(Rank.KING, result.highCard)
        assertEquals(Rank.TWO, result.lowCard)
    }

    @Test
    fun `rank analysis - connected 9-8`() {
        val result = BoardAnalyzer.analyze(board("9s", "8d", "3c"))
        assertTrue(result.connected) // 9 and 8 are 1 apart
        assertFalse(result.highlyConnected) // 3 is far from 8-9
    }

    @Test
    fun `rank analysis - highly connected J-T-8`() {
        val result = BoardAnalyzer.analyze(board("Js", "Td", "8c"))
        assertTrue(result.connected)
        assertTrue(result.highlyConnected) // J=11, T=10, 8 all in window 8-12
    }

    @Test
    fun `rank analysis - paired board`() {
        val result = BoardAnalyzer.analyze(board("Ks", "Kd", "7c"))
        assertTrue(result.paired)
        assertFalse(result.connected) // K=13, 7: not within 2
    }

    @Test
    fun `rank analysis - double paired on river`() {
        val result = BoardAnalyzer.analyze(board("Ks", "Kd", "Qc", "Qs", "7c"))
        assertTrue(result.doublePaired)
        assertTrue(result.paired)
    }

    @Test
    fun `rank analysis - trips on board`() {
        val result = BoardAnalyzer.analyze(board("7s", "7d", "7c"))
        assertTrue(result.trips)
        assertTrue(result.paired)
    }

    @Test
    fun `rank analysis - 5-6-7 connected and highly connected`() {
        val result = BoardAnalyzer.analyze(board("5c", "6d", "7s"))
        assertTrue(result.connected)
        assertTrue(result.highlyConnected)
    }

    @Test
    fun `rank analysis - ace is high card`() {
        val result = BoardAnalyzer.analyze(board("As", "5d", "4c"))
        assertEquals(Rank.ACE, result.highCard)
        assertEquals(Rank.FOUR, result.lowCard)
    }

    // ========== Straight Texture Tests ==========

    @Test
    fun `straight possible with 5-6-7`() {
        val result = BoardAnalyzer.analyze(board("5c", "6d", "7s"))
        assertTrue(result.straightPossible)
        assertTrue(result.straightDrawHeavy) // 5-6 and 6-7 consecutive
    }

    @Test
    fun `straight possible with J-T-8`() {
        val result = BoardAnalyzer.analyze(board("Js", "Td", "8c"))
        assertTrue(result.straightPossible)
        assertTrue(result.straightDrawHeavy) // T-J consecutive
    }

    @Test
    fun `straight not possible K-7-2`() {
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c"))
        assertFalse(result.straightPossible)
        assertFalse(result.straightDrawHeavy)
    }

    @Test
    fun `straight possible with A-K-Q broadway`() {
        val result = BoardAnalyzer.analyze(board("As", "Kd", "Qc"))
        assertTrue(result.straightPossible) // window 10-14 has A,K,Q
        assertTrue(result.straightDrawHeavy) // K-Q and Q...A consecutive
    }

    @Test
    fun `straight possible with ace low - A-5-4`() {
        val result = BoardAnalyzer.analyze(board("As", "5d", "4c"))
        assertTrue(result.straightPossible) // window 1-5 with ace-low: {1,4,5} = 3
    }

    @Test
    fun `straight not possible 9-8-3`() {
        // 9,8,3: no 5-rank window has 3+ cards
        val result = BoardAnalyzer.analyze(board("9s", "8d", "3c"))
        assertFalse(result.straightPossible)
    }

    // ========== Turn/River Change Detection Tests ==========

    @Test
    fun `flop has no change detection`() {
        val result = BoardAnalyzer.analyze(board("Qh", "7s", "2h"), previousCommunityCount = 0)
        assertFalse(result.flushCompletedThisStreet)
        assertFalse(result.straightCompletedThisStreet)
        assertFalse(result.boardPairedThisStreet)
    }

    @Test
    fun `flush completed on turn - third heart`() {
        // Previous: Q♥ 7♠ 2♥ (2 hearts), new: 9♥ → 3 hearts
        val result = BoardAnalyzer.analyze(board("Qh", "7s", "2h", "9h"), previousCommunityCount = 3)
        assertTrue(result.flushCompletedThisStreet)
    }

    @Test
    fun `flush not completed on turn - only second of suit`() {
        // Previous: Q♥ 7♠ 2♣ (1 heart), new: 9♥ → 2 hearts (not yet flush possible)
        val result = BoardAnalyzer.analyze(board("Qh", "7s", "2c", "9h"), previousCommunityCount = 3)
        assertFalse(result.flushCompletedThisStreet)
    }

    @Test
    fun `flush completed on river - fourth heart`() {
        // Previous: Q♥ 7♥ 2♥ 5♣ (3 hearts), new: 9♥ → 4 hearts
        val result = BoardAnalyzer.analyze(board("Qh", "7h", "2h", "5c", "9h"), previousCommunityCount = 4)
        assertTrue(result.flushCompletedThisStreet)
    }

    @Test
    fun `flush not completed - different suit`() {
        // Previous: Q♥ 7♠ 2♣ (1 heart), new: 9♠ → no suit went 2→3
        val result = BoardAnalyzer.analyze(board("Qh", "7s", "2c", "9s"), previousCommunityCount = 3)
        assertFalse(result.flushCompletedThisStreet)
    }

    @Test
    fun `board paired on turn`() {
        // Previous: K♠ 7♦ 2♣, new: 7♥ → paired
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c", "7h"), previousCommunityCount = 3)
        assertTrue(result.boardPairedThisStreet)
        assertTrue(result.paired)
    }

    @Test
    fun `board not paired on turn`() {
        // Previous: K♠ 7♦ 2♣, new: 9♥ → no pair
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c", "9h"), previousCommunityCount = 3)
        assertFalse(result.boardPairedThisStreet)
    }

    @Test
    fun `board paired making trips`() {
        // Previous: K♠ K♦ 7♣, new: K♥ → trips
        val result = BoardAnalyzer.analyze(board("Ks", "Kd", "7c", "Kh"), previousCommunityCount = 3)
        assertTrue(result.boardPairedThisStreet)
        assertTrue(result.trips)
    }

    @Test
    fun `board paired second pair on turn`() {
        // Previous: K♠ K♦ 7♣, new: 7♥ → double paired
        val result = BoardAnalyzer.analyze(board("Ks", "Kd", "7c", "7h"), previousCommunityCount = 3)
        assertTrue(result.boardPairedThisStreet)
        assertTrue(result.doublePaired)
    }

    @Test
    fun `straight completed on turn`() {
        // Previous: 5♣ 6♦ 9♠, new: 7♥ → now 5-6-7 in a 5-rank span
        val result = BoardAnalyzer.analyze(board("5c", "6d", "9s", "7h"), previousCommunityCount = 3)
        assertTrue(result.straightCompletedThisStreet)
    }

    @Test
    fun `straight not completed on turn - card doesnt help`() {
        // Previous: K♠ 7♦ 2♣, new: 3♥ → 2,3,7,K no 3 in a 5-rank span
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c", "3h"), previousCommunityCount = 3)
        assertFalse(result.straightCompletedThisStreet)
    }

    @Test
    fun `straight completed on turn - queen connects TJ`() {
        // Previous: T♠ J♦ 3♣, new: Q♥ → T-J-Q in span 10-14
        val result = BoardAnalyzer.analyze(board("Ts", "Jd", "3c", "Qh"), previousCommunityCount = 3)
        assertTrue(result.straightCompletedThisStreet)
    }

    // ========== Full Board Evolution Test ==========

    @Test
    fun `board evolution - flop`() {
        val flop = BoardAnalyzer.analyze(board("9h", "8h", "3c"), previousCommunityCount = 0)
        assertEquals(BoardWetness.WET, flop.wetness)
        assertTrue(flop.twoTone)
        assertTrue(flop.connected)
        assertFalse(flop.flushCompletedThisStreet)
        assertFalse(flop.boardPairedThisStreet)
    }

    @Test
    fun `board evolution - turn brings flush`() {
        val turn = BoardAnalyzer.analyze(board("9h", "8h", "3c", "2h"), previousCommunityCount = 3)
        assertTrue(turn.flushPossible) // 3 hearts
        assertTrue(turn.flushCompletedThisStreet)
        assertFalse(turn.boardPairedThisStreet)
        assertFalse(turn.straightCompletedThisStreet)
    }

    @Test
    fun `board evolution - river pairs board`() {
        val river = BoardAnalyzer.analyze(board("9h", "8h", "3c", "2h", "8c"), previousCommunityCount = 4)
        assertTrue(river.boardPairedThisStreet)
        assertTrue(river.paired)
        assertFalse(river.flushCompletedThisStreet) // 8♣ doesn't add hearts
    }

    // ========== Description Tests ==========

    @Test
    fun `description for dry board`() {
        val result = BoardAnalyzer.analyze(board("Ks", "7d", "2c"))
        assertTrue(result.description.startsWith("Dry"), result.description)
        assertTrue(result.description.contains("rainbow"), result.description)
        assertTrue(result.description.contains("unconnected"), result.description)
    }

    @Test
    fun `description for semi-wet two-tone`() {
        val result = BoardAnalyzer.analyze(board("Qh", "7s", "2h"))
        assertTrue(result.description.startsWith("Semi-wet"), result.description)
        assertTrue(result.description.contains("two-tone"), result.description)
    }

    @Test
    fun `description for wet board`() {
        val result = BoardAnalyzer.analyze(board("9h", "8h", "3c"))
        assertTrue(result.description.startsWith("Wet"), result.description)
    }

    @Test
    fun `description for very wet monotone`() {
        val result = BoardAnalyzer.analyze(board("Ts", "7s", "3s"))
        assertTrue(result.description.startsWith("Very wet"), result.description)
        assertTrue(result.description.contains("monotone"), result.description)
    }

    @Test
    fun `description for very wet flush and straight`() {
        val result = BoardAnalyzer.analyze(board("8h", "9h", "Tc"))
        assertTrue(result.description.startsWith("Very wet"), result.description)
    }

    // ========== Edge Cases ==========

    @Test
    fun `ace-2 not connected by rank value`() {
        // Ace=14, Two=2 → diff=12 → not connected (ace-low only for straight analysis)
        val result = BoardAnalyzer.analyze(board("As", "2d", "9c"))
        assertFalse(result.connected)
    }

    @Test
    fun `ace-king are connected`() {
        val result = BoardAnalyzer.analyze(board("As", "Kd", "3c"))
        assertTrue(result.connected) // 14 and 13 are within 2
    }

    @Test
    fun `river with 4 of a suit is monotone`() {
        val result = BoardAnalyzer.analyze(board("Ah", "Kh", "7h", "3h", "2c"))
        assertTrue(result.monotone) // 4 hearts out of 5 cards: monotone on river
    }

    @Test
    fun `river with 3 of a suit is not monotone`() {
        val result = BoardAnalyzer.analyze(board("Ah", "Kh", "7h", "3c", "2d"))
        assertFalse(result.monotone)
        assertTrue(result.flushPossible) // 3 hearts
    }

    @Test
    fun `highly connected with ace low wheel cards`() {
        // A-2-3: with ace-low, {1,2,3} are all in window 1-5
        val result = BoardAnalyzer.analyze(board("As", "2d", "3c"))
        assertTrue(result.highlyConnected)
        assertTrue(result.straightPossible)
    }
}
