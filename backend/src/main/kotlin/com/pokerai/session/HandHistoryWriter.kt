package com.pokerai.session

import com.pokerai.analysis.*
import com.pokerai.model.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object HandHistoryWriter {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val fileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun writeHand(
        state: GameState,
        results: List<Triple<Int, Int, String>>,
        holeCardsMap: Map<Int, HoleCards>,
        aiReasoning: Map<Int, Pair<String?, String?>>,
        startingChips: Map<Int, Int>,
        handAnalysisByPhase: Map<GamePhase, Map<Int, HandAnalysis>>,
        boardAnalysisByPhase: Map<GamePhase, BoardAnalysis> = emptyMap(),
        tournamentPlayersRemaining: Int? = null,
        dir: File = File("hand_histories")
    ) {
        dir.mkdirs()
        val timestamp = LocalDateTime.now().format(fileFormatter)
        val file = File(dir, "hand_${state.handNumber}_$timestamp.txt")
        val sb = StringBuilder()
        sb.appendLine("============================================================")
        sb.appendLine("Hand #${state.handNumber} - ${LocalDateTime.now().format(formatter)}")
        sb.appendLine("Blinds: ${state.smallBlind}/${state.bigBlind}")
        if (tournamentPlayersRemaining != null) {
            sb.appendLine("Players remaining: $tournamentPlayersRemaining")
        }
        sb.appendLine()

        // Players
        sb.appendLine("--- Players ---")
        for (player in state.playersInHand) {
            val type = if (player.isHuman) "Human" else player.profile?.archetype?.displayName ?: "AI"
            val chips = startingChips[player.index] ?: player.chips
            sb.appendLine("Seat ${player.index}: ${player.name} (${player.position.label}) - $chips chips [$type]")
        }
        sb.appendLine()

        // Hole Cards
        sb.appendLine("--- Hole Cards ---")
        for (player in state.playersInHand) {
            val cards = holeCardsMap[player.index]
            if (cards != null) {
                sb.appendLine("${player.name}: ${cards.card1.notation} ${cards.card2.notation}")
            }
        }
        sb.appendLine()

        // Action history grouped by phase
        val phases = listOf(GamePhase.PRE_FLOP, GamePhase.FLOP, GamePhase.TURN, GamePhase.RIVER)
        val communityCards = state.communityCards

        for (phase in phases) {
            val actions = state.actionHistory.filter { it.phase == phase }
            if (actions.isEmpty()) continue

            val header = when (phase) {
                GamePhase.PRE_FLOP -> "--- Pre-Flop ---"
                GamePhase.FLOP -> "--- Flop [${communityCards.take(3).joinToString(" ") { it.notation }}] ---"
                GamePhase.TURN -> "--- Turn [${communityCards.take(4).joinToString(" ") { it.notation }}] ---"
                GamePhase.RIVER -> "--- River [${communityCards.joinToString(" ") { it.notation }}] ---"
                else -> continue
            }
            sb.appendLine(header)

            // Board threats for this street
            val boardAnalysis = boardAnalysisByPhase[phase]
            if (boardAnalysis != null) {
                sb.appendLine("  Board: ${formatBoardThreats(boardAnalysis)}")
            }

            // Hand evaluations for this street
            val analyses = handAnalysisByPhase[phase]
            if (analyses != null) {
                for ((playerIndex, analysis) in analyses) {
                    val name = state.players[playerIndex].name
                    val draws = formatDraws(analysis)
                    val drawStr = if (draws.isNotEmpty()) " | $draws" else ""
                    sb.appendLine("  $name: ${analysis.madeHandDescription} [${analysis.tier}]$drawStr")
                }
            }

            // Track whether a bet has occurred on this street (for bet vs raise display)
            var streetHasBet = phase == GamePhase.PRE_FLOP

            for ((actionIndex, record) in state.actionHistory.withIndex()) {
                if (record.phase != phase) continue
                val isBet = record.action.type == ActionType.RAISE && !streetHasBet
                if (record.action.type == ActionType.RAISE) streetHasBet = true

                val reasoning = aiReasoning[actionIndex]
                val comment = if (reasoning != null && reasoning.first != null) {
                    "  // ${reasoning.second ?: "ai"}: ${reasoning.first}"
                } else ""
                sb.appendLine("  ${record.playerName}: ${record.action.describe(isBet)}$comment")
            }
            sb.appendLine()
        }

        // Result
        sb.appendLine("--- Result ---")
        if (communityCards.isNotEmpty()) {
            sb.appendLine("Board: ${communityCards.joinToString(" ") { it.notation }}")
        }
        val totalPot = results.sumOf { it.second }
        sb.appendLine("Pot: $totalPot")
        for ((playerIndex, amount, desc) in results) {
            val name = state.players[playerIndex].name
            val handDesc = if (desc.isNotEmpty()) " with $desc" else ""
            sb.appendLine("$name wins \$$amount$handDesc")
        }
        sb.appendLine("============================================================")
        sb.appendLine()

        file.writeText(sb.toString())
    }

    private fun formatBoardThreats(board: BoardAnalysis): String {
        val flush = when {
            board.flushCompletedThisStreet -> "flush completed this street"
            board.flushPossible -> "flush possible"
            board.flushDrawPossible -> "flush draw possible"
            else -> "flush not possible"
        }

        val straight = when {
            board.straightCompletedThisStreet -> "straight completed this street"
            board.straightPossible -> "straight possible"
            board.straightDrawHeavy -> "OESD possible"
            board.connected -> "gutshot straight draw possible"
            else -> "straight not possible"
        }

        val fullHouse = if (board.fullHousePossible) "full house possible" else "full house not possible"

        val pairing = when {
            board.boardPairedThisStreet -> ", board paired this street"
            board.paired -> ", board paired"
            else -> ""
        }

        return "$flush, $straight, $fullHouse$pairing"
    }

    private fun formatDraws(analysis: HandAnalysis): String {
        if (analysis.draws.isEmpty()) return ""
        val parts = analysis.draws.map { draw ->
            val label = when (draw.type) {
                DrawType.NUT_FLUSH_DRAW -> "nut flush draw"
                DrawType.FLUSH_DRAW -> "flush draw"
                DrawType.OESD -> "OESD"
                DrawType.GUTSHOT -> "gutshot"
                DrawType.BACKDOOR_FLUSH -> "backdoor flush"
                DrawType.BACKDOOR_STRAIGHT -> "backdoor straight"
                DrawType.OVERCARDS -> "overcards"
            }
            "$label (${draw.outs} outs)"
        }
        return parts.joinToString(", ") + " — ${analysis.totalOuts} outs total"
    }
}
