package com.pokerai.model

data class Player(
    val index: Int,
    var name: String,
    val isHuman: Boolean,
    var profile: PlayerProfile?,
    var chips: Int,
    var holeCards: HoleCards? = null,
    var currentBet: Int = 0,
    var totalBetThisHand: Int = 0,
    var isFolded: Boolean = false,
    var isAllIn: Boolean = false,
    var isSittingOut: Boolean = false,
    var lastAction: Action? = null,
    var position: Position = Position.UTG
) {
    val isActive: Boolean get() = !isFolded && !isAllIn && !isSittingOut && chips > 0

    fun resetForNewHand() {
        holeCards = null
        currentBet = 0
        totalBetThisHand = 0
        isFolded = false
        isAllIn = false
        lastAction = null
        if (chips <= 0) isSittingOut = true
    }

    fun resetForNewStreet() {
        currentBet = 0
        lastAction = null
    }
}
