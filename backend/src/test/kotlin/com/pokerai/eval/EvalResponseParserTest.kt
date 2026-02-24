package com.pokerai.eval

import com.pokerai.model.ActionType
import kotlin.test.*

class EvalResponseParserTest {

    // ── Perfect JSON ─────────────────────────────────────

    @Test
    fun `parses perfect JSON into Success`() {
        val input = """{"action": "fold", "amount": null, "reasoning": "too weak"}"""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(ActionType.FOLD, success.action.type)
        assertEquals("too weak", success.reasoning)
    }

    @Test
    fun `parses call with amount`() {
        val input = """{"action": "call", "amount": 50, "reasoning": "good odds"}"""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(ActionType.CALL, success.action.type)
        assertEquals(50, success.action.amount)
    }

    @Test
    fun `parses raise with amount`() {
        val input = """{"action": "raise", "amount": 200, "reasoning": "value bet"}"""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(ActionType.RAISE, success.action.type)
        assertEquals(200, success.action.amount)
    }

    @Test
    fun `parses check action`() {
        val input = """{"action": "check", "amount": null, "reasoning": "check to see free card"}"""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Success)
        assertEquals(ActionType.CHECK, (result as ParseResult.Success).action.type)
    }

    // ── all_in variations ────────────────────────────────

    @Test
    fun `parses all_in with underscore`() {
        val input = """{"action": "all_in", "amount": null, "reasoning": "nuts"}"""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Success)
        assertEquals(ActionType.ALL_IN, (result as ParseResult.Success).action.type)
    }

    @Test
    fun `parses allin without separator`() {
        val input = """{"action": "allin", "amount": null, "reasoning": "..."}"""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Success)
        assertEquals(ActionType.ALL_IN, (result as ParseResult.Success).action.type)
    }

    @Test
    fun `parses all-in with hyphen`() {
        val input = """{"action": "all-in", "amount": null, "reasoning": "..."}"""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Success)
        assertEquals(ActionType.ALL_IN, (result as ParseResult.Success).action.type)
    }

    // ── Missing fields ───────────────────────────────────

    @Test
    fun `handles missing reasoning field`() {
        val input = """{"action": "fold", "amount": null}"""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(ActionType.FOLD, success.action.type)
        assertNull(success.reasoning)
    }

    // ── Markdown fences ──────────────────────────────────

    @Test
    fun `parses JSON wrapped in markdown code fences`() {
        val input = "```json\n{\"action\": \"call\", \"amount\": 50, \"reasoning\": \"good odds\"}\n```"
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.PartialSuccess)
        val partial = result as ParseResult.PartialSuccess
        assertEquals(ActionType.CALL, partial.action.type)
        assertEquals(50, partial.action.amount)
        assertTrue(partial.warnings.any { it.contains("markdown") })
    }

    @Test
    fun `parses JSON wrapped in plain code fences`() {
        val input = "```\n{\"action\": \"fold\", \"amount\": null, \"reasoning\": \"weak\"}\n```"
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.PartialSuccess)
        assertEquals(ActionType.FOLD, (result as ParseResult.PartialSuccess).action.type)
    }

    // ── JSON with preamble ───────────────────────────────

    @Test
    fun `parses JSON with preamble text`() {
        val input = "Here is my decision:\n{\"action\": \"raise\", \"amount\": 200, \"reasoning\": \"value bet\"}"
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.PartialSuccess)
        val partial = result as ParseResult.PartialSuccess
        assertEquals(ActionType.RAISE, partial.action.type)
        assertEquals(200, partial.action.amount)
        assertTrue(partial.warnings.any { it.contains("embedded") || it.contains("surrounding") })
    }

    // ── Prose / Regex fallback ───────────────────────────

    @Test
    fun `falls back to regex for prose response with fold`() {
        val input = "I think I should fold here because my hand is too weak."
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.PartialSuccess)
        val partial = result as ParseResult.PartialSuccess
        assertEquals(ActionType.FOLD, partial.action.type)
        assertTrue(partial.warnings.any { it.contains("regex") })
    }

    @Test
    fun `extracts raise amount from prose`() {
        val input = "I'll raise to 150."
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.PartialSuccess)
        val partial = result as ParseResult.PartialSuccess
        assertEquals(ActionType.RAISE, partial.action.type)
        assertEquals(150, partial.action.amount)
    }

    // ── Failures ─────────────────────────────────────────

    @Test
    fun `completely unparseable response is Failure`() {
        val input = "I'm not sure what to do in this situation."
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `empty response is Failure`() {
        val input = ""
        val result = EvalResponseParser.parse(input)

        assertTrue(result is ParseResult.Failure)
    }

    // ── Edge cases ───────────────────────────────────────

    @Test
    fun `multiple JSON objects parses first one`() {
        val input = """{"action": "fold", "amount": null} {"action": "call", "amount": 50}"""
        val result = EvalResponseParser.parse(input)

        // First JSON object should be extracted
        val action = when (result) {
            is ParseResult.Success -> result.action
            is ParseResult.PartialSuccess -> result.action
            is ParseResult.Failure -> null
        }
        assertNotNull(action)
        assertEquals(ActionType.FOLD, action.type)
    }

    @Test
    fun `wrong field names result in Failure or regex fallback`() {
        val input = """{"move": "fold", "bet": null}"""
        val result = EvalResponseParser.parse(input)

        // tryParseJson can't find "action" field, extractJsonObject finds the object
        // but it still can't parse it → falls through to regex → finds "fold" in the string
        assertTrue(result is ParseResult.PartialSuccess || result is ParseResult.Failure)
        if (result is ParseResult.PartialSuccess) {
            assertEquals(ActionType.FOLD, result.action.type)
        }
    }
}
