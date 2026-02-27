package com.pokerai.eval

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Top-level entry point so Gradle can invoke this as a separate main class.
 *
 * Usage:
 *   ./gradlew runEval --args="--quick --ollama llama3.1:8b"
 *   ./gradlew runEval --args="--full --openrouter meta-llama/llama-3.1-8b-instruct"
 *   ./gradlew runEval --args="--oracle"
 *   ./gradlew runEval --args="--quick --judge anthropic/claude-sonnet-4-20250514 --ollama llama3.1:8b"
 *   ./gradlew runEval --args="--quick --ollama llama3.1:8b --json results.json"
 *
 * Environment variables:
 *   OPENROUTER_API_KEY - required for OpenRouter models and LLM judge
 *   OLLAMA_URL - Ollama base URL (default: http://localhost:11434)
 */
fun main(args: Array<String>) {
    EvalCli.main(args)
}

object EvalCli {

    fun main(_args: Array<String>) {
        //val args = _args
        val args = arrayOf("--quick", "--openrouter", "minimax/minimax-m2.5:nitro", "--timeout", "180")
        val config = parseArgs(args)

        if (config.showHelp) {
            printHelp()
            return
        }

        val httpClient = createHttpClient(config.timeoutMs)

        try {
            runBlocking {
                if (config.oracleOnly) {
                    runOracleValidation(config)
                    return@runBlocking
                }

                val adapters = buildAdapters(config, httpClient)
                if (adapters.isEmpty()) {
                    println("ERROR: No models specified. Use --ollama or --openrouter to add models.")
                    println()
                    printHelp()
                    return@runBlocking
                }

                val scenarios = selectScenarios(config)
                val judge = buildJudge(config, httpClient)

                println("══════════════════════════════════════════════════════════════")
                println("              LLM POKER EVALUATION STARTING                  ")
                println("══════════════════════════════════════════════════════════════")
                println()
                println("Mode: ${if (config.quick) "QUICK" else "FULL"}")
                println("Models: ${adapters.joinToString(", ") { "${it.modelName} (${it.provider})" }}")
                println("Scenarios: ${scenarios.size}")
                println("Repetitions: ${config.repetitions}")
                println("Temperature: ${config.temperature}")
                println("LLM Judge: ${if (judge.isEnabled) config.judgeModel ?: "none" else "disabled (heuristic)"}")
                println()

                val runner = EvalRunner(
                    adapters = adapters,
                    scenarios = scenarios,
                    repetitions = config.repetitions,
                    temperature = config.temperature,
                    delayBetweenCallsMs = config.delayMs,
                    reasoningJudge = judge
                )

                val report = runner.runFullEval()

                ReportPrinter.printFullReport(report)

                if (config.jsonOutput != null) {
                    EvalJsonExporter.export(report, config.jsonOutput)
                }

                if (config.includeOracle) {
                    println()
                    runOracleValidation(config)
                }
            }
        } finally {
            httpClient.close()
        }
    }

    // -- Configuration ---------------------------------------------------

    data class EvalConfig(
        val quick: Boolean = false,
        val full: Boolean = false,
        val oracleOnly: Boolean = false,
        val includeOracle: Boolean = false,
        val ollamaModels: List<String> = emptyList(),
        val openRouterModels: List<String> = emptyList(),
        val judgeModel: String? = null,
        val temperature: Double = 0.7,
        val repetitions: Int = 5,
        val delayMs: Long = 100,
        val jsonOutput: String? = null,
        val ollamaUrl: String = System.getenv("OLLAMA_URL") ?: "http://localhost:11434",
        val timeoutMs: Long = 30_000,
        val openRouterApiKey: String? = System.getenv("OPENROUTER_API_KEY"),
        val showHelp: Boolean = false
    )

    fun parseArgs(args: Array<String>): EvalConfig {
        var config = EvalConfig()
        val ollamaModels = mutableListOf<String>()
        val openRouterModels = mutableListOf<String>()

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "eval" -> {} // skip the subcommand itself
                "--quick" -> config = config.copy(quick = true, repetitions = 3)
                "--full" -> config = config.copy(full = true, repetitions = 20)
                "--oracle" -> config = config.copy(oracleOnly = true)
                "--with-oracle" -> config = config.copy(includeOracle = true)
                "--help", "-h" -> config = config.copy(showHelp = true)

                "--ollama" -> {
                    i++
                    if (i < args.size) ollamaModels.add(args[i])
                }
                "--openrouter" -> {
                    i++
                    if (i < args.size) openRouterModels.add(args[i])
                }
                "--judge" -> {
                    i++
                    if (i < args.size) config = config.copy(judgeModel = args[i])
                }
                "--temperature", "--temp" -> {
                    i++
                    if (i < args.size) config = config.copy(temperature = args[i].toDoubleOrNull() ?: 0.7)
                }
                "--reps" -> {
                    i++
                    if (i < args.size) config = config.copy(repetitions = args[i].toIntOrNull() ?: 5)
                }
                "--delay" -> {
                    i++
                    if (i < args.size) config = config.copy(delayMs = args[i].toLongOrNull() ?: 100)
                }
                "--json" -> {
                    i++
                    if (i < args.size) config = config.copy(jsonOutput = args[i])
                }
                "--ollama-url" -> {
                    i++
                    if (i < args.size) config = config.copy(ollamaUrl = args[i])
                }
                "--timeout" -> {
                    i++
                    if (i < args.size) config = config.copy(timeoutMs = (args[i].toLongOrNull() ?: 30) * 1000)
                }
            }
            i++
        }

        return config.copy(
            ollamaModels = ollamaModels,
            openRouterModels = openRouterModels
        )
    }

    // -- Builder helpers -------------------------------------------------

    private fun buildAdapters(config: EvalConfig, httpClient: HttpClient): List<LlmAdapter> {
        val adapters = mutableListOf<LlmAdapter>()

        for (model in config.ollamaModels) {
            adapters.add(OllamaEvalAdapter(
                httpClient = httpClient,
                baseUrl = config.ollamaUrl,
                modelName = model
            ))
        }

        for (model in config.openRouterModels) {
            val apiKey = config.openRouterApiKey
            if (apiKey.isNullOrBlank()) {
                println("WARNING: OPENROUTER_API_KEY not set. Skipping OpenRouter model: $model")
                continue
            }
            adapters.add(OpenRouterEvalAdapter(
                httpClient = httpClient,
                apiKey = apiKey,
                modelName = model
            ))
        }

        return adapters
    }

    internal fun selectScenarios(config: EvalConfig): List<EvalScenario> {
        return if (config.quick) {
            buildQuickScenarioSet()
        } else {
            ScenarioLibrary.all()
        }
    }

    /**
     * Quick eval set: ~10 scenarios covering key decision types.
     * 3 easy, 4 medium, 3 hard. Maximum discrimination in minimum calls.
     */
    internal fun buildQuickScenarioSet(): List<EvalScenario> {
        val quick = mutableListOf<EvalScenario>()

        // 3 easy: format sanity
        quick.addAll(ScenarioLibrary.byCategory(ScenarioCategory.FORMAT_COMPLIANCE).take(3))

        // 2 fidelity: the two most discriminating scenarios
        quick.addAll(ScenarioLibrary.byCategory(ScenarioCategory.ARCHETYPE_FIDELITY).take(2))

        // 3 medium strategic
        quick.addAll(
            ScenarioLibrary.byCategory(ScenarioCategory.STRATEGIC_CORRECTNESS)
                .filter { it.difficulty == EvalDifficulty.MEDIUM }
                .take(3)
        )

        // 2 hard strategic
        quick.addAll(
            ScenarioLibrary.byCategory(ScenarioCategory.STRATEGIC_CORRECTNESS)
                .filter { it.difficulty == EvalDifficulty.HARD }
                .take(2)
        )

        return quick
    }

    private fun buildJudge(config: EvalConfig, httpClient: HttpClient): ReasoningJudge {
        val judgeModel = config.judgeModel
        val apiKey = config.openRouterApiKey

        if (judgeModel == null || apiKey.isNullOrBlank()) {
            return ReasoningJudge(judgeAdapter = null, isEnabled = false)
        }

        val adapter = OpenRouterEvalAdapter(
            httpClient = httpClient,
            apiKey = apiKey,
            modelName = judgeModel
        )

        return ReasoningJudge(judgeAdapter = adapter, isEnabled = true)
    }

    private fun runOracleValidation(config: EvalConfig) {
        println("Running oracle validation...")
        val scenarios = ScenarioLibrary.all()
        val report = OracleValidator.validate(scenarios)
        OracleValidator.printReport(report)

        if (config.jsonOutput != null) {
            val oraclePath = config.jsonOutput.replace(".json", "_oracle.json")
            EvalJsonExporter.exportOracle(report, oraclePath)
        }
    }

    private fun createHttpClient(timeoutMs: Long): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        engine {
            requestTimeout = timeoutMs
        }
    }

    private fun printHelp() {
        println("""
            LLM Poker Evaluation Framework
            ===============================

            Usage: eval [options]

            Modes:
              --quick             Quick eval: ~10 scenarios, 3 reps (default)
              --full              Full eval: all ~85 scenarios, 20 reps
              --oracle            Oracle validation only (no LLM calls)

            Models:
              --ollama <model>    Add an Ollama model (e.g., llama3.1:8b)
              --openrouter <model> Add an OpenRouter model (e.g., openai/gpt-4o-mini)
              Multiple models can be specified by repeating the flag.

            Scoring:
              --judge <model>     Use an LLM judge for reasoning scoring (OpenRouter model)
              --with-oracle       Include oracle validation alongside LLM eval

            Settings:
              --temperature <n>   Sampling temperature (default: 0.7)
              --reps <n>          Repetitions for consistency testing (default: 5)
              --delay <ms>        Delay between API calls in ms (default: 100)
              --ollama-url <url>  Ollama base URL (default: http://localhost:11434)
              --timeout <secs>    HTTP request timeout in seconds (default: 30)

            Output:
              --json <path>       Export results to JSON file

            Environment:
              OPENROUTER_API_KEY  Required for OpenRouter models and LLM judge
              OLLAMA_URL          Override Ollama URL (alternative to --ollama-url)

            Examples:
              ./gradlew runEval --args="--quick --ollama llama3.1:8b"
              ./gradlew runEval --args="--quick --ollama llama3.1:8b --ollama mistral:7b --json results.json"
              ./gradlew runEval --args="--full --openrouter openai/gpt-4o-mini --judge anthropic/claude-sonnet-4-20250514"
              ./gradlew runEval --args="--oracle"
        """.trimIndent())
    }
}
