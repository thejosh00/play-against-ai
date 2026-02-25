package com.pokerai.eval

import kotlin.test.*

class EvalCliTest {

    @Test
    fun `parseArgs with quick flag`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--quick", "--ollama", "llama3.1:8b"))

        assertTrue(config.quick)
        assertEquals(3, config.repetitions)
        assertEquals(listOf("llama3.1:8b"), config.ollamaModels)
    }

    @Test
    fun `parseArgs with full flag`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--full"))

        assertTrue(config.full)
        assertEquals(20, config.repetitions)
    }

    @Test
    fun `parseArgs with multiple models`() {
        val config = EvalCli.parseArgs(arrayOf(
            "eval", "--ollama", "llama3.1:8b", "--ollama", "mistral:7b",
            "--openrouter", "openai/gpt-4o-mini"
        ))

        assertEquals(listOf("llama3.1:8b", "mistral:7b"), config.ollamaModels)
        assertEquals(listOf("openai/gpt-4o-mini"), config.openRouterModels)
    }

    @Test
    fun `parseArgs with judge`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--judge", "anthropic/claude-sonnet-4-20250514"))

        assertEquals("anthropic/claude-sonnet-4-20250514", config.judgeModel)
    }

    @Test
    fun `parseArgs with json output`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--json", "results.json"))

        assertEquals("results.json", config.jsonOutput)
    }

    @Test
    fun `parseArgs with temperature`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--temperature", "0.5"))

        assertEquals(0.5, config.temperature)
    }

    @Test
    fun `parseArgs with reps`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--reps", "10"))

        assertEquals(10, config.repetitions)
    }

    @Test
    fun `parseArgs with oracle`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--oracle"))

        assertTrue(config.oracleOnly)
    }

    @Test
    fun `parseArgs with help`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--help"))

        assertTrue(config.showHelp)
    }

    @Test
    fun `parseArgs with short help flag`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "-h"))

        assertTrue(config.showHelp)
    }

    @Test
    fun `parseArgs defaults`() {
        val config = EvalCli.parseArgs(arrayOf("eval"))

        assertFalse(config.quick)
        assertFalse(config.full)
        assertFalse(config.oracleOnly)
        assertEquals(0.7, config.temperature)
        assertEquals(5, config.repetitions)
        assertTrue(config.ollamaModels.isEmpty())
        assertTrue(config.openRouterModels.isEmpty())
        assertNull(config.judgeModel)
        assertNull(config.jsonOutput)
    }

    @Test
    fun `parseArgs with with-oracle flag`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--with-oracle"))

        assertTrue(config.includeOracle)
    }

    @Test
    fun `parseArgs with delay`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--delay", "500"))

        assertEquals(500L, config.delayMs)
    }

    @Test
    fun `parseArgs with ollama-url`() {
        val config = EvalCli.parseArgs(arrayOf("eval", "--ollama-url", "http://192.168.1.100:11434"))

        assertEquals("http://192.168.1.100:11434", config.ollamaUrl)
    }

    @Test
    fun `quick scenario set has approximately 10 scenarios`() {
        val quick = EvalCli.buildQuickScenarioSet()

        assertTrue(quick.size in 8..12, "Quick set should have 8-12 scenarios, got ${quick.size}")
    }

    @Test
    fun `quick scenario set has mixed categories`() {
        val quick = EvalCli.buildQuickScenarioSet()
        val categories = quick.map { it.category }.toSet()

        assertTrue(
            ScenarioCategory.FORMAT_COMPLIANCE in categories,
            "Quick set should include FORMAT_COMPLIANCE"
        )
        assertTrue(
            ScenarioCategory.ARCHETYPE_FIDELITY in categories,
            "Quick set should include ARCHETYPE_FIDELITY"
        )
        assertTrue(
            ScenarioCategory.STRATEGIC_CORRECTNESS in categories,
            "Quick set should include STRATEGIC_CORRECTNESS"
        )
    }

    @Test
    fun `selectScenarios returns all for full mode`() {
        val config = EvalCli.EvalConfig(full = true)
        val scenarios = EvalCli.selectScenarios(config)

        assertEquals(ScenarioLibrary.all().size, scenarios.size)
    }

    @Test
    fun `selectScenarios returns quick set for quick mode`() {
        val config = EvalCli.EvalConfig(quick = true)
        val scenarios = EvalCli.selectScenarios(config)

        assertTrue(scenarios.size < ScenarioLibrary.all().size)
        assertTrue(scenarios.size in 8..12)
    }
}
