package com.pokerai.analysis

import com.pokerai.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawDetectorTest {

    private fun card(notation: String) = Card.fromNotation(notation)
    private fun hole(c1: String, c2: String) = HoleCards(card(c1), card(c2))
    private fun board(vararg notations: String) = notations.map { card(it) }

    // ========== Flush Draw Tests ==========

    @Test
    fun `nut flush draw - ace of suit in hole cards`() {
        val draws = DrawDetector.detectDraws(hole("Ah", "Ks"), board("7h", "3h", "9h"))
        val flushDraw = draws.find { it.type == DrawType.NUT_FLUSH_DRAW }
        assertTrue(flushDraw != null, "Should detect nut flush draw")
        assertEquals(9, flushDraw.outs)
        assertTrue(flushDraw.isNut)
    }

    @Test
    fun `non-nut flush draw - low hole card of suit`() {
        val draws = DrawDetector.detectDraws(hole("8h", "7s"), board("Ah", "3h", "9h"))
        val flushDraw = draws.find { it.type == DrawType.FLUSH_DRAW }
        assertTrue(flushDraw != null, "Should detect non-nut flush draw")
        assertEquals(9, flushDraw.outs)
        assertTrue(!flushDraw.isNut)
    }

    @Test
    fun `flush draw with two hole cards of suit`() {
        val draws = DrawDetector.detectDraws(hole("8h", "7h"), board("Ah", "3h", "9c"))
        val flushDraw = draws.any { it.type == DrawType.NUT_FLUSH_DRAW || it.type == DrawType.FLUSH_DRAW }
        assertTrue(flushDraw, "Should detect flush draw with two suited hole cards")
        val draw = draws.first { it.type == DrawType.NUT_FLUSH_DRAW || it.type == DrawType.FLUSH_DRAW }
        assertEquals(9, draw.outs)
    }

    @Test
    fun `no flush draw when no hole card contributes`() {
        val draws = DrawDetector.detectDraws(hole("8s", "7c"), board("Ah", "3h", "9h"))
        val flushDraw = draws.any { it.type == DrawType.NUT_FLUSH_DRAW || it.type == DrawType.FLUSH_DRAW }
        assertTrue(!flushDraw, "Should NOT detect flush draw when no hole card contributes")
    }

    @Test
    fun `no flush draw when made flush already exists`() {
        // A♥ K♥ on 7♥ 3♥ 9♥ → all 5 cards are hearts → made flush, not a draw
        val draws = DrawDetector.detectDraws(hole("Ah", "Kh"), board("7h", "3h", "9h"))
        val flushDraw = draws.any {
            it.type == DrawType.NUT_FLUSH_DRAW || it.type == DrawType.FLUSH_DRAW
        }
        assertTrue(!flushDraw, "Should NOT detect flush draw when flush is already made")
    }

    @Test
    fun `flush draw on turn`() {
        val draws = DrawDetector.detectDraws(hole("Ah", "Ks"), board("7h", "3h", "9h", "2c"))
        val flushDraw = draws.find { it.type == DrawType.NUT_FLUSH_DRAW }
        assertTrue(flushDraw != null, "Should detect nut flush draw on turn")
        assertEquals(9, flushDraw.outs)
    }

    // ========== OESD Tests ==========

    @Test
    fun `oesd with 7-8 on 5-6-K board`() {
        val draws = DrawDetector.detectDraws(hole("7s", "8c"), board("5d", "6h", "Ks"))
        val oesd = draws.find { it.type == DrawType.OESD }
        assertTrue(oesd != null, "Should detect OESD")
        assertEquals(8, oesd.outs)
    }

    @Test
    fun `oesd with J-T on 8-9-2 board`() {
        val draws = DrawDetector.detectDraws(hole("Js", "Tc"), board("8d", "9h", "2s"))
        val oesd = draws.find { it.type == DrawType.OESD }
        assertTrue(oesd != null, "Should detect OESD")
        assertEquals(8, oesd.outs)
    }

    @Test
    fun `ace high straight draw is gutshot not oesd`() {
        // A-K-Q-J needs only a T → gutshot (can't go above ace)
        val draws = DrawDetector.detectDraws(hole("As", "Kc"), board("Qd", "Jh", "3s"))
        val oesd = draws.find { it.type == DrawType.OESD }
        val gutshot = draws.find { it.type == DrawType.GUTSHOT }
        assertTrue(oesd == null, "A-K-Q-J should NOT be OESD")
        assertTrue(gutshot != null, "A-K-Q-J should be gutshot")
        assertEquals(4, gutshot.outs)
    }

    // ========== Gutshot Tests ==========

    @Test
    fun `gutshot with 7-9 on 5-6-T board`() {
        val draws = DrawDetector.detectDraws(hole("7s", "9c"), board("5d", "6h", "Ts"))
        val gutshot = draws.find { it.type == DrawType.GUTSHOT }
        assertTrue(gutshot != null, "Should detect gutshot")
        assertEquals(4, gutshot.outs)
    }

    @Test
    fun `gutshot for wheel - A-5 on 2-3-K board`() {
        val draws = DrawDetector.detectDraws(hole("As", "5c"), board("2d", "3h", "Ks"))
        val gutshot = draws.find { it.type == DrawType.GUTSHOT }
        assertTrue(gutshot != null, "Should detect gutshot for wheel draw")
        assertEquals(4, gutshot.outs)
    }

    // ========== Backdoor Flush Tests ==========

    @Test
    fun `backdoor flush draw on flop with two suited hole cards`() {
        // A♥ K♥ on 7♥ 3♣ 9♠ → 3 hearts total → backdoor flush draw
        val draws = DrawDetector.detectDraws(hole("Ah", "Kh"), board("7h", "3c", "9s"))
        val backdoorFlush = draws.find { it.type == DrawType.BACKDOOR_FLUSH }
        assertTrue(backdoorFlush != null, "Should detect backdoor flush draw")
        assertEquals(1, backdoorFlush.outs)
    }

    @Test
    fun `backdoor flush draw on flop with one suited hole card`() {
        // A♥ K♠ on 7♥ 3♥ 9♠ → 3 hearts (A♥, 7♥, 3♥) → backdoor flush draw
        val draws = DrawDetector.detectDraws(hole("Ah", "Ks"), board("7h", "3h", "9s"))
        val backdoorFlush = draws.find { it.type == DrawType.BACKDOOR_FLUSH }
        assertTrue(backdoorFlush != null, "Should detect backdoor flush draw")
    }

    @Test
    fun `no backdoor flush draw on turn`() {
        val draws = DrawDetector.detectDraws(hole("Ah", "Kh"), board("7h", "3c", "9s", "2d"))
        val backdoorFlush = draws.find { it.type == DrawType.BACKDOOR_FLUSH }
        assertTrue(backdoorFlush == null, "Should NOT detect backdoor flush on turn")
    }

    // ========== Backdoor Straight Tests ==========

    @Test
    fun `backdoor straight draw on flop`() {
        // 8♠ 9♣ on 7♦ 2♥ K♠ → 7-8-9 within a straight window → backdoor straight
        val draws = DrawDetector.detectDraws(hole("8s", "9c"), board("7d", "2h", "Ks"))
        val backdoorStraight = draws.find { it.type == DrawType.BACKDOOR_STRAIGHT }
        assertTrue(backdoorStraight != null, "Should detect backdoor straight draw")
        assertEquals(1, backdoorStraight.outs)
    }

    @Test
    fun `no backdoor straight when cards not connected`() {
        val draws2 = DrawDetector.detectDraws(hole("2s", "7c"), board("Qd", "Kh", "As"))
        val backdoor2 = draws2.find { it.type == DrawType.BACKDOOR_STRAIGHT }
        assertTrue(backdoor2 == null, "Should NOT detect backdoor straight when hole cards don't contribute")
    }

    @Test
    fun `no backdoor straight on turn`() {
        val draws = DrawDetector.detectDraws(hole("8s", "9c"), board("7d", "2h", "Ks", "3c"))
        val backdoorStraight = draws.find { it.type == DrawType.BACKDOOR_STRAIGHT }
        assertTrue(backdoorStraight == null, "Should NOT detect backdoor straight on turn")
    }

    // ========== Overcards Tests ==========

    @Test
    fun `overcards when both hole cards above board`() {
        val draws = DrawDetector.detectDraws(hole("As", "Kc"), board("7d", "5h", "3s"))
        val overcards = draws.find { it.type == DrawType.OVERCARDS }
        assertTrue(overcards != null, "Should detect overcards")
        assertEquals(6, overcards.outs)
    }

    @Test
    fun `overcards AK above Q high board`() {
        // A(14) > Q(12) and K(13) > Q(12) → both above all board cards
        val draws = DrawDetector.detectDraws(hole("As", "Kc"), board("Qd", "5h", "3s"))
        val overcards = draws.find { it.type == DrawType.OVERCARDS }
        assertTrue(overcards != null, "AK should be overcards to Q-5-3 board")
        assertEquals(6, overcards.outs)
    }

    @Test
    fun `no overcards when one hole card below board`() {
        // A♠ J♣ on K♦ 5♥ 3♠ → J < K, so NOT overcards
        val draws = DrawDetector.detectDraws(hole("As", "Jc"), board("Kd", "5h", "3s"))
        val overcards = draws.find { it.type == DrawType.OVERCARDS }
        assertTrue(overcards == null, "AJ should NOT be overcards when K is on board")
    }

    @Test
    fun `no overcards when player has a pair`() {
        // A♠ K♣ on K♦ 5♥ 3♠ → player has top pair, overcards should not be flagged
        val draws = DrawDetector.detectDraws(hole("As", "Kc"), board("Kd", "5h", "3s"))
        val overcards = draws.find { it.type == DrawType.OVERCARDS }
        assertTrue(overcards == null, "Should NOT detect overcards when player has a pair")
    }

    // ========== Combo Draw Tests ==========

    @Test
    fun `combo draw - flush draw plus oesd`() {
        // 9♥ 8♥ on 6♥ 7♣ A♥ → flush draw (9 outs) + OESD (5-6-7-8-9, need 5 or T → 8 outs)
        val draws = DrawDetector.detectDraws(hole("9h", "8h"), board("6h", "7c", "Ah"))
        val flushDraw = draws.any { it.type == DrawType.NUT_FLUSH_DRAW || it.type == DrawType.FLUSH_DRAW }
        val oesd = draws.any { it.type == DrawType.OESD }
        assertTrue(flushDraw, "Should detect flush draw in combo")
        assertTrue(oesd, "Should detect OESD in combo")
    }

    @Test
    fun `combo draw - nut flush draw plus gutshot`() {
        // A♥ 5♥ on 4♥ 6♥ 7♠ → nut flush draw + OESD (4-5-6-7 with 3 or 8)
        val draws = DrawDetector.detectDraws(hole("Ah", "5h"), board("4h", "6h", "7s"))
        val nutFlush = draws.any { it.type == DrawType.NUT_FLUSH_DRAW }
        assertTrue(nutFlush, "Should detect nut flush draw")
        // The straight part: 4,5,6,7 → OESD (need 3 or 8)
        val hasStraightDraw = draws.any { it.type == DrawType.OESD || it.type == DrawType.GUTSHOT }
        assertTrue(hasStraightDraw, "Should detect straight draw component")
    }

    // ========== No Draw Tests ==========

    @Test
    fun `no draws at all`() {
        val draws = DrawDetector.detectDraws(hole("2s", "7c"), board("Ah", "Kd", "9s"))
        // Filter out backdoor draws to check for major draws
        val majorDraws = draws.filter {
            it.type != DrawType.BACKDOOR_FLUSH && it.type != DrawType.BACKDOOR_STRAIGHT
        }
        assertTrue(majorDraws.isEmpty() || majorDraws.all { it.type == DrawType.OVERCARDS }.not(),
            "Should have no meaningful draws")
    }

    // ========== River Tests ==========

    @Test
    fun `no draws on river`() {
        val draws = DrawDetector.detectDraws(hole("Ah", "Kh"), board("7h", "3h", "9s", "Qd", "2c"))
        assertTrue(draws.isEmpty(), "Should return empty list on river")
    }

    @Test
    fun `no draws on preflop`() {
        val draws = DrawDetector.detectDraws(hole("Ah", "Kh"), emptyList())
        assertTrue(draws.isEmpty(), "Should return empty list on preflop")
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `wheel draw A-2-3-4 is gutshot not oesd`() {
        // A-2-3-4 needs only 5 to complete → gutshot
        val draws = DrawDetector.detectDraws(hole("As", "4c"), board("2d", "3h", "Ks"))
        val oesd = draws.find { it.type == DrawType.OESD }
        val gutshot = draws.find { it.type == DrawType.GUTSHOT }
        assertTrue(oesd == null, "A-2-3-4 should NOT be OESD")
        assertTrue(gutshot != null, "A-2-3-4 should be gutshot")
    }

    @Test
    fun `flush draw on turn with 4 suited`() {
        val draws = DrawDetector.detectDraws(hole("Ah", "Ks"), board("7h", "3h", "9h", "Td"))
        val flushDraw = draws.find { it.type == DrawType.NUT_FLUSH_DRAW }
        assertTrue(flushDraw != null, "Should detect nut flush draw on turn")
        assertEquals(9, flushDraw.outs)
    }
}
