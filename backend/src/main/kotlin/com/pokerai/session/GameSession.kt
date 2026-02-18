package com.pokerai.session

import com.pokerai.ai.AiDecisionService
import com.pokerai.ai.OllamaLlmClient
import com.pokerai.dto.*
import com.pokerai.engine.GameEngine
import com.pokerai.engine.PotManager
import com.pokerai.model.*
import com.pokerai.model.archetype.PlayerArchetype
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import com.pokerai.appJson
import kotlin.random.Random

class GameSession(
    private val wsSession: DefaultWebSocketServerSession,
    private val aiService: AiDecisionService = AiDecisionService(llmClient = OllamaLlmClient()),
    private val aiThinkingDelayMs: LongRange = 1000L..2000L
) {
    private var state: GameState? = null
    private var config: GameConfig? = null
    private var tournamentState: TournamentState? = null
    private val playerActionChannel = Channel<ClientMessage.PlayerAction>(Channel.RENDEZVOUS)

    suspend fun handleMessage(text: String) {
        try {
            val message = appJson.decodeFromString(ClientMessage.serializer(), text)
            when (message) {
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
        sendState()

        // Run pre-flop betting
        runBettingRound()

        if (GameEngine.isHandComplete(s)) {
            finishHand()
            return
        }

        // Flop
        GameEngine.dealCommunity(s)
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
                GameEngine.applyAction(s, nextToAct, action)
                sendActionPerformed(player, action)
            } else {
                sendState()
                // AI thinking delay
                delay(aiThinkingDelayMs.random())
                val action = aiService.decide(player, s, config, tournamentState)
                GameEngine.applyAction(s, nextToAct, action)
                sendActionPerformed(player, action)
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

        val winners = results.map { (idx, amount, desc) ->
            WinnerDto(idx, s.players[idx].name, amount, desc)
        }
        val winnerIndices = winners.map { it.playerIndex }.toSet()

        // Build hole cards list in showdown order with mucking
        val allHoleCards = showdownOrder.mapNotNull { idx ->
            val player = s.players[idx]
            val holeCards = player.holeCards ?: return@mapNotNull null
            // Show cards if: player is a winner, player is the last aggressor, or player is human
            val isWinner = idx in winnerIndices
            val isLastAggressor = showdownOrder.firstOrNull() == idx
            val shouldShow = isWinner || isLastAggressor || player.isHuman
            HoleCardsDto(
                playerIndex = player.index,
                cards = holeCards.toList().map { CardDto.from(it) },
                mucked = !shouldShow
            )
        }

        val summary = winners.joinToString("; ") { w ->
            val desc = if (w.handDescription.isNotEmpty()) " with ${w.handDescription}" else ""
            "${w.playerName} wins \$${w.amount}$desc"
        }

        sendMessage(ServerMessage.HandResult(winners, allHoleCards, summary))

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

    private suspend fun sendState() {
        val s = state ?: return
        sendMessage(s.toUpdate(gameLabel = buildGameLabel()))
    }

    private suspend fun sendActionPerformed(player: Player, action: Action) {
        val s = state ?: return
        sendMessage(
            ServerMessage.ActionPerformed(
                playerIndex = player.index,
                playerName = player.name,
                action = action.describe(),
                phase = s.phase
            )
        )
    }

    private suspend fun sendMessage(msg: ServerMessage) {
        val text = appJson.encodeToString(ServerMessage.serializer(), msg)
        wsSession.send(Frame.Text(text))
    }
}
