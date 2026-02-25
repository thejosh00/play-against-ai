package com.pokerai.eval

import kotlinx.serialization.json.*
import java.io.File

/**
 * Exports evaluation reports to JSON for programmatic analysis.
 *
 * JSON reports can be:
 * - Compared across runs to track model improvement over time
 * - Loaded into notebooks or dashboards for visualization
 * - Diffed to see what changed between eval runs
 */
object EvalJsonExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Export an EvalReport to a JSON file.
     */
    fun export(report: EvalReport, outputPath: String) {
        val jsonString = toJsonString(report)
        File(outputPath).writeText(jsonString)
        println("Report exported to: $outputPath")
    }

    /**
     * Convert an EvalReport to a JSON string.
     *
     * Since EvalReport contains complex nested types that may not all be
     * @Serializable, we build the JSON manually using kotlinx.serialization's
     * JsonObject builder.
     */
    fun toJsonString(report: EvalReport): String {
        val root = buildJsonObject {
            put("timestamp", report.timestamp)
            put("scenarioCount", report.scenarioCount)
            put("archetypesTested", buildJsonArray {
                report.archetypesTested.forEach { add(it) }
            })
            put("recommendation", report.recommendation)

            put("models", buildJsonArray {
                for (model in report.models) {
                    add(buildModelJson(model))
                }
            })
        }

        return json.encodeToString(JsonElement.serializer(), root)
    }

    private fun buildModelJson(model: ModelReport): JsonElement = buildJsonObject {
        put("modelName", model.modelName)
        put("provider", model.provider)
        put("temperature", model.temperature)
        put("totalScenarios", model.totalScenarios)
        put("totalRuns", model.totalRuns)

        put("scores", buildJsonObject {
            put("format", model.formatScore)
            put("fidelity", model.fidelityScore)
            put("strategy", model.strategyScore)
            put("reasoning", model.reasoningScore)
            put("consistency", model.consistencyScore)
            put("weightedTotal", model.weightedTotal)
        })

        put("parseFailureRate", model.parseFailureRate)
        put("catastrophicErrorRate", model.catastrophicErrorRate)

        put("commonFailures", buildJsonArray {
            model.commonFailures.forEach { add(it) }
        })

        put("archetypeScores", buildJsonObject {
            for ((name, score) in model.archetypeScores) {
                put(name, buildJsonObject {
                    put("avgFormatScore", score.avgFormatScore)
                    put("avgStrategyScore", score.avgStrategyScore)
                    put("avgReasoningScore", score.avgReasoningScore)
                    put("fidelityScore", score.fidelityScore)
                    put("avgConsistencyScore", score.avgConsistencyScore)
                })
            }
        })

        put("difficultyScores", buildJsonObject {
            for ((diff, score) in model.difficultyScores) {
                put(diff.name, score)
            }
        })

        put("scenarioScores", buildJsonArray {
            for (score in model.scenarioScores) {
                add(buildJsonObject {
                    put("scenarioId", score.scenarioId)
                    put("archetypeName", score.archetypeName)
                    put("formatScore", score.formatScore)
                    put("strategyScore", score.strategyScore)
                    put("reasoningScore", score.reasoningScore)
                    put("action", score.action?.type?.name)
                    put("actionAmount", score.action?.amount?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("reasoning", score.reasoning)
                    put("latencyMs", score.latencyMs)
                })
            }
        })

        put("consistencyResults", buildJsonArray {
            for (cr in model.consistencyResults) {
                add(buildJsonObject {
                    put("scenarioId", cr.scenarioId)
                    put("archetypeName", cr.archetypeName)
                    put("totalRuns", cr.totalRuns)
                    put("dominantAction", cr.dominantAction?.name)
                    put("dominantActionPct", cr.dominantActionPct)
                    put("hasCatastrophicOutlier", cr.hasCatastrophicOutlier)
                    put("consistencyScore", cr.consistencyScore)
                    put("avgLatencyMs", cr.avgLatencyMs)
                    put("actionCounts", buildJsonObject {
                        for ((action, count) in cr.actionCounts) {
                            put(action.name, count)
                        }
                    })
                })
            }
        })
    }

    /**
     * Export oracle validation results to JSON.
     */
    fun exportOracle(report: OracleReport, outputPath: String) {
        val root = buildJsonObject {
            put("totalScenarios", report.totalScenarios)
            put("totalArchetypes", report.totalArchetypes)
            put("calibrationErrorCount", report.calibrationErrors.size)

            put("calibrationErrors", buildJsonArray {
                report.calibrationErrors.forEach { add(it) }
            })

            put("results", buildJsonArray {
                for (result in report.results) {
                    add(buildJsonObject {
                        put("scenarioId", result.scenarioId)
                        put("archetypeName", result.archetypeName)
                        put("dominantAction", result.dominantAction.name)
                        put("dominantPct", result.dominantPct)
                        put("avgConfidence", result.avgConfidence)
                        put("actionCounts", buildJsonObject {
                            for ((action, count) in result.actionCounts) {
                                put(action.name, count)
                            }
                        })
                    })
                }
            })
        }

        val jsonString = json.encodeToString(JsonElement.serializer(), root)
        File(outputPath).writeText(jsonString)
        println("Oracle report exported to: $outputPath")
    }
}
