package com.pokerai.model.archetype

import com.pokerai.model.Difficulty
import com.pokerai.model.PlayerProfile
import com.pokerai.model.Position
import kotlin.random.Random

sealed class PlayerArchetype {
    abstract val displayName: String
    abstract val weight: Int
    abstract val aiNames: List<String>

    abstract fun createProfile(): PlayerProfile
    abstract fun getOpenRange(position: Position): Set<String>
    abstract fun getFacingRaiseRange(position: Position): Set<String>
    abstract fun getFacing3BetRange(): Set<String>
    abstract fun buildSystemPrompt(profile: PlayerProfile): String

    companion object {
        val all: List<PlayerArchetype> = listOf(
            TagArchetype, LagArchetype, CallingStationArchetype, NitArchetype, SharkArchetype
        )

        private val DIFFICULTY_WEIGHTS: Map<Difficulty, Map<PlayerArchetype, Int>> = mapOf(
            Difficulty.LOW to mapOf(
                CallingStationArchetype to 45, NitArchetype to 25, TagArchetype to 15,
                LagArchetype to 10, SharkArchetype to 5
            ),
            Difficulty.MEDIUM to mapOf(
                CallingStationArchetype to 30, NitArchetype to 20, TagArchetype to 25,
                LagArchetype to 15, SharkArchetype to 10
            ),
            Difficulty.HIGH to mapOf(
                CallingStationArchetype to 15, NitArchetype to 15, TagArchetype to 25,
                LagArchetype to 20, SharkArchetype to 25
            ),
        )

        fun assignRandom(count: Int, difficulty: Difficulty? = null): List<Pair<PlayerProfile, String>> {
            val weights = if (difficulty != null) DIFFICULTY_WEIGHTS.getValue(difficulty) else null
            val usedNames = mutableSetOf<String>()
            return (1..count).map {
                val selected = if (weights != null) {
                    val totalWeight = weights.values.sum()
                    val roll = Random.nextInt(totalWeight)
                    var cumulative = 0
                    var picked: PlayerArchetype = TagArchetype
                    for ((archetype, weight) in weights) {
                        cumulative += weight
                        if (roll < cumulative) {
                            picked = archetype
                            break
                        }
                    }
                    picked
                } else {
                    val totalWeight = all.sumOf { it.weight }
                    val roll = Random.nextInt(totalWeight)
                    var cumulative = 0
                    var picked: PlayerArchetype = TagArchetype
                    for (archetype in all) {
                        cumulative += archetype.weight
                        if (roll < cumulative) {
                            picked = archetype
                            break
                        }
                    }
                    picked
                }
                val name = selected.aiNames.firstOrNull { it !in usedNames }
                    ?: all.flatMap { it.aiNames }.first { it !in usedNames }
                usedNames.add(name)
                selected.createProfile() to name
            }
        }
    }
}

fun randomBetween(min: Double, max: Double): Double =
    min + Random.nextDouble() * (max - min)
