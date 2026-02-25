package com.pokerai.eval

import com.pokerai.model.Action
import com.pokerai.model.ActionType

/**
 * Uses a strong LLM (the "judge") to score reasoning quality.
 *
 * The judge model evaluates whether the reasoning:
 * - References specific poker factors (hand strength, pot odds, board texture, position)
 * - Sounds like the archetype's personality (nit is cautious, LAG is aggressive, etc.)
 * - Is concise and coherent
 * - Is consistent with the action taken (no contradictions)
 *
 * @param judgeAdapter an LlmAdapter pointing to a strong model (e.g., Claude Sonnet via OpenRouter)
 * @param isEnabled if false, falls back to heuristic scoring (for cost savings or offline eval)
 */
class ReasoningJudge(
    private val judgeAdapter: LlmAdapter?,
    val isEnabled: Boolean = judgeAdapter != null
) {

    /**
     * Score reasoning quality using the LLM judge.
     *
     * If the judge is disabled or the call fails, falls back to heuristic scoring.
     *
     * @return score 0-3
     */
    suspend fun scoreReasoning(
        reasoning: String?,
        scenario: EvalScenario,
        chosenAction: Action?,
        archetypeName: String
    ): Int {
        if (!isEnabled || judgeAdapter == null) {
            return Scorer.scoreReasoning(reasoning, scenario, chosenAction)
        }

        if (reasoning.isNullOrBlank() || chosenAction == null) return 0

        return try {
            val score = callJudge(reasoning, scenario, chosenAction, archetypeName)
            score.coerceIn(0, 3)
        } catch (e: Exception) {
            println("    Warning: Judge call failed: ${e.message}, using heuristic")
            Scorer.scoreReasoning(reasoning, scenario, chosenAction)
        }
    }

    private suspend fun callJudge(
        reasoning: String,
        scenario: EvalScenario,
        chosenAction: Action,
        archetypeName: String
    ): Int {
        val systemPrompt = """
            You are evaluating the reasoning quality of an AI poker player.
            You will be given:
            - The poker scenario (hand, board, pot, situation)
            - The archetype personality the AI was playing
            - The action the AI chose
            - The reasoning the AI provided

            Score the reasoning quality on a 0-3 scale:

            3 = EXCELLENT: References specific factors (hand strength, pot odds, opponent type,
                board texture, position). Sounds like the archetype personality. Is concise
                (1-2 sentences). Consistent with the action taken.

            2 = ADEQUATE: Correct reasoning but generic ("I have a good hand so I'll raise").
                Missing the archetype's voice. Or slightly verbose.

            1 = POOR: Reasoning is present but wrong, contradictory, or irrelevant.
                Example: "I have nothing so I'll call for value."
                Or: reasoning says fold but action is raise.

            0 = MISSING: No reasoning, or completely incoherent gibberish.

            Respond with ONLY a single digit: 0, 1, 2, or 3. Nothing else.
        """.trimIndent()

        val actionStr = when (chosenAction.type) {
            ActionType.FOLD -> "fold"
            ActionType.CHECK -> "check"
            ActionType.CALL -> "call"
            ActionType.RAISE -> "raise to ${chosenAction.amount}"
            ActionType.ALL_IN -> "all-in"
        }

        val userPrompt = """
            SCENARIO: ${scenario.name}
            ${scenario.description}

            HAND: ${scenario.context.hand.tier.name} - ${scenario.context.hand.madeHandDescription}
            STREET: ${scenario.context.street.name}
            POT: ${scenario.context.potSize}, bet to call: ${scenario.context.betToCall}
            POSITION: ${scenario.context.position.label}

            ARCHETYPE: $archetypeName
            Personality summary: ${getArchetypePersonalitySummary(archetypeName)}

            ACTION TAKEN: $actionStr
            REASONING: "$reasoning"

            Score (0-3):
        """.trimIndent()

        val response = judgeAdapter!!.complete(systemPrompt, userPrompt, temperature = 0.0)
        val cleaned = response.trim().take(1)

        return cleaned.toIntOrNull() ?: 1
    }

    internal fun getArchetypePersonalitySummary(name: String): String = when (name) {
        "Nit" -> "Extremely tight and cautious. Folds marginal hands. Only bets/raises with strong holdings. Avoids risk. Sounds worried and conservative."
        "Calling Station" -> "Passive, calls with almost anything. Rarely folds, rarely raises. Simple reasoning. Doesn't think about ranges or pot odds deeply."
        "Loose-Aggressive" -> "Loose and aggressive. Bets and raises frequently, including as bluffs. Confident, creative reasoning. Looks for spots to put pressure."
        "Tight-Aggressive" -> "Tight but aggressive when involved. Solid, fundamental reasoning. References pot odds, position, hand strength. Professional tone."
        "Shark" -> "Advanced, exploitative player. Sophisticated reasoning about opponent ranges, board texture, and balance. Adapts to opponent type."
        else -> "Standard poker player."
    }
}
