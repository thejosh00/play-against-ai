package com.pokerai.session

import com.pokerai.ai.*
import com.pokerai.analysis.*
import com.pokerai.dto.*
import com.pokerai.engine.GameEngine
import com.pokerai.engine.HandEvaluator
import com.pokerai.engine.PotManager
import com.pokerai.model.*
import com.pokerai.model.archetype.PlayerArchetype
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import com.pokerai.appJson
import org.slf4j.LoggerFactory
import kotlin.random.Random

class GameSession(
    private val wsSession: DefaultWebSocketServerSession,
    private val sessionTracker: SessionTracker = SessionTracker(bigBlind = 10),
    private val opponentModeler: OpponentModeler = OpponentModeler(),
    private val aiService: AiDecisionService = AiDecisionService(
        llmClient = OllamaLlmClient(),
        sessionTracker = sessionTracker,
        opponentModeler = opponentModeler
    ),
    private val aiThinkingDelayMs: LongRange = 1000L..3000L,
    private val handHistoryEnabled: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(GameSession::class.java)
    private var state: GameState? = null
    private var config: GameConfig? = null
    private var tournamentState: TournamentState? = null
    private val playerActionChannel = Channel<ClientMessage.PlayerAction>(Channel.RENDEZVOUS)
    private val aiReasoningByActionIndex = mutableMapOf<Int, Pair<String?, String?>>()
    private var startingChips = emptyMap<Int, Int>()
    private val handAnalysisByPhase = mutableMapOf<GamePhase, Map<Int, HandAnalysis>>()
    private val boardAnalysisByPhase = mutableMapOf<GamePhase, BoardAnalysis>()

    suspend fun handleMessage(text: String) {
        try {
            when (val message = appJson.decodeFromString(ClientMessage.serializer(), text)) {
                is ClientMessage.StartGame -> startGame(message)
                is ClientMessage.PlayerAction -> playerActionChannel.send(message)
                is ClientMessage.DealNextHand -> dealNextHand()
                is ClientMessage.ToggleSetting -> handleToggle(message)
            }
        } catch (e: Exception) {
            sendMessage(ServerMessage.Error("Invalid message: ${e.message}"))
        }
    }

    private suspend fun startGame(msg: ClientMessage.StartGame) {
        config = msg.config
        val cfg = msg.config
        if (cfg != null) {
            state = GameEngine.createGame(msg.playerName, cfg)
            if (cfg is GameConfig.Tournament) {
                tournamentState = TournamentState.create(cfg)
            }
        } else {
            state = GameEngine.createGame(msg.playerName, msg.startingChips, msg.smallBlind, msg.bigBlind)
        }
        sendState()
        dealNextHand()
    }

    private suspend fun dealNextHand() {
        val s = state ?: return

        if (s.playerCount() < 2) {
            sendMessage(ServerMessage.Error("Not enough players to continue"))
            return
        }

        // Update blinds/antes from tournament state before dealing
        val ts = tournamentState
        if (ts != null) {
            val level = ts.currentBlindLevel
            s.smallBlind = level.smallBlind
            s.bigBlind = level.bigBlind
            s.ante = level.ante
        }

        GameEngine.advanceDealer(s)
        GameEngine.startNewHand(s)
        aiReasoningByActionIndex.clear()
        handAnalysisByPhase.clear()
        boardAnalysisByPhase.clear()
        startingChips = s.players.associate { it.index to (it.chips + it.currentBet) }

        sessionTracker.recordHandStart(s.players)
        opponentModeler.recordNewHand(s.players)

        sendState()

        // Run pre-flop betting
        runBettingRound()

        if (GameEngine.isHandComplete(s)) {
            finishHand()
            return
        }

        // Flop
        GameEngine.dealCommunity(s)
        recordHandAnalysis(s)
        recordBoardAnalysis(s, previousCommunityCount = 0)
        sendState()

        if (!GameEngine.allInRunout(s)) {
            runBettingRound()
            if (GameEngine.isHandComplete(s)) {
                finishHand()
                return
            }
        }

        // Turn
        GameEngine.dealCommunity(s)
        recordHandAnalysis(s)
        recordBoardAnalysis(s, previousCommunityCount = 3)
        sendState()

        if (!GameEngine.allInRunout(s)) {
            runBettingRound()
            if (GameEngine.isHandComplete(s)) {
                finishHand()
                return
            }
        }

        // River
        GameEngine.dealCommunity(s)
        recordHandAnalysis(s)
        recordBoardAnalysis(s, previousCommunityCount = 4)
        sendState()

        if (!GameEngine.allInRunout(s)) {
            runBettingRound()
            if (GameEngine.isHandComplete(s)) {
                finishHand()
                return
            }
        }

        // Showdown
        finishHand()
    }

    private suspend fun runBettingRound() {
        val s = state ?: return

        // currentPlayerIndex is already set to the first-to-act by startNewHand/dealCommunity.
        // Use it directly for the first iteration, then getNextToAct for the rest.
        var nextToAct: Int? = s.currentPlayerIndex.takeIf { s.players[it].isActive }

        while (nextToAct != null) {
            s.currentPlayerIndex = nextToAct
            val player = s.players[nextToAct]

            if (player.isHuman) {
                sendState()
                val playerAction = playerActionChannel.receive()
                val action = playerActionToAction(playerAction, player, s)
                val isBet = action.type == ActionType.RAISE && s.currentBetLevel == 0 && s.phase != GamePhase.PRE_FLOP
                opponentModeler.recordAction(nextToAct, action, s.phase)
                GameEngine.applyAction(s, nextToAct, action)
                sendActionPerformed(player, action, isBet)
            } else {
                sendState()
                logger.debug("[Hand #${s.handNumber}] Waiting for ${player.name} (${s.phase})...")
                val thinkingDelay = aiThinkingDelayMs.random()
                val start = System.currentTimeMillis()
                val decision = aiService.decide(player, s, config, tournamentState)
                val elapsed = System.currentTimeMillis() - start
                logger.debug("[Hand #${s.handNumber}] ${player.name}: ${decision.action.type} (${decision.source}, ${elapsed}ms)")
                val remaining = thinkingDelay - elapsed
                if (remaining > 0) delay(remaining)
                val isBet = decision.action.type == ActionType.RAISE && s.currentBetLevel == 0 && s.phase != GamePhase.PRE_FLOP
                opponentModeler.recordAction(nextToAct, decision.action, s.phase)
                GameEngine.applyAction(s, nextToAct, decision.action)
                aiReasoningByActionIndex[s.actionHistory.size - 1] = Pair(decision.reasoning, decision.source)
                sendActionPerformed(player, decision.action, isBet)
            }

            if (GameEngine.isHandComplete(s)) break
            nextToAct = GameEngine.getNextToAct(s)
        }
    }

    private fun playerActionToAction(msg: ClientMessage.PlayerAction, player: Player, s: GameState): Action {
        return when (msg.action) {
            ActionType.FOLD -> Action.fold()
            ActionType.CHECK -> Action.check()
            ActionType.CALL -> {
                val amount = minOf(s.currentBetLevel - player.currentBet, player.chips)
                Action.call(amount)
            }
            ActionType.RAISE -> {
                val raiseTotal = msg.amount ?: (s.currentBetLevel + s.minRaise)
                Action.raise(raiseTotal)
            }
            ActionType.ALL_IN -> Action.allIn(player.chips)
        }
    }

    private suspend fun finishHand() {
        val s = state ?: return
        val showdownOrder = GameEngine.getShowdownOrder(s)

        // Deduct rake for cash games before evaluating showdown
        val cfg = config
        if (cfg is GameConfig.CashGame && cfg.rakeEnabled) {
            PotManager.deductRake(s, 0.05, cfg.stakes.rakeCap)
        }

        val results = GameEngine.evaluateShowdown(s)

        // Record showdown for session tracking
        val shownHands = s.players
            .filter { !it.isFolded && !it.isSittingOut && it.holeCards != null }
            .associate { it.index to it.holeCards!! }
        sessionTracker.recordShowdown(s, results, shownHands)

        val winners = results.map { (idx, amount, desc) ->
            WinnerDto(idx, s.players[idx].name, amount, desc)
        }
        val winnerIndices = winners.map { it.playerIndex }.toSet()

        val isShowdown = s.activePlayers.size >= 2

        // Determine which players' cards are revealed
        s.showdownRevealedPlayers = if (isShowdown) {
            val lastAggressor = showdownOrder.firstOrNull()
            winnerIndices + setOfNotNull(lastAggressor)
        } else {
            emptySet()
        }

        // Build hole cards list in showdown order with mucking
        val allHoleCards = if (!isShowdown) {
            emptyList()
        } else {
            showdownOrder.mapNotNull { idx ->
                val player = s.players[idx]
                val holeCards = player.holeCards ?: return@mapNotNull null
                val shouldShow = idx in s.showdownRevealedPlayers || player.isHuman
                HoleCardsDto(
                    playerIndex = player.index,
                    cards = holeCards.toList().map { CardDto.from(it) },
                    mucked = !shouldShow
                )
            }
        }

        val summary = winners.joinToString("; ") { w ->
            val desc = if (w.handDescription.isNotEmpty()) " with ${w.handDescription}" else ""
            "${w.playerName} wins \$${w.amount}$desc"
        }

        sendMessage(ServerMessage.HandResult(winners, allHoleCards, summary))

        // Write hand history
        if (handHistoryEnabled) {
            val holeCardsMap = s.players
                .filter { it.holeCards != null }
                .associate { it.index to it.holeCards!! }
            HandHistoryWriter.writeHand(s, results, holeCardsMap, aiReasoningByActionIndex, startingChips, handAnalysisByPhase, boardAnalysisByPhase, tournamentState?.remainingPlayers, opponentModeler, config?.difficulty)
        }

        // Post-hand processing depends on game mode
        when (cfg) {
            is GameConfig.CashGame -> handleCashGamePostHand(s, cfg)
            is GameConfig.Tournament -> handleTournamentPostHand(s, cfg)
            null -> handleLegacyPostHand(s)
        }

        sendState()
    }

    private suspend fun handleCashGamePostHand(s: GameState, cfg: GameConfig.CashGame) {
        for (player in s.players) {
            if (player.chips <= 0) {
                val reloadChips = cfg.stakes.startingChips
                player.chips = reloadChips
                player.isSittingOut = false
                sendMessage(ServerMessage.PlayerReloaded(player.index, player.name, reloadChips))
            }
        }
    }

    private suspend fun handleTournamentPostHand(s: GameState, cfg: GameConfig.Tournament) {
        val ts = tournamentState ?: return

        // 1. Mark busted table players
        for (player in s.players) {
            if (player.chips <= 0 && !player.isSittingOut) {
                player.isSittingOut = true
                ts.remainingPlayers--
                sendMessage(ServerMessage.PlayerEliminated(player.index, player.name))

                // If human busted, tournament is over for them
                if (player.isHuman) {
                    sendMessage(ServerMessage.TournamentFinished(
                        finishPosition = ts.remainingPlayers + 1,
                        totalPlayers = ts.totalPlayers
                    ))
                    return
                }
            }
        }

        // 2. Simulate background eliminations
        val activeTableCount = s.players.count { !it.isSittingOut }
        val backgroundEliminations = simulateBackgroundEliminations(ts, activeTableCount)
        ts.remainingPlayers -= backgroundEliminations

        // 3. Replace busted AI seats if not at final table
        if (ts.remainingPlayers > cfg.tableSize) {
            val bustedAiSeats = s.players.filter { it.isSittingOut && !it.isHuman }
            val avgStack = s.players.filter { !it.isSittingOut }.map { it.chips }.average().toInt()

            for (seat in bustedAiSeats) {
                val (profile, name) = PlayerArchetype.assignRandom(1, cfg.difficulty).first()
                val variance = (avgStack * 0.2 * (Random.nextDouble() * 2 - 1)).toInt()
                seat.name = name
                seat.profile = profile
                seat.chips = (avgStack + variance).coerceAtLeast(1)
                seat.isSittingOut = false
                seat.isFolded = false
                seat.isAllIn = false
                sendMessage(ServerMessage.PlayerJoined(seat.index, seat.name, seat.chips))
            }
        }

        // 4. Advance tournament hand counter / blind level
        ts.advanceHand()

        // 5. Check if tournament is won
        if (ts.remainingPlayers <= 1) {
            sendMessage(ServerMessage.TournamentFinished(
                finishPosition = 1,
                totalPlayers = ts.totalPlayers
            ))
            return
        }

        // 6. Send tournament update
        val level = ts.currentBlindLevel
        sendMessage(ServerMessage.TournamentUpdate(
            remainingPlayers = ts.remainingPlayers,
            totalPlayers = ts.totalPlayers,
            blindLevel = level.level,
            smallBlind = level.smallBlind,
            bigBlind = level.bigBlind,
            ante = level.ante,
            handsUntilNextLevel = ts.handsUntilNextLevel
        ))
    }

    private fun simulateBackgroundEliminations(ts: TournamentState, activeTableCount: Int): Int {
        val backgroundPlayers = ts.remainingPlayers - activeTableCount
        if (backgroundPlayers <= 0) return 0

        val totalLevels = ts.blindStructure.size.toDouble()
        val eliminationRate = 0.01 + 0.04 * (ts.currentBlindLevelIndex / totalLevels)

        var eliminations = 0
        for (i in 0 until backgroundPlayers) {
            if (Random.nextDouble() < eliminationRate) {
                eliminations++
            }
        }
        return eliminations.coerceAtMost(backgroundPlayers)
    }

    private suspend fun handleLegacyPostHand(s: GameState) {
        // Original elimination logic
        for (player in s.players) {
            if (player.chips <= 0 && !player.isSittingOut) {
                player.isSittingOut = true
                sendMessage(ServerMessage.PlayerEliminated(player.index, player.name))
            }
        }
    }

    private suspend fun handleToggle(msg: ClientMessage.ToggleSetting) {
        val s = state ?: return
        when (msg.setting) {
            "showAiCards" -> s.showAiCards = msg.value
            "showPlayerTypes" -> s.showPlayerTypes = msg.value
            "showStats" -> s.showStats = msg.value
        }
        sendState()
    }

    private fun buildGameLabel(): String? {
        return when (val cfg = config) {
            is GameConfig.CashGame -> {
                val rakeStr = if (cfg.rakeEnabled) " - Rake: 5%/\$${cfg.stakes.rakeCap} cap" else ""
                "${cfg.stakes.label} Cash$rakeStr"
            }
            is GameConfig.Tournament -> {
                val ts = tournamentState ?: return null
                "${cfg.buyin.label} Tournament - ${ts.totalPlayers} players"
            }
            null -> null
        }
    }

    private fun recordBoardAnalysis(s: GameState, previousCommunityCount: Int) {
        if (s.communityCards.size >= 3) {
            boardAnalysisByPhase[s.phase] = BoardAnalyzer.analyze(s.communityCards, previousCommunityCount)
        }
    }

    private fun recordHandAnalysis(s: GameState) {
        val analyses = s.activePlayers
            .filter { it.holeCards != null }
            .associate { it.index to HandStrengthClassifier.analyze(it.holeCards!!, s.communityCards) }
        handAnalysisByPhase[s.phase] = analyses
    }

    private suspend fun sendState() {
        val s = state ?: return
        sendMessage(s.toUpdate(gameLabel = buildGameLabel(), opponentModeler = opponentModeler))
    }

    private suspend fun sendActionPerformed(player: Player, action: Action, isBet: Boolean = false) {
        val s = state ?: return
        sendMessage(
            ServerMessage.ActionPerformed(
                playerIndex = player.index,
                playerName = player.name,
                action = action.describe(isBet),
                phase = s.phase
            )
        )
    }

    private suspend fun sendMessage(msg: ServerMessage) {
        try {
            val text = appJson.encodeToString(ServerMessage.serializer(), msg)
            wsSession.send(Frame.Text(text))
        } catch (e: Exception) {
            logger.warn("Failed to send WebSocket message (${msg::class.simpleName}): ${e.message}")
        }
    }
}
