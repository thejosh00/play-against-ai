package com.pokerai.archetype

import com.pokerai.model.Difficulty
import com.pokerai.model.archetype.CallingStationArchetype
import com.pokerai.model.archetype.PlayerArchetype
import com.pokerai.model.archetype.SharkArchetype
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerArchetypeWeightsTest {

    @Test
    fun `LOW difficulty produces more CallingStations`() {
        val counts = mutableMapOf<String, Int>()
        repeat(1000) {
            val assignments = PlayerArchetype.assignRandom(1, Difficulty.LOW)
            val archetype = assignments[0].first.archetype.displayName
            counts[archetype] = (counts[archetype] ?: 0) + 1
        }
        val callingStations = counts[CallingStationArchetype.displayName] ?: 0
        val sharks = counts[SharkArchetype.displayName] ?: 0
        assertTrue(callingStations > sharks, "LOW difficulty: CallingStations ($callingStations) should outnumber Sharks ($sharks)")
    }

    @Test
    fun `HIGH difficulty produces more Sharks`() {
        val counts = mutableMapOf<String, Int>()
        repeat(1000) {
            val assignments = PlayerArchetype.assignRandom(1, Difficulty.HIGH)
            val archetype = assignments[0].first.archetype.displayName
            counts[archetype] = (counts[archetype] ?: 0) + 1
        }
        val sharks = counts[SharkArchetype.displayName] ?: 0
        val callingStations = counts[CallingStationArchetype.displayName] ?: 0
        assertTrue(sharks > callingStations, "HIGH difficulty: Sharks ($sharks) should outnumber CallingStations ($callingStations)")
    }

    @Test
    fun `null difficulty uses default weights`() {
        // Should not throw and should produce valid results
        val assignments = PlayerArchetype.assignRandom(5, null)
        assertTrue(assignments.size == 5)
        assertTrue(assignments.all { it.first.archetype in PlayerArchetype.all })
        assertTrue(assignments.all { it.second.isNotEmpty() })
    }

    @Test
    fun `assignRandom returns correct count`() {
        val assignments = PlayerArchetype.assignRandom(3, Difficulty.MEDIUM)
        assertTrue(assignments.size == 3)
    }
}
