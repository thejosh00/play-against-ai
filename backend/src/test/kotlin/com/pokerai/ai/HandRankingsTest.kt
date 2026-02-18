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
    fun `all archetype cutoffs are in valid range`() {
        for (archetype in PlayerArchetype.all) {
            for (position in Position.entries) {
                val openCutoff = archetype.getOpenCutoff(position)
                assertTrue(
                    openCutoff in 1..169,
                    "${archetype.displayName} open cutoff $openCutoff at $position out of range"
                )
                val facingRaiseCutoff = archetype.getFacingRaiseCutoff(position)
                assertTrue(
                    facingRaiseCutoff in 1..169,
                    "${archetype.displayName} facing-raise cutoff $facingRaiseCutoff at $position out of range"
                )
            }
            val facing3BetCutoff = archetype.getFacing3BetCutoff()
            assertTrue(
                facing3BetCutoff in 1..169,
                "${archetype.displayName} facing-3bet cutoff $facing3BetCutoff out of range"
            )
        }
    }

    @Test
    fun `BTN open cutoff greater than UTG open cutoff for all archetypes`() {
        for (archetype in PlayerArchetype.all) {
            val btnCutoff = archetype.getOpenCutoff(Position.BTN)
            val utgCutoff = archetype.getOpenCutoff(Position.UTG)
            assertTrue(
                btnCutoff > utgCutoff,
                "${archetype.displayName} BTN cutoff ($btnCutoff) should be > UTG cutoff ($utgCutoff)"
            )
        }
    }

    @Test
    fun `facing 3bet cutoff is at most facing raise cutoff for all archetypes`() {
        for (archetype in PlayerArchetype.all) {
            val facing3Bet = archetype.getFacing3BetCutoff()
            for (position in Position.entries) {
                val facingRaise = archetype.getFacingRaiseCutoff(position)
                assertTrue(
                    facing3Bet <= facingRaise,
                    "${archetype.displayName} facing-3bet cutoff ($facing3Bet) should be <= facing-raise cutoff ($facingRaise) at $position"
                )
            }
        }
    }
}
