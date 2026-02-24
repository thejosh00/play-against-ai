package com.pokerai.eval

/**
 * Formats evaluation reports for console output.
 */
object ReportPrinter {

    fun printFullReport(report: EvalReport) {
        println()
        println("╔══════════════════════════════════════════════════════════════╗")
        println("║           LLM POKER DECISION EVALUATION REPORT             ║")
        println("╚══════════════════════════════════════════════════════════════╝")
        println()
        println("Scenarios: ${report.scenarioCount}  |  Archetypes: ${report.archetypesTested.joinToString(", ")}")
        println()

        printComparisonTable(report.models)

        for (model in report.models) {
            println()
            printModelDetail(model)
        }

        println()
        println("═══════════════════════════════════════════════════════════════")
        println(report.recommendation)
    }

    private fun printComparisonTable(models: List<ModelReport>) {
        println("┌─────────────────────────┬────────┬──────────┬──────────┬───────────┬─────────────┬───────┐")
        println("│ Model                   │ Format │ Fidelity │ Strategy │ Reasoning │ Consistency │ TOTAL │")
        println("├─────────────────────────┼────────┼──────────┼──────────┼───────────┼─────────────┼───────┤")

        for (model in models.sortedByDescending { it.weightedTotal }) {
            val name = model.modelName.take(23).padEnd(23)
            val format = String.format("%5.1f", model.formatScore)
            val fidelity = String.format("%7.1f", model.fidelityScore)
            val strategy = String.format("%7.1f", model.strategyScore)
            val reasoning = String.format("%8.1f", model.reasoningScore)
            val consistency = String.format("%10.1f", model.consistencyScore)
            val total = String.format("%4.1f", model.weightedTotal)
            println("│ $name │ $format │ $fidelity │ $strategy │ $reasoning │ $consistency │ $total │")
        }

        println("└─────────────────────────┴────────┴──────────┴──────────┴───────────┴─────────────┴───────┘")
    }

    private fun printModelDetail(model: ModelReport) {
        println("━━━ ${model.modelName} (${model.provider}) ━━━━━━━━━━━━━━━━━━━━━")
        println("  Weighted Total: ${String.format("%.1f", model.weightedTotal)} / 100")
        println("  Format: ${String.format("%.1f", model.formatScore)}  Fidelity: ${String.format("%.1f", model.fidelityScore)}  " +
            "Strategy: ${String.format("%.1f", model.strategyScore)}  Reasoning: ${String.format("%.1f", model.reasoningScore)}  " +
            "Consistency: ${String.format("%.1f", model.consistencyScore)}")
        println()

        if (model.archetypeScores.isNotEmpty()) {
            println("  Per-Archetype:")
            for ((name, score) in model.archetypeScores) {
                println("    $name: format=${String.format("%.1f", score.avgFormatScore)} " +
                    "strategy=${String.format("%.1f", score.avgStrategyScore)} " +
                    "reasoning=${String.format("%.1f", score.avgReasoningScore)}")
            }
        }

        if (model.difficultyScores.isNotEmpty()) {
            println("  Per-Difficulty:")
            for ((diff, score) in model.difficultyScores.toSortedMap()) {
                println("    ${diff.name}: ${String.format("%.1f", score)}%")
            }
        }

        if (model.commonFailures.isNotEmpty()) {
            println("  Issues:")
            for (failure in model.commonFailures) {
                println("    ! $failure")
            }
        }

        println("  Parse failure rate: ${String.format("%.1f", model.parseFailureRate * 100)}%")
        println("  Catastrophic error rate: ${String.format("%.1f", model.catastrophicErrorRate * 100)}%")
    }

    /**
     * Print a single model report in compact form (useful for quick checks).
     */
    fun printCompact(model: ModelReport) {
        println("${model.modelName}: ${String.format("%.1f", model.weightedTotal)}/100 " +
            "[F:${String.format("%.0f", model.formatScore)} " +
            "Fi:${String.format("%.0f", model.fidelityScore)} " +
            "S:${String.format("%.0f", model.strategyScore)} " +
            "R:${String.format("%.0f", model.reasoningScore)} " +
            "C:${String.format("%.0f", model.consistencyScore)}]")
    }
}
