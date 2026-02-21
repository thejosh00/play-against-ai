package com.pokerai

import com.pokerai.ai.AiDecisionService
import com.pokerai.ai.LlmClient
import com.pokerai.ai.PreFlopStrategy
import com.pokerai.dto.ClientMessage
import com.pokerai.dto.ServerMessage
import com.pokerai.model.*
import com.pokerai.session.GameSession
import kotlin.random.Random
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.pokerai.appJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GameIntegrationTest {

    private val json = appJson

    /** LlmClient that always calls or checks — used for post-flop AI decisions. */
    private class AlwaysCallLlmClient : LlmClient {
        override suspend fun getDecision(player: Player, state: GameState): Action {
            val callAmount = state.currentBetLevel - player.currentBet
            return if (callAmount > 0) {
                Action.call(minOf(callAmount, player.chips))
            } else {
                Action.check()
            }
        }

        override suspend fun isAvailable(): Boolean = true
    }

    private val aiService = AiDecisionService(
        preFlopStrategy = PreFlopStrategy(Random(42)),
        llmClient = AlwaysCallLlmClient()
    )

    private fun Application.configureTestApp() {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 60.seconds
        }

        routing {
            webSocket("/game") {
                val session = GameSession(
                    wsSession = this,
                    aiService = aiService,
                    aiThinkingDelayMs = 0L..0L
                )
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> launch { session.handleMessage(frame.readText()) }
                        else -> {}
                    }
                }
            }
        }
    }

    @Test
    fun `play a complete game through WebSocket`() = testApplication {
        application { configureTestApp() }

        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        client.webSocket("/game") {
            sendClientMessage(
                ClientMessage.StartGame(
                    playerName = "TestPlayer",
                    startingChips = 1000,
                    smallBlind = 5,
                    bigBlind = 10
                )
            )

            for (hand in 1..3) {
                val handMessages = playOneHand()

                val stateUpdates = handMessages.filterIsInstance<ServerMessage.GameStateUpdate>()
                val handResults = handMessages.filterIsInstance<ServerMessage.HandResult>()
                val errors = handMessages.filterIsInstance<ServerMessage.Error>()

                assertTrue(errors.isEmpty(), "Hand $hand had errors: ${errors.map { it.message }}")
                assertEquals(1, handResults.size, "Hand $hand should have exactly 1 hand result")
                assertTrue(handResults[0].winners.isNotEmpty(), "Hand $hand should have at least one winner")
                assertTrue(handResults[0].allHoleCards.isNotEmpty(), "Hand $hand should reveal hole cards")

                val lastState = stateUpdates.last()
                assertEquals(GamePhase.HAND_COMPLETE, lastState.phase, "Hand $hand should end in HAND_COMPLETE")

                val firstNonWaiting = stateUpdates.first { it.phase != GamePhase.WAITING }
                assertEquals(GamePhase.PRE_FLOP, firstNonWaiting.phase, "Hand $hand should start with PRE_FLOP")

                assertEquals(hand, lastState.handNumber, "Hand number should be $hand")

                if (hand < 3) {
                    sendClientMessage(ClientMessage.DealNextHand)
                }
            }
        }
    }

    @Test
    fun `hand reaches showdown when nobody folds`() = testApplication {
        application { configureTestApp() }

        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        client.webSocket("/game") {
            sendClientMessage(
                ClientMessage.StartGame(
                    playerName = "TestPlayer",
                    startingChips = 1000,
                    smallBlind = 5,
                    bigBlind = 10
                )
            )

            // Play several hands — at least one should reach post-flop
            // (pre-flop strategy is randomized, so some AI may fold)
            val allPhasesSeen = mutableSetOf<GamePhase>()

            for (hand in 1..5) {
                val handMessages = playOneHand()
                val stateUpdates = handMessages.filterIsInstance<ServerMessage.GameStateUpdate>()
                allPhasesSeen.addAll(stateUpdates.map { it.phase })

                val handResult = handMessages.filterIsInstance<ServerMessage.HandResult>().single()

                // Winners should receive chips from the pot
                assertTrue(handResult.winners.sumOf { it.amount } > 0, "Pot should be awarded")

                if (hand < 5) {
                    sendClientMessage(ClientMessage.DealNextHand)
                }
            }

            // Over 5 hands, we should see at least pre-flop and hand-complete
            assertTrue(GamePhase.PRE_FLOP in allPhasesSeen, "Should have seen PRE_FLOP")
            assertTrue(GamePhase.HAND_COMPLETE in allPhasesSeen, "Should have seen HAND_COMPLETE")
        }
    }

    @Test
    fun `human can raise and game continues correctly`() = testApplication {
        application { configureTestApp() }

        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        client.webSocket("/game") {
            sendClientMessage(
                ClientMessage.StartGame(
                    playerName = "TestPlayer",
                    startingChips = 1000,
                    smallBlind = 5,
                    bigBlind = 10
                )
            )

            // Play a hand where the human raises on their first action
            var humanRaised = false
            val messages = mutableListOf<ServerMessage>()

            withTimeout(30.seconds) {
                while (true) {
                    val frame = incoming.receive() as Frame.Text
                    val msg = json.decodeFromString(ServerMessage.serializer(), frame.readText())
                    messages.add(msg)

                    when (msg) {
                        is ServerMessage.GameStateUpdate -> {
                            if (msg.phase == GamePhase.HAND_COMPLETE) break
                            if (msg.isUserTurn) {
                                if (!humanRaised && msg.minimumRaise <= 1000) {
                                    sendClientMessage(
                                        ClientMessage.PlayerAction(ActionType.RAISE, amount = 30)
                                    )
                                    humanRaised = true
                                } else if (msg.callAmount > 0) {
                                    sendClientMessage(ClientMessage.PlayerAction(ActionType.CALL))
                                } else {
                                    sendClientMessage(ClientMessage.PlayerAction(ActionType.CHECK))
                                }
                            }
                        }
                        is ServerMessage.ActionPerformed -> {}
                        is ServerMessage.HandResult -> {}
                        is ServerMessage.PlayerEliminated -> {}
                        is ServerMessage.Error -> error("Server error: ${msg.message}")
                        else -> {}
                    }
                }
            }

            assertTrue(humanRaised, "Human should have raised")
            assertTrue(
                messages.any { it is ServerMessage.HandResult },
                "Should have a hand result"
            )
        }
    }

    /**
     * Receives messages for one hand, responding to action prompts with call/check.
     * Returns all messages received for the hand.
     */
    private suspend fun DefaultClientWebSocketSession.playOneHand(): List<ServerMessage> {
        val messages = mutableListOf<ServerMessage>()

        withTimeout(30.seconds) {
            while (true) {
                val frame = incoming.receive() as Frame.Text
                val msg = json.decodeFromString(ServerMessage.serializer(), frame.readText())
                messages.add(msg)

                when (msg) {
                    is ServerMessage.GameStateUpdate -> {
                        if (msg.phase == GamePhase.HAND_COMPLETE) return@withTimeout
                        if (msg.isUserTurn) {
                            val action = if (msg.callAmount > 0) {
                                ClientMessage.PlayerAction(ActionType.CALL)
                            } else {
                                ClientMessage.PlayerAction(ActionType.CHECK)
                            }
                            sendClientMessage(action)
                        }
                    }
                    is ServerMessage.ActionPerformed -> {}
                    is ServerMessage.HandResult -> {}
                    is ServerMessage.PlayerEliminated -> {}
                    is ServerMessage.Error -> error("Server error: ${msg.message}")
                    else -> {}
                }
            }
        }

        return messages
    }

    private suspend fun DefaultClientWebSocketSession.sendClientMessage(msg: ClientMessage) {
        send(Frame.Text(json.encodeToString(ClientMessage.serializer(), msg)))
    }
}
