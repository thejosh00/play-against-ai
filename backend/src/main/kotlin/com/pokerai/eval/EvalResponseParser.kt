package com.pokerai.eval

import com.pokerai.model.Action
import com.pokerai.model.ActionType
import kotlinx.serialization.json.*

/**
 * Parses LLM responses for evaluation purposes.
 *
 * Unlike the production LlmResponseParser (which silently falls back to fold on failure),
 * this parser returns a detailed ParseResult that indicates exactly what went right/wrong
 * with the response format.
 */
object EvalResponseParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Parse an LLM response string into a ParseResult.
     *
     * Attempts multiple parsing strategies in order of strictness:
     * 1. Direct JSON parse (perfect format) -> Success
     * 2. Strip markdown fences then parse -> PartialSuccess with warning
     * 3. Extract JSON from surrounding text -> PartialSuccess with warning
     * 4. Regex extraction of action/amount -> PartialSuccess with warnings
     * 5. Give up -> Failure
     */
    fun parse(rawResponse: String): ParseResult {
        val trimmed = rawResponse.trim()
        if (trimmed.isEmpty()) {
            return ParseResult.Failure(rawResponse, "Empty response")
        }

        // Strategy 1: Direct JSON parse
        tryParseJson(trimmed)?.let { (action, reasoning) ->
            return ParseResult.Success(action, reasoning, rawResponse)
        }

        // Strategy 2: Strip markdown fences
        val stripped = stripMarkdownFences(trimmed)
        if (stripped != trimmed) {
            tryParseJson(stripped)?.let { (action, reasoning) ->
                return ParseResult.PartialSuccess(
                    action, reasoning, rawResponse,
                    listOf("Response wrapped in markdown code fences")
                )
            }
        }

        // Strategy 3: Extract JSON object from surrounding text
        val extracted = extractJsonObject(trimmed)
        if (extracted != null) {
            tryParseJson(extracted)?.let { (action, reasoning) ->
                return ParseResult.PartialSuccess(
                    action, reasoning, rawResponse,
                    listOf("JSON embedded in surrounding text")
                )
            }
        }

        // Strategy 4: Regex extraction
        val regexResult = tryRegexExtraction(trimmed)
        if (regexResult != null) {
            return ParseResult.PartialSuccess(
                regexResult.first, regexResult.second, rawResponse,
                listOf("Parsed via regex extraction — no valid JSON found")
            )
        }

        // Strategy 5: Give up
        return ParseResult.Failure(rawResponse, "Could not extract any valid action from response")
    }

    /**
     * Try to parse a string as the expected JSON format:
     * {"action": "fold", "amount": null, "reasoning": "..."}
     *
     * Returns (Action, reasoning) or null if parsing fails.
     */
    private fun tryParseJson(text: String): Pair<Action, String?>? {
        return try {
            val obj = json.parseToJsonElement(text).jsonObject

            val actionStr = obj["action"]?.jsonPrimitive?.content?.lowercase()
                ?: return null

            val amount = obj["amount"]?.let {
                if (it is JsonNull) null
                else it.jsonPrimitive.intOrNull
            }

            val reasoning = obj["reasoning"]?.let {
                if (it is JsonNull) null
                else it.jsonPrimitive.contentOrNull
            }

            val action = when (actionStr) {
                "fold" -> Action.fold()
                "check" -> Action.check()
                "call" -> Action.call(amount ?: 0)
                "raise" -> Action.raise(amount ?: 0)
                "all_in", "allin", "all-in" -> Action.allIn(amount ?: 0)
                else -> return null
            }

            action to reasoning
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Strip markdown code fences: ```json ... ``` or ``` ... ```
     */
    private fun stripMarkdownFences(text: String): String {
        var result = text
        result = result.replaceFirst(Regex("^```(json)?\\s*\\n?"), "")
        result = result.replaceFirst(Regex("\\n?```\\s*$"), "")
        return result.trim()
    }

    /**
     * Find the first JSON object {...} in the text.
     */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null

        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * Last resort: try to extract an action using regex patterns.
     * Looks for patterns like "I would fold" or "action: raise" or "my action is call".
     */
    private fun tryRegexExtraction(text: String): Pair<Action, String?>? {
        val lower = text.lowercase()

        val pattern = Regex("\\b(fold|check|call|raise|all[_\\-]?in)\\b")
        val match = pattern.find(lower) ?: return null
        val actionStr = match.groupValues[1].replace("-", "_").replace(" ", "_")

        val action = when (actionStr) {
            "fold" -> Action.fold()
            "check" -> Action.check()
            "call" -> Action.call(0)
            "raise" -> {
                val amountMatch = Regex("raise\\s+(?:to\\s+)?(\\d+)").find(lower)
                val amount = amountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                Action.raise(amount)
            }
            "all_in", "allin" -> Action.allIn(0)
            else -> return null
        }

        return action to null
    }
}
