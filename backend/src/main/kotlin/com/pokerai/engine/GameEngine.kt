package com.pokerai.engine

import com.pokerai.model.*
import com.pokerai.model.archetype.PlayerArchetype
import kotlin.math.min

object GameEngine {

    fun createGame(
        playerName: String,
        startingChips: Int,
        smallBlind: Int,
        bigBlind: Int
    ): GameState {
        val aiAssignments = PlayerArchetype.assignRandom(5)
        val players = mutableListOf(
            Player(
                index = 0,
                name = playerName,
                isHuman = true,
                profile = null,
                chips = startingChips
            )
        )
        aiAssignments.forEachIndexed { i, (profile, name) ->
            players.add(
                Player(
                    index = i + 1,
                    name = name,
                    isHuman = false,
                    profile = profile,
                    chips = startingChips
                )
            )
        }
        return GameState(
            players = players,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            dealerIndex = (0 until players.size).random()
        )
    }

    fun createGame(playerName: String, config: GameConfig): GameState {
        val startingChips: Int
        val smallBlind: Int
        val bigBlind: Int

        when (config) {
            is GameConfig.CashGame -> {
                startingChips = config.stakes.startingChips
                smallBlind = config.stakes.smallBlind
                bigBlind = config.stakes.bigBlind
            }
            is GameConfig.Tournament -> {
                startingChips = config.startingBBs * 50
                smallBlind = 25
                bigBlind = 50
            }
        }

        val aiCount = config.tableSize - 1
        val aiAssignments = PlayerArchetype.assignRandom(aiCount, config.difficulty)
        val players = mutableListOf(
            Player(
                index = 0,
                name = playerName,
                isHuman = true,
                profile = null,
                chips = startingChips
            )
        )
        aiAssignments.forEachIndexed { i, (profile, name) ->
            players.add(
                Player(
                    index = i + 1,
                    name = name,
                    isHuman = false,
                    profile = profile,
                    chips = startingChips
                )
            )
        }
        return GameState(
            players = players,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            dealerIndex = (0 until players.size).random()
        )
    }

    fun startNewHand(state: GameState): GameState {
        state.handNumber++

        // Reset players
        for (player in state.players) {
            player.resetForNewHand()
        }

        // Reset state
        state.communityCards.clear()
        state.pot = 0
        state.sidePots.clear()
        state.currentBetLevel = 0
        state.minRaise = state.bigBlind
        state.lastRaiserIndex = -1
        state.actionHistory.clear()

        // Shuffle deck
        state.deck.reset()
        state.deck.shuffle()

        // Assign positions
        assignPositions(state)

        // Deal hole cards
        for (player in state.playersInHand) {
            val c1 = state.deck.deal()
            val c2 = state.deck.deal()
            player.holeCards = HoleCards(c1, c2)
        }

        // Post antes
        if (state.ante > 0) {
            postAntes(state)
        }

        // Post blinds
        postBlinds(state)

        state.phase = GamePhase.PRE_FLOP

        // Set first to act (left of BB for pre-flop)
        state.currentPlayerIndex = getFirstToActPreFlop(state)

        return state
    }

    private fun assignPositions(state: GameState) {
        val active = state.playersInHand
        val total = active.size

        for (player in active) {
            player.position = Position.forSeat(player.index, state.dealerIndex, total)
        }
    }

    private fun postAntes(state: GameState) {
        for (player in state.playersInHand) {
            val anteAmount = min(state.ante, player.chips)
            player.chips -= anteAmount
            player.totalBetThisHand += anteAmount
            state.pot += anteAmount
            if (player.chips == 0) player.isAllIn = true
        }
    }

    private fun postBlinds(state: GameState) {
        val active = state.playersInHand.sortedBy {
            (it.index - state.dealerIndex - 1 + state.players.size) % state.players.size
        }

        if (active.size >= 2) {
            val sb = active[0]
            val sbAmount = minOf(state.smallBlind, sb.chips)
            sb.chips -= sbAmount
            sb.currentBet = sbAmount
            sb.totalBetThisHand = sbAmount
            state.pot += sbAmount

            val bb = active[1]
            val bbAmount = minOf(state.bigBlind, bb.chips)
            bb.chips -= bbAmount
            bb.currentBet = bbAmount
            bb.totalBetThisHand = bbAmount
            state.pot += bbAmount

            state.currentBetLevel = bbAmount
            state.minRaise = state.bigBlind
        }
    }

    private fun getFirstToActPreFlop(state: GameState): Int {
        val active = state.playersInHand.sortedBy {
            (it.index - state.dealerIndex - 1 + state.players.size) % state.players.size
        }
        // First to act pre-flop is position after BB (index 2 in sorted order)
        return if (active.size > 2) {
            active[2].index
        } else {
            // Heads-up: SB acts first pre-flop
            active[0].index
        }
    }

    fun getFirstToActPostFlop(state: GameState): Int {
        val active = state.playersInHand.filter { !it.isFolded }
            .sortedBy {
                (it.index - state.dealerIndex - 1 + state.players.size) % state.players.size
            }
        // Find first non-all-in player
        return active.firstOrNull { it.isActive }?.index ?: active.first().index
    }

    fun applyAction(state: GameState, playerIndex: Int, action: Action): GameState {
        val player = state.players[playerIndex]

        when (action.type) {
            ActionType.FOLD -> {
                player.isFolded = true
                player.lastAction = action
            }

            ActionType.CHECK -> {
                player.lastAction = action
            }

            ActionType.CALL -> {
                val callAmount = minOf(state.currentBetLevel - player.currentBet, player.chips)
                player.chips -= callAmount
                player.currentBet += callAmount
                player.totalBetThisHand += callAmount
                state.pot += callAmount
                if (player.chips == 0) player.isAllIn = true
                player.lastAction = Action.call(callAmount)
            }

            ActionType.RAISE -> {
                val totalBet = action.amount
                val additional = totalBet - player.currentBet
                val actualAdditional = minOf(additional, player.chips)
                player.chips -= actualAdditional
                player.currentBet += actualAdditional
                player.totalBetThisHand += actualAdditional
                state.pot += actualAdditional
                state.minRaise = (player.currentBet - state.currentBetLevel).coerceAtLeast(state.bigBlind)
                state.currentBetLevel = player.currentBet
                state.lastRaiserIndex = playerIndex
                if (player.chips == 0) player.isAllIn = true
                player.lastAction = Action.raise(player.currentBet)
            }

            ActionType.ALL_IN -> {
                val allInAmount = player.chips
                player.chips = 0
                player.currentBet += allInAmount
                player.totalBetThisHand += allInAmount
                state.pot += allInAmount
                player.isAllIn = true
                if (player.currentBet > state.currentBetLevel) {
                    state.minRaise = (player.currentBet - state.currentBetLevel).coerceAtLeast(state.bigBlind)
                    state.currentBetLevel = player.currentBet
                    state.lastRaiserIndex = playerIndex
                }
                player.lastAction = Action.allIn(allInAmount)
            }
        }

        state.actionHistory.add(
            ActionRecord(playerIndex, player.name, action, state.phase)
        )

        return state
    }

    fun getNextToAct(state: GameState): Int? {
        val playersInHand = state.playersInHand.filter { !it.isFolded }

        // Only one player left -> hand is over
        if (playersInHand.size <= 1) return null

        // No one can bet (all are all-in or only one is active)
        if (state.bettingPlayers.isEmpty()) return null

        val totalPlayers = state.players.size
        var nextIndex = (state.currentPlayerIndex + 1) % totalPlayers

        // Walk around the table looking for someone who needs to act
        for (i in 0 until totalPlayers) {
            val player = state.players[nextIndex]
            if (!player.isFolded && !player.isAllIn && !player.isSittingOut && player.chips > 0) {
                // Check if this player has already acted and the round is complete
                if (hasEveryoneActed(state, nextIndex)) return null
                return nextIndex
            }
            nextIndex = (nextIndex + 1) % totalPlayers
        }
        return null
    }

    private fun hasEveryoneActed(state: GameState, candidateIndex: Int): Boolean {
        // If there was a raise and we've come back to the raiser, round is done
        if (state.lastRaiserIndex == candidateIndex) return true

        // If no raise happened, check if all active players have acted
        // (everyone's bet matches currentBetLevel, or they have an action recorded this street)
        val activePlayers = state.players.filter { !it.isFolded && !it.isAllIn && !it.isSittingOut }
        if (activePlayers.all { it.currentBet == state.currentBetLevel }) {
            // Check that the candidate has also already acted this street
            val actionsThisStreet = state.actionHistory.filter { it.phase == state.phase }
            val candidateActed = actionsThisStreet.any { it.playerIndex == candidateIndex }
            return candidateActed
        }
        return false
    }

    fun getValidActions(state: GameState, playerIndex: Int): List<ActionType> {
        val player = state.players[playerIndex]
        val actions = mutableListOf<ActionType>()

        if (state.currentBetLevel > player.currentBet) {
            actions.add(ActionType.FOLD)
            actions.add(ActionType.CALL)
            // Can raise if chips allow
            val callAmount = state.currentBetLevel - player.currentBet
            if (player.chips > callAmount) {
                actions.add(ActionType.RAISE)
            }
        } else {
            actions.add(ActionType.CHECK)
            if (player.chips > 0) {
                actions.add(ActionType.RAISE)
            }
        }
        actions.add(ActionType.ALL_IN)

        return actions
    }

    fun getCallAmount(state: GameState, playerIndex: Int): Int {
        val player = state.players[playerIndex]
        return minOf(state.currentBetLevel - player.currentBet, player.chips)
    }

    fun getMinRaiseTotal(state: GameState): Int {
        return state.currentBetLevel + state.minRaise
    }

    fun dealCommunity(state: GameState): GameState {
        when (state.phase) {
            GamePhase.PRE_FLOP -> {
                // Deal flop: burn 1, deal 3
                state.deck.deal() // burn
                state.communityCards.addAll(state.deck.deal(3))
                state.phase = GamePhase.FLOP
            }

            GamePhase.FLOP -> {
                // Deal turn: burn 1, deal 1
                state.deck.deal() // burn
                state.communityCards.add(state.deck.deal())
                state.phase = GamePhase.TURN
            }

            GamePhase.TURN -> {
                // Deal river: burn 1, deal 1
                state.deck.deal() // burn
                state.communityCards.add(state.deck.deal())
                state.phase = GamePhase.RIVER
            }

            else -> {}
        }

        // Reset for new street
        for (player in state.players) {
            player.resetForNewStreet()
        }
        state.currentBetLevel = 0
        state.minRaise = state.bigBlind
        state.lastRaiserIndex = -1
        state.currentPlayerIndex = getFirstToActPostFlop(state)

        return state
    }

    fun evaluateShowdown(state: GameState): List<Triple<Int, Int, String>> {
        state.phase = GamePhase.SHOWDOWN

        val activePlayers = state.players.filter { !it.isFolded && !it.isSittingOut }

        if (activePlayers.size == 1) {
            val winner = activePlayers[0]
            winner.chips += state.pot
            val amount = state.pot
            state.pot = 0
            state.phase = GamePhase.HAND_COMPLETE
            return listOf(Triple(winner.index, amount, ""))
        }

        val evaluations = mutableMapOf<Int, HandEvaluation>()
        for (player in activePlayers) {
            val allCards = player.holeCards!!.toList() + state.communityCards
            evaluations[player.index] = HandEvaluator.evaluateBest(allCards)
        }

        val results = PotManager.awardPot(state, evaluations)
        state.phase = GamePhase.HAND_COMPLETE
        return results
    }

    fun isHandComplete(state: GameState): Boolean {
        val notFolded = state.players.count { !it.isFolded && !it.isSittingOut }
        return notFolded <= 1
    }

    fun allInRunout(state: GameState): Boolean {
        val notFolded = state.players.filter { !it.isFolded && !it.isSittingOut }
        val canBet = notFolded.count { !it.isAllIn && it.chips > 0 }
        return notFolded.size > 1 && canBet <= 1
    }

    /**
     * Returns player indices in proper showdown show order:
     * - Last aggressor on the final street shows first
     * - If no aggression, first to act (earliest position post-dealer) shows first
     * - Remaining players in clockwise order from the first shower
     */
    fun getShowdownOrder(state: GameState): List<Int> {
        val showdownPlayers = state.players.filter { !it.isFolded && !it.isSittingOut }
        if (showdownPlayers.size <= 1) return showdownPlayers.map { it.index }

        val totalPlayers = state.players.size

        // Determine who shows first: last aggressor on final street, or first to act
        val finalStreetActions = state.actionHistory.filter { it.phase == state.phase }
        val lastAggressor = finalStreetActions.lastOrNull {
            it.action.type == ActionType.RAISE || it.action.type == ActionType.ALL_IN
        }?.playerIndex

        val firstShower = if (lastAggressor != null && showdownPlayers.any { it.index == lastAggressor }) {
            lastAggressor
        } else {
            // First to act post-flop: earliest position after dealer
            showdownPlayers.sortedBy {
                (it.index - state.dealerIndex - 1 + totalPlayers) % totalPlayers
            }.first().index
        }

        // Order clockwise from first shower
        val ordered = mutableListOf(firstShower)
        var next = (firstShower + 1) % totalPlayers
        while (ordered.size < showdownPlayers.size) {
            if (showdownPlayers.any { it.index == next }) {
                ordered.add(next)
            }
            next = (next + 1) % totalPlayers
        }
        return ordered
    }

    fun advanceDealer(state: GameState): GameState {
        val total = state.players.size
        var next = (state.dealerIndex + 1) % total
        while (state.players[next].isSittingOut) {
            next = (next + 1) % total
        }
        state.dealerIndex = next
        return state
    }
}
