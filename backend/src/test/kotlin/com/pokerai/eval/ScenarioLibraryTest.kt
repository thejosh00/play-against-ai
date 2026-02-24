package com.pokerai.eval

import com.pokerai.model.ActionType
import kotlin.test.*

class ScenarioLibraryTest {

    @Test
    fun `all returns expected count`() {
        val all = ScenarioLibrary.all()
        assertEquals(85, all.size)
    }

    @Test
    fun `no duplicate IDs`() {
        val all = ScenarioLibrary.all()
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Found duplicate scenario IDs: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test
    fun `all categories represented`() {
        val byCategory = ScenarioLibrary.all().groupBy { it.category }
        assertEquals(10, byCategory[ScenarioCategory.FORMAT_COMPLIANCE]?.size, "FORMAT_COMPLIANCE count")
        assertEquals(15, byCategory[ScenarioCategory.ARCHETYPE_FIDELITY]?.size, "ARCHETYPE_FIDELITY count")
        assertEquals(40, byCategory[ScenarioCategory.STRATEGIC_CORRECTNESS]?.size, "STRATEGIC_CORRECTNESS count")
        assertEquals(20, byCategory[ScenarioCategory.BEHAVIORAL_CONSISTENCY]?.size, "BEHAVIORAL_CONSISTENCY count")
    }

    @Test
    fun `all difficulties represented`() {
        val byDifficulty = ScenarioLibrary.all().groupBy { it.difficulty }
        assertTrue(byDifficulty.containsKey(EvalDifficulty.EASY), "Should have EASY scenarios")
        assertTrue(byDifficulty.containsKey(EvalDifficulty.MEDIUM), "Should have MEDIUM scenarios")
        assertTrue(byDifficulty.containsKey(EvalDifficulty.HARD), "Should have HARD scenarios")
        assertTrue(byDifficulty.containsKey(EvalDifficulty.EXPERT), "Should have EXPERT scenarios")
    }

    @Test
    fun `every scenario has at least one correct action`() {
        ScenarioLibrary.all().forEach { scenario ->
            assertTrue(scenario.correctActions.isNotEmpty(), "Scenario ${scenario.id} has no correct actions")
        }
    }

    @Test
    fun `no scenario has same action in both correct and wrong`() {
        ScenarioLibrary.all().forEach { scenario ->
            val correctTypes = scenario.correctActions.map { it.actionType }.toSet()
            val overlap = correctTypes.intersect(scenario.wrongActions)
            assertTrue(overlap.isEmpty(), "Scenario ${scenario.id} has actions in both correct and wrong: $overlap")
        }
    }

    @Test
    fun `fidelity scenarios have distributions for all 5 archetypes`() {
        val expectedArchetypes = setOf("Nit", "Calling Station", "LAG", "TAG", "Shark")
        ScenarioLibrary.byCategory(ScenarioCategory.ARCHETYPE_FIDELITY).forEach { scenario ->
            assertNotNull(scenario.archetypeDistributions, "Fidelity scenario ${scenario.id} missing distributions")
            val keys = scenario.archetypeDistributions!!.keys
            assertEquals(expectedArchetypes, keys, "Fidelity scenario ${scenario.id} missing archetypes: ${expectedArchetypes - keys}")
        }
    }

    @Test
    fun `distribution percentages sum to approximately 100 percent`() {
        ScenarioLibrary.all()
            .filter { it.archetypeDistributions != null }
            .forEach { scenario ->
                scenario.archetypeDistributions!!.forEach { (archetype, dist) ->
                    val midFold = (dist.foldPct.start + dist.foldPct.endInclusive) / 2
                    val midCheck = (dist.checkPct.start + dist.checkPct.endInclusive) / 2
                    val midCall = (dist.callPct.start + dist.callPct.endInclusive) / 2
                    val midRaise = (dist.raisePct.start + dist.raisePct.endInclusive) / 2
                    val total = midFold + midCheck + midCall + midRaise
                    assertTrue(
                        total in 0.8..1.2,
                        "Scenario ${scenario.id}, archetype $archetype: distribution midpoints sum to $total (expected ~1.0)"
                    )
                }
            }
    }

    @Test
    fun `pot odds are calculated correctly`() {
        // Find a scenario with known betToCall and potSize
        // fc_01 has potSize=100, betToCall=100 → potOdds = 100/(100+100) = 0.5
        val fc01 = ScenarioLibrary.all().first { it.id == "fc_01" }
        assertEquals(0.5, fc01.context.potOdds, 0.001)
    }

    @Test
    fun `SPR is calculated correctly`() {
        // fc_01 has effectiveStack=1000, potSize=100 → spr = 10.0
        val fc01 = ScenarioLibrary.all().first { it.id == "fc_01" }
        assertEquals(10.0, fc01.context.spr, 0.001)
    }

    @Test
    fun `format compliance scenarios use easy or medium difficulty`() {
        ScenarioLibrary.byCategory(ScenarioCategory.FORMAT_COMPLIANCE).forEach { scenario ->
            assertTrue(
                scenario.difficulty == EvalDifficulty.EASY || scenario.difficulty == EvalDifficulty.MEDIUM,
                "Format compliance scenario ${scenario.id} has difficulty ${scenario.difficulty}, expected EASY or MEDIUM"
            )
        }
    }

    @Test
    fun `byCategory filter works`() {
        val fidelity = ScenarioLibrary.byCategory(ScenarioCategory.ARCHETYPE_FIDELITY)
        assertTrue(fidelity.all { it.category == ScenarioCategory.ARCHETYPE_FIDELITY })
        assertTrue(fidelity.isNotEmpty())
    }

    @Test
    fun `byTag filter works`() {
        val bluffScenarios = ScenarioLibrary.byTag("bluff")
        assertTrue(bluffScenarios.all { "bluff" in it.tags })
        assertTrue(bluffScenarios.isNotEmpty())
    }

    @Test
    fun `byDifficulty filter works`() {
        val easy = ScenarioLibrary.byDifficulty(EvalDifficulty.EASY)
        assertTrue(easy.all { it.difficulty == EvalDifficulty.EASY })
        assertTrue(easy.isNotEmpty())
    }

    @Test
    fun `easy strategic scenarios have clear wrong actions`() {
        ScenarioLibrary.all()
            .filter { it.difficulty == EvalDifficulty.EASY && it.category == ScenarioCategory.STRATEGIC_CORRECTNESS }
            .forEach { scenario ->
                assertTrue(
                    scenario.wrongActions.isNotEmpty(),
                    "Easy strategic scenario ${scenario.id} should have wrong actions"
                )
            }
    }
}
