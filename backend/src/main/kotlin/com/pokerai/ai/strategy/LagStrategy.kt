package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.BoardWetness
import com.pokerai.analysis.HandStrengthTier
import com.pokerai.analysis.PotType
import com.pokerai.model.Action
import com.pokerai.model.Position

/**
 * Coded postflop strategy for the LAG (Loose Aggressive) archetype.
 *
 * The LAG is aggressive, positionally aware, and opponent-adaptive:
 * - Bets and raises as the default action (not check/call)
 * - C-bets 70-85% of the time as the preflop raiser
 * - Continues barreling on the turn and river with a mix of value and bluffs
 * - Uses board texture to determine sizing and bluff frequency
 * - Adjusts aggression based on opponent type
 * - Bluffs more against tight/passive opponents, less against loose/passive ones
 * - Plays fast with strong hands (bet/raise for value, not slowplay)
 *
 * Instinct (1-100):
 * - Low instinct (1-30): more cautious — still aggressive, but picks better spots
 * - Medium instinct (31-65): standard LAG aggression
 * - High instinct (66-85): maximum pressure — extra barrels, bigger sizing
 * - Very high instinct (86-100): reckless — overbluffs, hero raises, spews
 */
class LagStrategy : ArchetypeStrategy {

    override fun decide(ctx: DecisionContext): ActionDecision {
        val effectiveInstinct = adjustInstinct(ctx)

        return when (ctx.street) {
            Street.PREFLOP -> error("ArchetypeStrategy should not be called preflop — use PreFlopStrategy")
            Street.FLOP -> decideFlopLag(ctx, effectiveInstinct)
            Street.TURN -> decideTurnLag(ctx, effectiveInstinct)
            Street.RIVER -> decideRiverLag(ctx, effectiveInstinct)
        }
    }

    // ── Instinct adjustment ──────────────────────────────────────────

    private fun adjustInstinct(ctx: DecisionContext): Int {
        var instinct = ctx.instinct

        // Position matters a lot to the LAG. In position = more aggressive.
        if (ctx.position in listOf(Position.BTN, Position.CO)) {
            instinct += 8
        } else if (ctx.position in listOf(Position.SB, Position.UTG, Position.UTG1)) {
            instinct -= 5
        }

        // Opponent type is THE key adjustment for the LAG.
        ctx.bettorRead?.let { bettor ->
            when (bettor.playerType) {
                OpponentType.TIGHT_PASSIVE -> instinct += 12
                OpponentType.TIGHT_AGGRESSIVE -> instinct -= 8
                OpponentType.LOOSE_PASSIVE -> instinct -= 15
                OpponentType.LOOSE_AGGRESSIVE -> instinct -= 10
                OpponentType.UNKNOWN -> {}
            }
        }

        // When checked to (not facing a bet), check who we're up against
        if (!ctx.facingBet && ctx.opponents.isNotEmpty()) {
            val primaryOpponent = ctx.opponents.firstOrNull()
            primaryOpponent?.let { opp ->
                when (opp.playerType) {
                    OpponentType.TIGHT_PASSIVE -> instinct += 10
                    OpponentType.LOOSE_PASSIVE -> instinct -= 10
                    OpponentType.TIGHT_AGGRESSIVE -> instinct -= 5
                    OpponentType.LOOSE_AGGRESSIVE -> instinct -= 5
                    OpponentType.UNKNOWN -> {}
                }
            }
        }

        // Session momentum: LAG gets MORE aggressive when winning
        ctx.sessionStats?.let { session ->
            when {
                session.resultBB > 30.0 -> instinct += 5
                session.resultBB < -60.0 -> instinct -= 10
                session.resultBB < -30.0 -> instinct -= 5
            }
        }

        // Recent showdown results
        ctx.sessionStats?.recentShowdowns?.let { showdowns ->
            val recent = showdowns.firstOrNull { it.handsAgo <= 5 }
            if (recent != null) {
                when (recent.event) {
                    ShowdownEvent.GOT_BLUFFED -> instinct += 5
                    ShowdownEvent.CALLED_AND_LOST -> instinct -= 8
                    ShowdownEvent.CALLED_AND_WON -> instinct += 3
                    ShowdownEvent.SAW_OPPONENT_BLUFF -> {}
                    ShowdownEvent.SAW_BIG_POT_LOSS -> instinct -= 3
                }
            }
        }

        // SPR affects LAG strategy: low SPR → more willing to get it in
        if (ctx.spr < 3.0) {
            instinct += 5
        }

        // Follow through on prior-street aggression when not facing a bet
        if (ctx.wasAggressorThisHand && !ctx.facingBet) {
            instinct += 25
        }

        return instinct.coerceIn(1, 100)
    }

    // ── Board texture helpers ────────────────────────────────────────

    private fun isGoodCbetBoard(ctx: DecisionContext): Boolean {
        val board = ctx.board
        return when {
            board.highCard.value >= 12 && board.wetness <= BoardWetness.SEMI_WET -> true
            board.wetness == BoardWetness.DRY -> true
            board.wetness == BoardWetness.VERY_WET -> false
            board.paired -> true
            else -> board.wetness <= BoardWetness.WET
        }
    }

    private fun isScareTurnOrRiver(ctx: DecisionContext): Boolean {
        return ctx.board.flushCompletedThisStreet
            || ctx.board.straightCompletedThisStreet
            || ctx.board.highCard.value >= 12
    }

    private fun canRepresentStrength(ctx: DecisionContext): Boolean {
        return when {
            ctx.isInitiator -> true
            ctx.board.flushCompletedThisStreet && ctx.hand.hasNutAdvantage -> true
            ctx.board.straightCompletedThisStreet -> true
            ctx.board.boardPairedThisStreet -> true
            else -> false
        }
    }

    private fun shouldBluff(instinct: Int, ctx: DecisionContext, baseThreshold: Int): Boolean {
        val threshold = when (ctx.potType) {
            PotType.HEADS_UP -> baseThreshold
            PotType.THREE_WAY -> baseThreshold + 15
            PotType.MULTIWAY -> baseThreshold + 30
        }
        return instinct > threshold && canRepresentStrength(ctx)
    }

    // ── Sizing strategy ──────────────────────────────────────────────

    private fun chooseSizing(ctx: DecisionContext, isBluff: Boolean): Double {
        val p = ctx.profile
        val board = ctx.board
        return when {
            board.wetness <= BoardWetness.DRY -> p.betSizePotFraction * 0.55
            board.wetness == BoardWetness.SEMI_WET -> p.betSizePotFraction * 0.85
            else -> p.betSizePotFraction * 1.1
        }
    }

    private fun chooseLagRaiseSizing(ctx: DecisionContext): Double {
        return if (ctx.betAsFractionOfPot <= 0.5) {
            ctx.profile.raiseMultiplier * 1.1
        } else {
            ctx.profile.raiseMultiplier
        }
    }

    // ── Street handlers ──────────────────────────────────────────────

    private fun decideFlopLag(
        ctx: DecisionContext,
        instinct: Int
    ): ActionDecision {
        val tier = ctx.hand.tier

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.85, "re-raising with monster — let's go")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.hand.totalOuts >= 8 && instinct > 70)
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.4, "re-raising strong hand + draw")
                    else callAction(ctx, 0.65, "calling the raise with a strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.6)
                        callAction(ctx, 0.5, "floating the raise — seeing what happens on the turn")
                    else foldAction(0.55, "raise is too big with a medium hand")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8)
                        callAction(ctx, 0.4, "calling raise with a draw")
                    else foldAction(0.7, "folding weak hand to raise")
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 85
                        && ctx.potType == PotType.HEADS_UP
                        && ctx.board.wetness <= BoardWetness.DRY
                    ) {
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.15, "re-bluff raise — pure aggression")
                    } else {
                        foldAction(0.75, "folding nothing to a raise")
                    }
                }
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 35)
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.8, "raising with monster — building the pot")
                    else callAction(ctx, 0.7, "flatting the monster — deception")
                }
                HandStrengthTier.STRONG -> {
                    if (instinct > 55)
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.55, "raising strong hand for value")
                    else callAction(ctx, 0.7, "calling with a strong hand — play the turn")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.hand.totalOuts >= 5 && instinct > 65 && ctx.potType == PotType.HEADS_UP) {
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.35, "raising medium + draw as semi-bluff")
                    } else {
                        callAction(ctx, 0.6, "calling with medium hand — floating")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 5) {
                        callAction(ctx, 0.5, "calling with a draw")
                    } else if (instinct > 60
                        && ctx.potType == PotType.HEADS_UP
                        && ctx.position in listOf(Position.BTN, Position.CO)
                    ) {
                        callAction(ctx, 0.3, "floating in position — planning to take it away on the turn")
                    } else {
                        foldAction(0.6, "folding weak hand")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 75
                        && ctx.potType == PotType.HEADS_UP
                        && ctx.board.wetness <= BoardWetness.SEMI_WET
                    ) {
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.2, "bluff raise on dry board")
                    } else if (instinct > 60
                        && ctx.potType == PotType.HEADS_UP
                        && ctx.position in listOf(Position.BTN, Position.CO)
                    ) {
                        callAction(ctx, 0.2, "floating with nothing in position — will bluff turn")
                    } else {
                        foldAction(0.7, "folding nothing to a bet")
                    }
                }
            }
        }

        // ── CHECKED TO (C-BET DECISION) ──────────────────────────
        if (ctx.isInitiator) {
            val goodBoard = isGoodCbetBoard(ctx)
            val sizing = chooseSizing(ctx, tier >= HandStrengthTier.MEDIUM)

            return when (tier) {
                HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                    betAction(ctx, sizing, 0.85, "c-betting for value")
                }
                HandStrengthTier.MEDIUM -> {
                    if (goodBoard || instinct > 45) {
                        betAction(ctx, sizing, 0.65, "c-betting medium hand")
                    } else {
                        checkAction(0.5, "checking medium hand on bad board — pot control")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 4) {
                        betAction(ctx, sizing, 0.6, "c-betting with a draw")
                    } else if (goodBoard && instinct > 35) {
                        betAction(ctx, sizing, 0.45, "c-betting air on a good board")
                    } else {
                        checkAction(0.55, "checking weak hand — board is bad for bluffing")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (goodBoard && shouldBluff(instinct, ctx, 30)) {
                        betAction(ctx, sizing, 0.4, "bluff c-bet on a good board")
                    } else {
                        checkAction(0.6, "checking air — bad board for bluffing")
                    }
                }
            }
        }

        // NOT the initiator — take stabs in position.
        return when (tier) {
            HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                val sizing = chooseSizing(ctx, false)
                betAction(ctx, sizing, 0.7, "betting for value after opponent checked")
            }
            HandStrengthTier.MEDIUM -> {
                if (ctx.board.wetness >= BoardWetness.WET || instinct > 55) {
                    val sizing = chooseSizing(ctx, false) * 0.8
                    betAction(ctx, sizing, 0.5, "betting medium hand for protection")
                } else {
                    checkAction(0.55, "checking medium hand on dry board — pot control")
                }
            }
            HandStrengthTier.WEAK -> {
                if (ctx.hand.totalOuts >= 5 && instinct > 40) {
                    betAction(ctx, chooseSizing(ctx, true), 0.4, "betting a draw")
                } else {
                    checkAction(0.6, "checking weak hand")
                }
            }
            HandStrengthTier.NOTHING -> {
                if (ctx.potType == PotType.HEADS_UP
                    && ctx.position in listOf(Position.BTN, Position.CO)
                    && instinct > 50
                    && ctx.board.wetness <= BoardWetness.SEMI_WET
                ) {
                    betAction(ctx, chooseSizing(ctx, true), 0.3, "position stab with nothing")
                } else {
                    checkAction(0.65, "checking nothing")
                }
            }
        }
    }

    private fun decideTurnLag(
        ctx: DecisionContext,
        instinct: Int
    ): ActionDecision {
        val tier = ctx.hand.tier
        val scareCard = isScareTurnOrRiver(ctx)

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER ->
                    raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.8, "re-raising turn with monster")
                HandStrengthTier.STRONG ->
                    callAction(ctx, 0.6, "calling turn raise with strong hand")
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.5 && instinct > 55)
                        callAction(ctx, 0.35, "peeling turn raise with medium hand")
                    else foldAction(0.6, "folding medium hand to turn raise")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 10 && instinct > 65)
                        callAction(ctx, 0.25, "calling turn raise with big draw")
                    else foldAction(0.7, "folding to turn raise")
                }
                HandStrengthTier.NOTHING -> foldAction(0.8, "folding nothing to turn raise")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 40)
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.75, "raising turn for value with monster")
                    else callAction(ctx, 0.8, "flatting monster — set up river shove")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.board.wetness >= BoardWetness.WET && instinct > 60)
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.45, "raising turn to deny equity")
                    else callAction(ctx, 0.65, "calling turn with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.6)
                        callAction(ctx, 0.5, "calling turn with medium hand")
                    else if (ctx.hand.totalOuts >= 5)
                        callAction(ctx, 0.35, "calling turn — have outs")
                    else foldAction(0.55, "folding medium hand to big turn bet")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8)
                        callAction(ctx, 0.4, "calling turn with a draw")
                    else if (instinct > 70 && ctx.potType == PotType.HEADS_UP && scareCard) {
                        callAction(ctx, 0.2, "floating turn scare card — bluffing river")
                    } else {
                        foldAction(0.65, "folding weak hand on turn")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 80
                        && ctx.potType == PotType.HEADS_UP
                        && scareCard
                        && canRepresentStrength(ctx)
                    ) {
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.15, "bluff raising turn scare card")
                    } else {
                        foldAction(0.75, "folding nothing on turn")
                    }
                }
            }
        }

        // ── CHECKED TO (DOUBLE BARREL DECISION) ──────────────────
        if (ctx.isInitiator || ctx.isAggressor) {
            val sizing = chooseSizing(ctx, tier >= HandStrengthTier.MEDIUM)

            return when (tier) {
                HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                    betAction(ctx, sizing, 0.8, "double barreling for value")
                }
                HandStrengthTier.MEDIUM -> {
                    if (scareCard) {
                        checkAction(0.5, "checking turn — bad card, pot control")
                    } else if (instinct > 50) {
                        betAction(ctx, sizing, 0.55, "barreling turn with medium hand")
                    } else {
                        checkAction(0.5, "checking turn, pot control")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 5) {
                        betAction(ctx, sizing, 0.5, "semi-bluff barrel on turn")
                    } else if (scareCard && shouldBluff(instinct, ctx, 45)) {
                        betAction(ctx, sizing, 0.3, "bluff barrel on scare card")
                    } else {
                        checkAction(0.55, "giving up on turn — no draw, no scare card")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (scareCard && shouldBluff(instinct, ctx, 40)) {
                        betAction(ctx, sizing, 0.25, "bluff barrel on turn scare card")
                    } else {
                        checkAction(0.6, "checking nothing on turn — will bluff river if opportunity arises")
                    }
                }
            }
        }

        // Not the aggressor — checked to us on the turn.
        return when (tier) {
            HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                betAction(ctx, chooseSizing(ctx, false), 0.7, "betting turn for value")
            }
            HandStrengthTier.MEDIUM -> {
                if (instinct > 50)
                    betAction(ctx, chooseSizing(ctx, false) * 0.75, 0.4, "thin bet on turn")
                else checkAction(0.55, "checking turn")
            }
            HandStrengthTier.WEAK -> {
                if (ctx.hand.totalOuts >= 5 && instinct > 45)
                    betAction(ctx, chooseSizing(ctx, true), 0.35, "betting draw on turn")
                else checkAction(0.6, "checking weak hand on turn")
            }
            HandStrengthTier.NOTHING -> {
                if (ctx.potType == PotType.HEADS_UP && instinct > 60 && scareCard) {
                    betAction(ctx, chooseSizing(ctx, true), 0.25, "delayed bluff on turn scare card")
                } else {
                    checkAction(0.65, "checking nothing on turn")
                }
            }
        }
    }

    private fun decideRiverLag(
        ctx: DecisionContext,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile
        val tier = ctx.hand.tier
        val scareCard = isScareTurnOrRiver(ctx)

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER ->
                    callAction(ctx, 0.85, "calling river raise with monster")
                HandStrengthTier.STRONG -> {
                    if (instinct > 55)
                        callAction(ctx, 0.4, "hero calling river raise — they might be adjusting to me")
                    else foldAction(0.55, "folding to river raise — they probably have it")
                }
                else -> foldAction(0.7, "folding to river raise")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 30)
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.75, "raising river for max value")
                    else callAction(ctx, 0.8, "calling river with monster")
                }
                HandStrengthTier.STRONG -> {
                    callAction(ctx, 0.7, "calling river with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.5) {
                        callAction(ctx, 0.55, "calling small river bet with medium hand")
                    } else if (ctx.betAsFractionOfPot <= 0.75 && instinct > 50) {
                        callAction(ctx, 0.4, "calling river — might be a bluff")
                    } else {
                        foldAction(0.55, "folding medium hand to big river bet")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (instinct > 80
                        && ctx.potType == PotType.HEADS_UP
                        && ctx.hand.hasNutAdvantage
                        && canRepresentStrength(ctx)
                    ) {
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.15, "river bluff raise — representing the nuts")
                    } else {
                        foldAction(0.65, "folding weak hand on river")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 85
                        && ctx.potType == PotType.HEADS_UP
                        && canRepresentStrength(ctx)
                    ) {
                        raiseAction(ctx, chooseLagRaiseSizing(ctx), 0.1, "river bluff raise with nothing — max pressure")
                    } else {
                        foldAction(0.75, "folding nothing on river")
                    }
                }
            }
        }

        // ── CHECKED TO (TRIPLE BARREL / VALUE BET / BLUFF) ───────
        if (ctx.isInitiator || ctx.isAggressor) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    val sizing = p.betSizePotFraction * 1.1
                    betAction(ctx, sizing, 0.85, "river value bet with monster")
                }
                HandStrengthTier.STRONG -> {
                    betAction(ctx, p.betSizePotFraction, 0.7, "river value bet with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.board.wetness <= BoardWetness.SEMI_WET
                        && !ctx.board.flushCompletedThisStreet
                        && !ctx.board.straightCompletedThisStreet
                    ) {
                        if (instinct > 45)
                            betAction(ctx, p.betSizePotFraction * 0.65, 0.4, "thin river value bet")
                        else checkAction(0.5, "checking back medium — river is marginal")
                    } else {
                        checkAction(0.6, "checking medium hand — board is scary")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (shouldBluff(instinct, ctx, 45) && ctx.potType == PotType.HEADS_UP) {
                        val sizing = p.betSizePotFraction * 1.2
                        betAction(ctx, sizing, 0.2, "river bluff — missed draw, representing strength")
                    } else {
                        checkAction(0.6, "checking back weak hand — no bluff")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (shouldBluff(instinct, ctx, 40) && ctx.potType == PotType.HEADS_UP) {
                        val sizing = p.betSizePotFraction * 1.3
                        betAction(ctx, sizing, 0.15, "triple barrel bluff — they have to fold")
                    } else {
                        checkAction(0.65, "giving up — no bluff on the river")
                    }
                }
            }
        }

        // Not the aggressor — checked to on the river.
        return when (tier) {
            HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                betAction(ctx, p.betSizePotFraction, 0.7, "value betting river")
            }
            HandStrengthTier.MEDIUM -> {
                if (ctx.board.wetness <= BoardWetness.SEMI_WET && instinct > 50)
                    betAction(ctx, p.betSizePotFraction * 0.6, 0.35, "thin value on river")
                else checkAction(0.6, "checking river")
            }
            HandStrengthTier.WEAK, HandStrengthTier.NOTHING -> {
                if (instinct > 70 && ctx.potType == PotType.HEADS_UP && scareCard) {
                    betAction(ctx, p.betSizePotFraction * 1.1, 0.2, "delayed bluff on scary river")
                } else {
                    checkAction(0.7, "checking river — nothing to bet")
                }
            }
        }
    }

    // ── Action construction helpers ─────────────────────────────────

    private fun foldAction(confidence: Double, reasoning: String) =
        ActionDecision(Action.fold(), confidence, reasoning)

    private fun checkAction(confidence: Double, reasoning: String) =
        ActionDecision(Action.check(), confidence, reasoning)

    private fun callAction(ctx: DecisionContext, confidence: Double, reasoning: String) =
        ActionDecision(Action.call(ctx.betToCall), confidence, reasoning)

    private fun betAction(
        ctx: DecisionContext,
        potFraction: Double,
        confidence: Double,
        reasoning: String
    ): ActionDecision {
        val amount = (ctx.potSize * potFraction).toInt()
            .coerceAtLeast(ctx.suggestedSizes.minRaise)
        return ActionDecision(Action.raise(amount), confidence, reasoning)
    }

    private fun raiseAction(
        ctx: DecisionContext,
        multiplier: Double,
        confidence: Double,
        reasoning: String
    ): ActionDecision {
        val raiseToAmount = (ctx.betToCall * multiplier).toInt()
            .coerceAtLeast(ctx.suggestedSizes.minRaise)
        return ActionDecision(Action.raise(raiseToAmount), confidence, reasoning)
    }
}
