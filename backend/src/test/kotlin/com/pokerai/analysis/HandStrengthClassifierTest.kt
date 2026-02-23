package com.pokerai.analysis

import com.pokerai.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HandStrengthClassifierTest {

    private fun card(notation: String) = Card.fromNotation(notation)
    private fun hole(c1: String, c2: String) = HoleCards(card(c1), card(c2))
    private fun board(vararg notations: String) = notations.map { card(it) }

    // ========== MONSTER Tier Tests ==========

    @Test
    fun `set is monster`() {
        val result = HandStrengthClassifier.analyze(hole("7s", "7c"), board("7d", "Kh", "2s"))
        assertEquals(HandStrengthTier.MONSTER, result.tier)
        assertTrue(result.madeHandDescription.contains("set of seven"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `two pair is monster`() {
        val result = HandStrengthClassifier.analyze(hole("Ks", "7c"), board("Kd", "7h", "2s"))
        assertEquals(HandStrengthTier.MONSTER, result.tier)
        assertTrue(result.madeHandDescription.contains("two pair"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `made flush is monster`() {
        val result = HandStrengthClassifier.analyze(hole("Ah", "9h"), board("Kh", "5h", "3h"))
        assertEquals(HandStrengthTier.MONSTER, result.tier)
        assertTrue(result.madeHandDescription.contains("flush"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `made straight is monster`() {
        val result = HandStrengthClassifier.analyze(hole("7s", "8c"), board("5d", "6h", "9s"))
        assertEquals(HandStrengthTier.MONSTER, result.tier)
        assertTrue(result.madeHandDescription.contains("straight"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `full house is monster`() {
        val result = HandStrengthClassifier.analyze(hole("Ks", "Kc"), board("Kd", "7h", "7s"))
        assertEquals(HandStrengthTier.MONSTER, result.tier)
        assertTrue(result.madeHandDescription.contains("full house"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    // ========== STRONG Tier Tests ==========

    @Test
    fun `overpair is strong`() {
        val result = HandStrengthClassifier.analyze(hole("Qs", "Qc"), board("Jd", "7h", "2s"))
        assertEquals(HandStrengthTier.STRONG, result.tier)
        assertTrue(result.madeHandDescription.contains("overpair"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `top pair top kicker is strong`() {
        val result = HandStrengthClassifier.analyze(hole("As", "Kc"), board("Kd", "7h", "2s"))
        assertEquals(HandStrengthTier.STRONG, result.tier)
        assertTrue(result.madeHandDescription.contains("top pair"), result.madeHandDescription)
        assertTrue(result.madeHandDescription.contains("ace kicker"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `combo draw flush plus oesd promotes to strong`() {
        // 9♥ 8♥ on 6♥ 7♥ K♣ → flush draw (9) + OESD (8) ≈ 15 outs → STRONG
        val result = HandStrengthClassifier.analyze(hole("9h", "8h"), board("6h", "7h", "Kc"))
        assertEquals(HandStrengthTier.STRONG, result.tier)
        assertTrue(result.draws.isNotEmpty())
        assertTrue(result.totalOuts >= 12, "Should have 12+ combined outs, got ${result.totalOuts}")
    }

    // ========== MEDIUM Tier Tests ==========

    @Test
    fun `top pair weak kicker is medium`() {
        val result = HandStrengthClassifier.analyze(hole("Ks", "4c"), board("Kd", "Qh", "8s"))
        assertEquals(HandStrengthTier.MEDIUM, result.tier)
        assertTrue(result.madeHandDescription.contains("top pair"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `second pair is medium`() {
        val result = HandStrengthClassifier.analyze(hole("Qs", "3c"), board("Kd", "Qh", "8s"))
        assertEquals(HandStrengthTier.MEDIUM, result.tier)
        assertTrue(result.madeHandDescription.contains("second pair"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `oesd alone promotes nothing to medium`() {
        // 7♠ 8♣ on 5♦ 6♥ K♠ → no pair + OESD (8 outs) → MEDIUM
        val result = HandStrengthClassifier.analyze(hole("7s", "8c"), board("5d", "6h", "Ks"))
        assertEquals(HandStrengthTier.MEDIUM, result.tier)
        val oesd = result.draws.find { it.type == DrawType.OESD }
        assertTrue(oesd != null, "Should have OESD draw")
    }

    @Test
    fun `pocket pair below one board card is medium`() {
        // Pocket 8s on K-5-3 → below one board card (K) → MEDIUM
        val result = HandStrengthClassifier.analyze(hole("8s", "8c"), board("Kd", "5h", "3s"))
        assertEquals(HandStrengthTier.MEDIUM, result.tier)
        assertTrue(result.madeHand)
    }

    @Test
    fun `second pair plus flush draw promotes to strong`() {
        // Q♥ 3♥ on K♥ Q♦ 8♥ → second pair (Q) + flush draw (9 outs) → STRONG
        val result = HandStrengthClassifier.analyze(hole("Qh", "3h"), board("Kh", "Qd", "8h"))
        assertEquals(HandStrengthTier.STRONG, result.tier)
    }

    // ========== WEAK Tier Tests ==========

    @Test
    fun `bottom pair is weak`() {
        val result = HandStrengthClassifier.analyze(hole("2s", "5c"), board("Kd", "Qh", "5s"))
        assertEquals(HandStrengthTier.WEAK, result.tier)
        assertTrue(result.madeHandDescription.contains("bottom pair"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `gutshot promotes nothing to weak`() {
        // 7♠ 9♣ on 5♦ 6♥ K♠ → gutshot (need 8, 4 outs) → WEAK
        val result = HandStrengthClassifier.analyze(hole("7s", "9c"), board("5d", "6h", "Ks"))
        assertEquals(HandStrengthTier.WEAK, result.tier)
        val gutshot = result.draws.find { it.type == DrawType.GUTSHOT }
        assertTrue(gutshot != null, "Should have gutshot draw")
    }

    @Test
    fun `underpair is weak`() {
        val result = HandStrengthClassifier.analyze(hole("3s", "3c"), board("Kd", "Qh", "8s"))
        assertEquals(HandStrengthTier.WEAK, result.tier)
        assertTrue(result.madeHandDescription.contains("underpair"), result.madeHandDescription)
        assertTrue(result.madeHand)
    }

    @Test
    fun `pocket pair below two board cards is weak`() {
        // Pocket 5s on K-Q-7 → below K and Q → WEAK
        val result = HandStrengthClassifier.analyze(hole("5s", "5c"), board("Kd", "Qh", "7s"))
        assertEquals(HandStrengthTier.WEAK, result.tier)
    }

    // ========== NOTHING Tier Tests ==========

    @Test
    fun `complete air is nothing`() {
        val result = HandStrengthClassifier.analyze(hole("2s", "7c"), board("Kd", "Qh", "9s"))
        assertEquals(HandStrengthTier.NOTHING, result.tier)
        assertFalse(result.madeHand)
    }

    @Test
    fun `ace high no draw is nothing`() {
        // A♠ 3♣ on K♦ Q♥ 9♠ → ace high, no meaningful draw → NOTHING
        val result = HandStrengthClassifier.analyze(hole("As", "3c"), board("Kd", "Qh", "9s"))
        assertEquals(HandStrengthTier.NOTHING, result.tier)
        assertTrue(result.madeHandDescription.contains("ace high"), result.madeHandDescription)
    }

    @Test
    fun `backdoor draws alone stay nothing`() {
        val result2 = HandStrengthClassifier.analyze(hole("2h", "3h"), board("7h", "Kc", "9s"))
        val majorDraws = result2.draws.filter {
            it.type != DrawType.BACKDOOR_FLUSH && it.type != DrawType.BACKDOOR_STRAIGHT
        }
        if (majorDraws.isEmpty() && result2.totalOuts < 4) {
            assertEquals(HandStrengthTier.NOTHING, result2.tier)
        }
    }

    // ========== Draw Promotion Tests ==========

    @Test
    fun `nothing plus flush draw promotes to medium`() {
        val result = HandStrengthClassifier.analyze(hole("Ah", "Js"), board("Kh", "5h", "3h"))
        assertEquals(HandStrengthTier.MEDIUM, result.tier)
    }

    @Test
    fun `nothing plus oesd promotes to medium`() {
        val result = HandStrengthClassifier.analyze(hole("7s", "8c"), board("5d", "6h", "Ks"))
        assertEquals(HandStrengthTier.MEDIUM, result.tier)
    }

    @Test
    fun `nothing plus flush draw plus oesd promotes to strong`() {
        val result = HandStrengthClassifier.analyze(hole("9h", "8h"), board("6h", "7h", "Kc"))
        assertEquals(HandStrengthTier.STRONG, result.tier)
    }

    @Test
    fun `weak bottom pair plus flush draw promotes to medium`() {
        val result = HandStrengthClassifier.analyze(hole("5h", "3h"), board("Kh", "Qh", "5d"))
        assertEquals(HandStrengthTier.MEDIUM, result.tier)
    }

    @Test
    fun `nothing plus gutshot promotes to weak`() {
        val result = HandStrengthClassifier.analyze(hole("7s", "9c"), board("5d", "6h", "Ks"))
        assertEquals(HandStrengthTier.WEAK, result.tier)
    }

    // ========== River Tests (No Draw Promotion) ==========

    @Test
    fun `river missed draw is nothing`() {
        val result = HandStrengthClassifier.analyze(hole("7s", "8c"), board("5d", "6h", "Ks", "Qc", "2d"))
        assertEquals(HandStrengthTier.NOTHING, result.tier)
        assertTrue(result.draws.isEmpty(), "Should have no draws on river")
    }

    @Test
    fun `river top pair weak kicker stays medium`() {
        val result = HandStrengthClassifier.analyze(hole("Ks", "4c"), board("Kd", "Qh", "8s", "3c", "7d"))
        assertEquals(HandStrengthTier.MEDIUM, result.tier)
        assertTrue(result.draws.isEmpty())
    }

    @Test
    fun `river made hand unaffected by former draws`() {
        val result = HandStrengthClassifier.analyze(hole("Ah", "9h"), board("Kh", "5h", "3c", "7d", "2h"))
        assertEquals(HandStrengthTier.MONSTER, result.tier)
        assertTrue(result.draws.isEmpty())
    }

    // ========== Edge Cases ==========

    @Test
    fun `board only pair is weak`() {
        val result = HandStrengthClassifier.analyze(hole("As", "Kc"), board("7d", "7h", "2s"))
        assertEquals(HandStrengthTier.WEAK, result.tier)
    }

    @Test
    fun `board pair with player pairing higher card is two pair monster`() {
        val result = HandStrengthClassifier.analyze(hole("As", "7c"), board("7d", "7h", "2s"))
        assertEquals(HandStrengthTier.MONSTER, result.tier)
    }

    @Test
    fun `paired board player has second card pairing gives two pair`() {
        val result = HandStrengthClassifier.analyze(hole("Ks", "7c"), board("Kd", "7h", "2s"))
        assertEquals(HandStrengthTier.MONSTER, result.tier)
    }

    // ========== hasNutAdvantage Tests ==========

    @Test
    fun `nut advantage with ace of flush suit`() {
        val result = HandStrengthClassifier.analyze(hole("Ah", "Ks"), board("7h", "3h", "9h"))
        assertTrue(result.hasNutAdvantage, "Should have nut advantage with A♥ on 3-heart board")
    }

    @Test
    fun `no nut advantage when ace of suit on board`() {
        val result = HandStrengthClassifier.analyze(hole("Kh", "Qs"), board("Ah", "3h", "9h"))
        assertFalse(result.hasNutAdvantage)
    }

    @Test
    fun `no nut advantage when suit not present`() {
        val result = HandStrengthClassifier.analyze(hole("As", "Ks"), board("7h", "3h", "9d"))
        assertFalse(result.hasNutAdvantage, "No flush possible in spades so A♠ doesn't matter")
    }

    // ========== Description Quality Tests ==========

    @Test
    fun `set description includes set wording`() {
        val result = HandStrengthClassifier.analyze(hole("Js", "Jc"), board("Jd", "5h", "2s"))
        assertTrue(result.madeHandDescription.contains("set of jack"), result.madeHandDescription)
    }

    @Test
    fun `overpair description includes overpair wording`() {
        val result = HandStrengthClassifier.analyze(hole("Qs", "Qc"), board("Jd", "7h", "2s"))
        assertTrue(result.madeHandDescription.contains("overpair"), result.madeHandDescription)
    }

    @Test
    fun `underpair description mentions underpair`() {
        val result = HandStrengthClassifier.analyze(hole("3s", "3c"), board("Kd", "Qh", "8s"))
        assertTrue(result.madeHandDescription.contains("underpair"), result.madeHandDescription)
    }

    @Test
    fun `flush description includes suit high card`() {
        val result = HandStrengthClassifier.analyze(hole("Ah", "9h"), board("Kh", "5h", "3h"))
        assertTrue(result.madeHandDescription.contains("ace"), result.madeHandDescription)
        assertTrue(result.madeHandDescription.contains("flush"), result.madeHandDescription)
    }

    @Test
    fun `straight description includes high card`() {
        val result = HandStrengthClassifier.analyze(hole("7s", "8c"), board("5d", "6h", "9s"))
        assertTrue(result.madeHandDescription.contains("straight"), result.madeHandDescription)
        assertTrue(result.madeHandDescription.contains("nine"), result.madeHandDescription)
    }

    @Test
    fun `draw description included for no-pair hands`() {
        val result = HandStrengthClassifier.analyze(hole("7s", "8c"), board("5d", "6h", "Ks"))
        assertTrue(result.madeHandDescription.contains("straight draw"), result.madeHandDescription)
    }

    @Test
    fun `draw description included with made hand`() {
        val result = HandStrengthClassifier.analyze(hole("Qh", "3h"), board("Kh", "Qd", "8h"))
        assertTrue(result.madeHandDescription.contains("second pair"), result.madeHandDescription)
        assertTrue(result.madeHandDescription.contains("flush draw"), result.madeHandDescription)
    }

    // ========== madeHand Flag Tests ==========

    @Test
    fun `madeHand true for real pair`() {
        val result = HandStrengthClassifier.analyze(hole("Ks", "7c"), board("Kd", "Qh", "8s"))
        assertTrue(result.madeHand)
    }

    @Test
    fun `madeHand false for board only pair`() {
        val result = HandStrengthClassifier.analyze(hole("As", "Kc"), board("7d", "7h", "2s"))
        assertFalse(result.madeHand)
    }

    @Test
    fun `madeHand false for high card`() {
        val result = HandStrengthClassifier.analyze(hole("As", "Kc"), board("Qd", "Jh", "9s"))
        assertFalse(result.madeHand)
    }

    @Test
    fun `madeHand true for two pair`() {
        val result = HandStrengthClassifier.analyze(hole("Ks", "7c"), board("Kd", "7h", "2s"))
        assertTrue(result.madeHand)
    }
}
