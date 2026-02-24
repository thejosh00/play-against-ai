package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.HandStrengthTier
import com.pokerai.model.Action

/**
 * Coded postflop strategy for the Calling Station archetype.
 *
 * The calling station's default action is to call. They:
 * - Call with any piece of the board (any pair, any draw, ace high, sometimes king high)
 * - Almost never raise (only with monsters, and even then they often just call)
 * - Almost never bluff (rare desperate river bluffs with missed draws)
 * - Are nearly insensitive to bet sizing
 * - Don't think about what the opponent has — only about their own hand
 * - DO fold when they have absolutely nothing (no pair, no draw, no overcards)
 * - DO fold on the river when they missed a draw (no more cards to come)
 *
 * Instinct (1-100):
 * - Low instinct (1-30): slightly more disciplined — folds the weakest holdings
 * - Medium instinct (31-65): default calling station behavior
 * - High instinct (66-85): calls even wider, might call with king high
 * - Very high instinct (86-100): might raise a strong hand or attempt a clumsy bluff
 */
class CallingStationStrategy : ArchetypeStrategy {

    override fun decide(ctx: DecisionContext): ActionDecision {
        val effectiveInstinct = adjustInstinct(ctx)
        val effectiveTier = adjustTierForPotType(ctx)

        return when (ctx.street) {
            Street.PREFLOP -> decidePreflopCallingStation(ctx, effectiveTier, effectiveInstinct)
            Street.FLOP -> decideFlopCallingStation(ctx, effectiveTier, effectiveInstinct)
            Street.TURN -> decideTurnCallingStation(ctx, effectiveTier, effectiveInstinct)
            Street.RIVER -> decideRiverCallingStation(ctx, effectiveTier, effectiveInstinct)
        }
    }

    // ── Instinct and tier adjustments ───────────────────────────────

    private fun adjustInstinct(ctx: DecisionContext): Int {
        var instinct = ctx.instinct

        // Calling stations DON'T adjust for board texture — they don't think about it.
        // They DON'T adjust for SPR — they don't know what that is.
        // They DON'T adjust much for multiway — they just look at their own hand.

        // Session result: calling stations on a losing streak get LOOSER (chase losses)
        // This is the opposite of the nit who tightens up when losing.
        ctx.sessionStats?.let { session ->
            when {
                session.resultBB < -30.0 -> instinct += 8   // losing → chase harder
                session.resultBB < -15.0 -> instinct += 3   // losing a bit → slightly looser
                session.resultBB > 30.0 -> instinct -= 3    // winning → slightly more content to fold marginal hands
            }
        }

        // Recent showdown: calling stations are heavily affected by recent results
        ctx.sessionStats?.recentShowdowns?.let { showdowns ->
            val recent = showdowns.firstOrNull { it.handsAgo <= 20 }
            if (recent != null) {
                when (recent.event) {
                    ShowdownEvent.CALLED_AND_WON -> instinct += 15  // "See! I knew they were bluffing!" → calls even wider
                    ShowdownEvent.CALLED_AND_LOST -> instinct -= 5  // slight discouragement, but short-lived
                    ShowdownEvent.GOT_BLUFFED -> instinct += 10     // "I KNEW I should have called!" → very sticky next few hands
                    ShowdownEvent.SAW_OPPONENT_BLUFF -> instinct += 8  // "People bluff all the time!"
                    ShowdownEvent.SAW_BIG_POT_LOSS -> {}            // doesn't affect them — they're not scared of big pots
                }
            }
        }

        // Opponent type: calling stations don't really adjust based on opponent type
        // because they don't think about what the opponent has.
        // But they're slightly more likely to fold to a player who has SHOWN strength
        // at recent showdowns — a vague memory that "that guy always has it."
        ctx.bettorRead?.let { bettor ->
            // Only the most extreme adjustment: if this specific opponent beat them recently
            ctx.sessionStats?.recentShowdowns
                ?.filter { it.opponentIndex == bettor.playerIndex && it.handsAgo <= 8 }
                ?.firstOrNull { it.event == ShowdownEvent.CALLED_AND_LOST }
                ?.let {
                    instinct -= 8  // "that guy had it last time..."
                }
        }

        return instinct.coerceIn(1, 100)
    }

    private fun adjustTierForPotType(ctx: DecisionContext): HandStrengthTier {
        // Calling stations don't meaningfully adjust for pot type.
        // They call with what they call with regardless of how many players are in.
        return ctx.hand.tier
    }

    // ── Street handlers ─────────────────────────────────────────────

    private fun decidePreflopCallingStation(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        // Preflop is normally handled by PreFlopStrategy.
        // Fallback: calling stations call almost everything and rarely raise.
        return when {
            ctx.facingBet && tier == HandStrengthTier.MONSTER ->
                if (instinct > 75) raiseAction(ctx, ctx.profile.raiseMultiplier, 0.6, "raising big hand preflop")
                else callAction(ctx, 0.8, "just calling with a big hand")
            ctx.facingBet && tier <= HandStrengthTier.MEDIUM ->
                callAction(ctx, 0.7, "calling to see a flop")
            ctx.facingBet && tier == HandStrengthTier.WEAK ->
                if (instinct > 40) callAction(ctx, 0.5, "calling with a weak hand — want to see cards")
                else foldAction(0.5, "folding a weak hand preflop")
            ctx.facingBet && tier == HandStrengthTier.NOTHING ->
                foldAction(0.7, "even I can fold this preflop")
            !ctx.facingBet && tier <= HandStrengthTier.STRONG ->
                callAction(ctx, 0.6, "limping in")  // calling station limps rather than raises
            else -> foldAction(0.6, "folding preflop")
        }
    }

    private fun decideFlopCallingStation(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile

        // ── FACING A RAISE (our bet was raised) ──────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER ->
                    callAction(ctx, 0.9, "calling the raise — I have a big hand")
                HandStrengthTier.STRONG ->
                    callAction(ctx, 0.8, "calling the raise — my hand is good")
                HandStrengthTier.MEDIUM ->
                    callAction(ctx, 0.6, "calling the raise — I have something")
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 5 || instinct > 60)
                        callAction(ctx, 0.4, "calling the raise with a draw")
                    else foldAction(0.6, "raise is too much with this weak hand")
                }
                HandStrengthTier.NOTHING ->
                    foldAction(0.75, "nothing — even I fold to a raise here")
            }
        }

        // ── FACING A BET ─────────────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 80)
                        raiseAction(ctx, p.raiseMultiplier, 0.7, "raising! I have a great hand!")
                    else callAction(ctx, 0.95, "calling with a monster — don't want to scare them")
                }
                HandStrengthTier.STRONG -> {
                    if (instinct > 90)
                        raiseAction(ctx, p.raiseMultiplier, 0.5, "raising with a strong hand — feeling bold")
                    else callAction(ctx, 0.9, "calling with a strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    callAction(ctx, 0.85, "I have a pair — calling")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.madeHand || ctx.hand.totalOuts >= 4 || instinct > 45) {
                        callAction(ctx, 0.7, "I have something — calling")
                    } else {
                        foldAction(0.6, "I really have nothing here")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 75) {
                        callAction(ctx, 0.25, "maybe they're bluffing — I'll call with nothing")
                    } else {
                        foldAction(0.7, "I have absolutely nothing")
                    }
                }
            }
        }

        // ── CHECKED TO ───────────────────────────────────────────────────
        return when (tier) {
            HandStrengthTier.MONSTER -> {
                if (instinct > 55)
                    betAction(ctx, p.betSizePotFraction, 0.6, "betting my monster — want to build the pot")
                else checkAction(0.7, "checking my monster — someone will bet, then I'll call")
            }
            HandStrengthTier.STRONG -> {
                if (instinct > 75)
                    betAction(ctx, p.betSizePotFraction, 0.4, "betting strong hand")
                else checkAction(0.8, "checking strong hand — I'll call if someone bets")
            }
            HandStrengthTier.MEDIUM -> {
                if (instinct > 88)
                    betAction(ctx, p.betSizePotFraction * 0.6, 0.2, "small bet with medium hand")
                else checkAction(0.9, "checking — I'll see what happens")
            }
            else -> {
                checkAction(0.95, "checking")
            }
        }
    }

    private fun decideTurnCallingStation(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile

        // ── FACING A RAISE ──────────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER -> callAction(ctx, 0.9, "calling turn raise with monster")
                HandStrengthTier.STRONG -> callAction(ctx, 0.75, "calling turn raise — my hand is good")
                HandStrengthTier.MEDIUM -> {
                    if (instinct > 50)
                        callAction(ctx, 0.45, "calling turn raise — I have a pair")
                    else foldAction(0.55, "turn raise is scary even for me")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8 && instinct > 55)
                        callAction(ctx, 0.3, "calling turn raise with a draw")
                    else foldAction(0.65, "folding to turn raise")
                }
                HandStrengthTier.NOTHING -> foldAction(0.8, "nothing on the turn — folding to raise")
            }
        }

        // ── FACING A BET ────────────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 75)
                        raiseAction(ctx, p.raiseMultiplier, 0.65, "raising turn with monster")
                    else callAction(ctx, 0.9, "calling turn with monster")
                }
                HandStrengthTier.STRONG -> {
                    callAction(ctx, 0.85, "calling turn with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    callAction(ctx, 0.8, "I have a pair on the turn — calling")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.madeHand || ctx.hand.totalOuts >= 4 || instinct > 50) {
                        callAction(ctx, 0.6, "calling turn — I have something")
                    } else if (instinct > 65) {
                        callAction(ctx, 0.3, "calling turn with high card — they might be bluffing")
                    } else {
                        foldAction(0.6, "weak hand on the turn — folding")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 80)
                        callAction(ctx, 0.2, "calling with nothing — stubborn")
                    else foldAction(0.75, "nothing on the turn — even I fold")
                }
            }
        }

        // ── CHECKED TO ──────────────────────────────────────────────────
        return when (tier) {
            HandStrengthTier.MONSTER -> {
                if (instinct > 50)
                    betAction(ctx, p.betSizePotFraction, 0.55, "betting monster on turn")
                else checkAction(0.65, "checking monster — trapping")
            }
            HandStrengthTier.STRONG -> {
                if (instinct > 70)
                    betAction(ctx, p.betSizePotFraction, 0.35, "betting strong on turn")
                else checkAction(0.8, "checking strong hand on turn")
            }
            else -> {
                checkAction(0.9, "checking turn")
            }
        }
    }

    private fun decideRiverCallingStation(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile

        // ── FACING A RAISE ──────────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER -> callAction(ctx, 0.9, "calling river raise with monster")
                HandStrengthTier.STRONG -> callAction(ctx, 0.7, "calling river raise — I have a good hand")
                HandStrengthTier.MEDIUM -> {
                    if (instinct > 60)
                        callAction(ctx, 0.35, "calling river raise — I can't fold a pair")
                    else foldAction(0.55, "river raise is too much")
                }
                else -> foldAction(0.75, "folding to river raise")
            }
        }

        // ── FACING A BET ────────────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 65)
                        raiseAction(ctx, p.raiseMultiplier, 0.6, "raising river with a monster!")
                    else callAction(ctx, 0.9, "calling river with monster")
                }
                HandStrengthTier.STRONG -> {
                    callAction(ctx, 0.85, "calling river — I have a strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    callAction(ctx, 0.75, "I have a pair — I call")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.madeHand) {
                        if (instinct > 30)
                            callAction(ctx, 0.6, "I have a pair — have to call")
                        else foldAction(0.4, "weak pair... maybe I should fold")
                    } else {
                        if (instinct > 80)
                            callAction(ctx, 0.2, "missed my draw but maybe they're bluffing")
                        else foldAction(0.7, "missed my draw — no more cards coming")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 85)
                        callAction(ctx, 0.15, "calling with nothing — pure stubbornness")
                    else foldAction(0.8, "nothing on the river — have to fold")
                }
            }
        }

        // ── CHECKED TO ON THE RIVER ─────────────────────────────────────
        return when (tier) {
            HandStrengthTier.MONSTER -> {
                if (instinct > 40)
                    betAction(ctx, p.betSizePotFraction, 0.6, "betting my monster on the river")
                else checkAction(0.5, "checking monster — maybe they'll bet")
            }
            HandStrengthTier.STRONG -> {
                if (instinct > 65)
                    betAction(ctx, p.betSizePotFraction * 0.7, 0.35, "value bet on river with strong hand")
                else checkAction(0.75, "checking strong hand on river")
            }
            HandStrengthTier.MEDIUM -> {
                checkAction(0.9, "checking river — I have a pair, that's enough")
            }
            HandStrengthTier.WEAK -> {
                if (!ctx.hand.madeHand && instinct > 85) {
                    val bluffSizing = if (instinct > 92) {
                        p.betSizePotFraction * 1.2  // occasional overbet bluff (clumsy)
                    } else {
                        p.betSizePotFraction * 0.4  // weak undersized bluff
                    }
                    betAction(ctx, bluffSizing, 0.1, "missed my draw — desperate bluff")
                } else {
                    checkAction(0.9, "checking weak hand on river")
                }
            }
            HandStrengthTier.NOTHING -> {
                if (instinct > 90) {
                    betAction(ctx, p.betSizePotFraction * 0.4, 0.08, "bluffing with nothing — pure desperation")
                } else {
                    checkAction(0.95, "checking nothing on river")
                }
            }
        }
    }

    // ── Action construction helpers ─────────────────────────────────

    private fun foldAction(confidence: Double, reasoning: String) =
        ActionDecision(Action.fold(), confidence, reasoning)

    private fun checkAction(confidence: Double, reasoning: String) =
        ActionDecision(Action.check(), confidence, reasoning)

    private fun callAction(ctx: DecisionContext, confidence: Double, reasoning: String): ActionDecision {
        return ActionDecision(Action.call(ctx.betToCall), confidence, reasoning)
    }

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
