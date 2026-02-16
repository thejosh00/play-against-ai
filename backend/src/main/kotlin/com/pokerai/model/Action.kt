package com.pokerai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ActionType {
    @SerialName("fold") FOLD,
    @SerialName("check") CHECK,
    @SerialName("call") CALL,
    @SerialName("raise") RAISE,
    @SerialName("all_in") ALL_IN
}

@Serializable
data class Action(
    val type: ActionType,
    val amount: Int = 0
) {
    companion object {
        fun fold() = Action(ActionType.FOLD)
        fun check() = Action(ActionType.CHECK)
        fun call(amount: Int) = Action(ActionType.CALL, amount)
        fun raise(amount: Int) = Action(ActionType.RAISE, amount)
        fun allIn(amount: Int) = Action(ActionType.ALL_IN, amount)
    }

    fun describe(): String = when (type) {
        ActionType.FOLD -> "Fold"
        ActionType.CHECK -> "Check"
        ActionType.CALL -> "Call \$$amount"
        ActionType.RAISE -> "Raise to \$$amount"
        ActionType.ALL_IN -> "All-In \$$amount"
    }
}
