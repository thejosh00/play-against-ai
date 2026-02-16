package com.pokerai.ai

import com.pokerai.model.Position
import com.pokerai.model.archetype.PlayerArchetype
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HandRankingsTest {

    @Test
    fun `RANKED_HANDS contains exactly 169 unique entries`() {
        assertEquals(169, HandRankings.RANKED_HANDS.size)
        assertEquals(169, HandRankings.RANKED_HANDS.toSet().size)
    }

    @Test
    fun `indexOf returns correct positions for known hands`() {
        assertEquals(0, HandRankings.indexOf("AA"))
        assertEquals(1, HandRankings.indexOf("KK"))
        assertEquals(2, HandRankings.indexOf("QQ"))
        assertEquals(3, HandRankings.indexOf("JJ"))
        assertEquals(4, HandRankings.indexOf("AKs"))
    }

    @Test
    fun `indexOf throws for unknown hand notation`() {
        assertFailsWith<IllegalStateException> {
            HandRankings.indexOf("ZZs")
        }
    }

    @Test
    fun `topN returns expected sets`() {
        val top1 = HandRankings.topN(1)
        assertEquals(setOf("AA"), top1)

        val top5 = HandRankings.topN(5)
        assertEquals(setOf("AA", "KK", "QQ", "JJ", "AKs"), top5)
    }

    @Test
    fun `topN clamps at bounds`() {
        val top0 = HandRankings.topN(0)
        assertTrue(top0.isEmpty())

        val topAll = HandRankings.topN(200)
        assertEquals(169, topAll.size)
    }

    @Test
    fun `rangeToCutoff finds max index plus one`() {
        val range = setOf("AA", "KK", "QQ")
        assertEquals(3, HandRankings.rangeToCutoff(range))
    }

    @Test
    fun `rangeToCutoff handles empty range`() {
        assertEquals(0, HandRankings.rangeToCutoff(emptySet()))
    }

    @Test
    fun `adjustRange widens range with positive adjustment`() {
        val base = setOf("AA", "KK", "QQ") // cutoff = 3
        val adjusted = HandRankings.adjustRange(base, 2)
        // cutoff 3 + 2 = 5 -> top 5
        assertEquals(HandRankings.topN(5), adjusted)
    }

    @Test
    fun `adjustRange tightens range with negative adjustment`() {
        val base = setOf("AA", "KK", "QQ", "JJ", "AKs") // cutoff = 5
        val adjusted = HandRankings.adjustRange(base, -3)
        // cutoff 5 - 3 = 2 -> top 2
        assertEquals(setOf("AA", "KK"), adjusted)
    }

    @Test
    fun `adjustRange clamps at lower bound`() {
        val base = setOf("AA", "KK") // cutoff = 2
        val adjusted = HandRankings.adjustRange(base, -10)
        // cutoff 2 - 10 = -8, clamped to 1 -> top 1
        assertEquals(setOf("AA"), adjusted)
    }

    @Test
    fun `adjustRange clamps at upper bound`() {
        val base = HandRankings.topN(165) // cutoff = 165
        val adjusted = HandRankings.adjustRange(base, 20)
        // cutoff 165 + 20 = 185, clamped to 169
        assertEquals(169, adjusted.size)
    }

    @Test
    fun `all archetype facing-raise hands exist in rankings`() {
        for (archetype in PlayerArchetype.all) {
            for (position in Position.entries) {
                val range = archetype.getFacingRaiseRange(position)
                for (hand in range) {
                    assertTrue(
                        hand in HandRankings.RANKED_HANDS,
                        "Hand $hand from ${archetype.displayName} facing-raise range not found in rankings"
                    )
                }
            }
        }
    }

    @Test
    fun `all archetype facing-3bet hands exist in rankings`() {
        for (archetype in PlayerArchetype.all) {
            val range = archetype.getFacing3BetRange()
            for (hand in range) {
                assertTrue(
                    hand in HandRankings.RANKED_HANDS,
                    "Hand $hand from ${archetype.displayName} facing-3bet range not found in rankings"
                )
            }
        }
    }

    @Test
    fun `all archetype open-raise hands exist in rankings`() {
        for (archetype in PlayerArchetype.all) {
            for (position in Position.entries) {
                val range = archetype.getOpenRange(position)
                for (hand in range) {
                    assertTrue(
                        hand in HandRankings.RANKED_HANDS,
                        "Hand $hand from ${archetype.displayName} open range at $position not found in rankings"
                    )
                }
            }
        }
    }
}
