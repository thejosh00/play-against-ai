package com.pokerai.eval

import com.pokerai.model.Action
import com.pokerai.model.ActionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.*

class EvalJsonExporterTest {

    private fun minimalReport(): EvalReport {
        val scenarioScore = ScenarioScore(
            scenarioId = "s1",
            modelName = "test-model",
            archetypeName = "Nit",
            formatScore = 3,
            strategyScore = 4,
            reasoningScore = 2,
            action = Action.fold(),
            reasoning = "too weak",
            rawResponse = """{"action":"fold"}""",
            parseResult = ParseResult.Success(Action.fold(), "too weak", """{"action":"fold"}"""),
            latencyMs = 150
        )

        val consistencyResult = ConsistencyResult(
            scenarioId = "s1",
            modelName = "test-model",
            archetypeName = "Nit",
            totalRuns = 5,
            actionCounts = mapOf(ActionType.FOLD to 4, ActionType.CALL to 1),
            dominantAction = ActionType.FOLD,
            dominantActionPct = 0.8,
            hasCatastrophicOutlier = false,
            consistencyScore = 3,
            avgLatencyMs = 120
        )

        val modelReport = ModelReport(
            modelName = "test-model",
            provider = "test",
            temperature = 0.7,
            totalScenarios = 1,
            totalRuns = 6,
            formatScore = 100.0,
            fidelityScore = 50.0,
            strategyScore = 100.0,
            reasoningScore = 66.7,
            consistencyScore = 100.0,
            weightedTotal = 85.0,
            archetypeScores = mapOf(
                "Nit" to ArchetypeScore("Nit", 3.0, 4.0, 2.0, 0.0, 3.0)
            ),
            difficultyScores = mapOf(EvalDifficulty.EASY to 100.0),
            parseFailureRate = 0.0,
            catastrophicErrorRate = 0.0,
            commonFailures = emptyList(),
            scenarioScores = listOf(scenarioScore),
            consistencyResults = listOf(consistencyResult)
        )

        return EvalReport(
            timestamp = 1700000000000L,
            models = listOf(modelReport),
            scenarioCount = 1,
            archetypesTested = listOf("Nit", "Calling Station", "LAG", "TAG", "Shark"),
            recommendation = "RANKING: 1. test-model"
        )
    }

    @Test
    fun `toJsonString produces valid JSON`() {
        val report = minimalReport()
        val jsonString = EvalJsonExporter.toJsonString(report)

        // Should not throw
        val parsed = Json.parseToJsonElement(jsonString)
        assertNotNull(parsed)
    }

    @Test
    fun `JSON contains model scores`() {
        val jsonString = EvalJsonExporter.toJsonString(minimalReport())
        val root = Json.parseToJsonElement(jsonString).jsonObject

        val models = root["models"]!!.jsonArray
        assertEquals(1, models.size)

        val scores = models[0].jsonObject["scores"]!!.jsonObject
        assertNotNull(scores["weightedTotal"])
        assertNotNull(scores["format"])
        assertNotNull(scores["strategy"])
    }

    @Test
    fun `JSON contains scenario scores`() {
        val jsonString = EvalJsonExporter.toJsonString(minimalReport())
        val root = Json.parseToJsonElement(jsonString).jsonObject

        val scenarioScores = root["models"]!!.jsonArray[0].jsonObject["scenarioScores"]!!.jsonArray
        assertEquals(1, scenarioScores.size)
        assertEquals("s1", scenarioScores[0].jsonObject["scenarioId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `JSON contains consistency results`() {
        val jsonString = EvalJsonExporter.toJsonString(minimalReport())
        val root = Json.parseToJsonElement(jsonString).jsonObject

        val results = root["models"]!!.jsonArray[0].jsonObject["consistencyResults"]!!.jsonArray
        assertEquals(1, results.size)

        val actionCounts = results[0].jsonObject["actionCounts"]!!.jsonObject
        assertTrue(actionCounts.containsKey("FOLD"))
    }

    @Test
    fun `JSON contains top-level fields`() {
        val jsonString = EvalJsonExporter.toJsonString(minimalReport())
        val root = Json.parseToJsonElement(jsonString).jsonObject

        assertNotNull(root["timestamp"])
        assertNotNull(root["scenarioCount"])
        assertNotNull(root["archetypesTested"])
        assertNotNull(root["recommendation"])
    }

    @Test
    fun `export writes file to disk`() {
        val tmpFile = File.createTempFile("eval_test_", ".json")
        tmpFile.deleteOnExit()

        EvalJsonExporter.export(minimalReport(), tmpFile.absolutePath)

        assertTrue(tmpFile.exists())
        assertTrue(tmpFile.length() > 0)

        // Verify it's valid JSON
        val content = tmpFile.readText()
        Json.parseToJsonElement(content)
    }

    @Test
    fun `exportOracle writes oracle report to disk`() {
        val tmpFile = File.createTempFile("oracle_test_", ".json")
        tmpFile.deleteOnExit()

        val oracleReport = OracleReport(
            results = listOf(
                OracleResult(
                    scenarioId = "s1",
                    archetypeName = "Nit",
                    actionCounts = mapOf(ActionType.FOLD to 80, ActionType.CALL to 20),
                    totalRuns = 100,
                    dominantAction = ActionType.FOLD,
                    dominantPct = 0.8,
                    avgConfidence = 0.75,
                    distribution = ActionDistribution(0.8..0.8, 0.0..0.0, 0.2..0.2, 0.0..0.0)
                )
            ),
            calibrationErrors = listOf("test error"),
            totalScenarios = 1,
            totalArchetypes = 5
        )

        EvalJsonExporter.exportOracle(oracleReport, tmpFile.absolutePath)

        assertTrue(tmpFile.exists())
        val content = tmpFile.readText()
        val root = Json.parseToJsonElement(content).jsonObject
        assertTrue(root.containsKey("calibrationErrors"))
        assertTrue(root.containsKey("results"))
    }
}
