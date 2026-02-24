package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.BoardWetness
import com.pokerai.analysis.HandStrengthTier
import com.pokerai.analysis.PotType
import com.pokerai.model.Action

/**
 * Coded postflop strategy for the Nit archetype.
 *
 * The Nit is tight, passive, and risk-averse:
 * - Folds too much, especially on turn and river
 * - Rarely bluffs
 * - Rarely raises (only with monsters)
 * - Has sizing tells (bets smaller with medium hands, larger with strong hands)
 * - Misses thin value on the river (checks back hands they should bet)
 * - Gives up on turns after c-betting with medium/weak hands
 *
 * The instinct value (1-100) controls behavioral variation:
 * - Low instinct (1-30): maximum caution, fold anything marginal
 * - Medium instinct (31-65): default tight play
 * - High instinct (66-85): slightly looser, might call one extra street
 * - Very high instinct (86-100): rare deviation — might bluff or hero call
 */
class NitStrategy : ArchetypeStrategy {

    override fun decide(ctx: DecisionContext): ActionDecision {
        val effectiveInstinct = adjustInstinct(ctx)
        val effectiveTier = adjustTierForPotType(ctx)

        return when (ctx.street) {
            Street.PREFLOP -> decidePreflopNit(ctx, effectiveTier, effectiveInstinct)
            Street.FLOP -> decideFlopNit(ctx, effectiveTier, effectiveInstinct)
            Street.TURN -> decideTurnNit(ctx, effectiveTier, effectiveInstinct)
            Street.RIVER -> decideRiverNit(ctx, effectiveTier, effectiveInstinct)
        }
    }

    // ── Instinct and tier adjustments ───────────────────────────────

    private fun adjustInstinct(ctx: DecisionContext): Int {
        var instinct = ctx.instinct

        // SPR-based adjustment: low SPR makes nits MORE cautious (unless they have a monster)
        if (ctx.spr < 3.0 && ctx.hand.tier > HandStrengthTier.STRONG) {
            instinct -= 10
        }

        // Multiway pots make nits more cautious
        if (ctx.potType == PotType.MULTIWAY) {
            instinct -= 5
        }

        // Wet boards make nits more cautious
        if (ctx.board.wetness == BoardWetness.VERY_WET) {
            instinct -= 5
        }

        // ── Session result adjustment ──────────────────────────
        ctx.sessionStats?.let { session ->
            when {
                session.resultBB < -30.0 -> instinct -= 10
                session.resultBB < -15.0 -> instinct -= 5
                session.resultBB > 30.0 -> instinct += 5
            }
        }

        // ── Recent showdown adjustment ─────────────────────────
        ctx.sessionStats?.recentShowdowns?.let { showdowns ->
            val recent = showdowns.firstOrNull { it.handsAgo <= 5 }
            if (recent != null) {
                when (recent.event) {
                    ShowdownEvent.GOT_BLUFFED -> instinct += 12
                    ShowdownEvent.CALLED_AND_LOST -> instinct -= 10
                    ShowdownEvent.CALLED_AND_WON -> instinct += 5
                    ShowdownEvent.SAW_OPPONENT_BLUFF -> instinct += 5
                    ShowdownEvent.SAW_BIG_POT_LOSS -> instinct -= 5
                }
            }
        }

        // ── Opponent type adjustment ───────────────────────────
        ctx.bettorRead?.let { bettor ->
            when (bettor.playerType) {
                OpponentType.LOOSE_AGGRESSIVE -> instinct += 10
                OpponentType.LOOSE_PASSIVE -> instinct += 5
                OpponentType.TIGHT_AGGRESSIVE -> instinct -= 15
                OpponentType.TIGHT_PASSIVE -> instinct -= 10
                OpponentType.UNKNOWN -> {}
            }
        }

        // ── Recent bluff by the bettor specifically ────────────
        if (ctx.bettorRead != null) {
            ctx.sessionStats?.recentShowdowns
                ?.filter { it.opponentIndex == ctx.bettorRead.playerIndex && it.handsAgo <= 10 }
                ?.firstOrNull { it.event == ShowdownEvent.GOT_BLUFFED || it.event == ShowdownEvent.SAW_OPPONENT_BLUFF }
                ?.let {
                    instinct += 8
                }
        }

        return instinct.coerceIn(1, 100)
    }

    private fun adjustTierForPotType(ctx: DecisionContext): HandStrengthTier {
        val tier = ctx.hand.tier
        if (ctx.potType == PotType.THREE_WAY || ctx.potType == PotType.MULTIWAY) {
            return when (tier) {
                HandStrengthTier.MONSTER -> HandStrengthTier.MONSTER
                HandStrengthTier.STRONG -> HandStrengthTier.MEDIUM
                HandStrengthTier.MEDIUM -> HandStrengthTier.WEAK
                HandStrengthTier.WEAK -> HandStrengthTier.NOTHING
                HandStrengthTier.NOTHING -> HandStrengthTier.NOTHING
            }
        }
        return tier
    }

    // ── Street handlers ─────────────────────────────────────────────

    private fun decidePreflopNit(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        // Preflop is normally handled by PreFlopStrategy before this is called.
        // This is a fallback.
        return when {
            ctx.facingBet && tier >= HandStrengthTier.MEDIUM ->
                foldAction(0.7, "preflop fold — not strong enough")
            ctx.facingBet && tier == HandStrengthTier.STRONG ->
                callAction(ctx, 0.6, "preflop call with strong hand")
            ctx.facingBet && tier == HandStrengthTier.MONSTER ->
                raiseAction(ctx, ctx.profile.raiseMultiplier, 0.8, "preflop raise with monster")
            !ctx.facingBet && tier <= HandStrengthTier.STRONG ->
                betAction(ctx, 2.5 * ctx.suggestedSizes.fullPot / ctx.potSize.coerceAtLeast(1), 0.7, "open raise")
            else -> foldAction(0.8, "preflop default fold")
        }
    }

    private fun decideFlopNit(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile

        // ── FACING A RAISE (we bet/raised, opponent raised us back) ────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 70) raiseAction(ctx, p.raiseMultiplier, 0.9, "re-raise with monster")
                    else callAction(ctx, 0.85, "call the raise — we have a monster")
                }
                HandStrengthTier.STRONG -> {
                    if (instinct > 80 && ctx.board.wetness <= BoardWetness.SEMI_WET)
                        callAction(ctx, 0.35, "hero call on dry board")
                    else foldAction(0.8, "folding strong hand to flop raise")
                }
                else -> foldAction(0.95, "easy fold to flop raise")
            }
        }

        // ── FACING A BET (opponent bet, we haven't acted aggressively yet) ──
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 60)
                        raiseAction(ctx, p.raiseMultiplier, 0.85, "raising for value with monster")
                    else callAction(ctx, 0.9, "slowplaying monster — just call")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.betAsFractionOfPot <= p.postFlopCallCeiling)
                        callAction(ctx, 0.75, "calling with strong hand")
                    else foldAction(0.65, "bet too large for comfort even with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.5)
                        callAction(ctx, 0.5, "reluctant call with medium hand")
                    else foldAction(0.7, "medium hand can't handle this sizing")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8 && instinct > 65 && hasDecentOdds(ctx))
                        callAction(ctx, 0.3, "chasing a draw against better judgment")
                    else foldAction(0.9, "folding weak hand to bet")
                }
                HandStrengthTier.NOTHING -> foldAction(0.98, "nothing — easy fold")
            }
        }

        // ── CHECKED TO (we can bet or check) ────────────────────────────
        return when {
            ctx.isInitiator && tier <= HandStrengthTier.STRONG -> {
                betAction(ctx, p.betSizePotFraction, 0.8, "c-bet with strong+ hand")
            }
            ctx.isInitiator && tier == HandStrengthTier.MEDIUM -> {
                val cbetFreq = 1.0 - p.postFlopCheckProb
                if (shouldAct(cbetFreq, instinct)) {
                    val sizing = if (ctx.board.wetness <= BoardWetness.SEMI_WET)
                        p.betSizePotFraction
                    else p.betSizePotFraction * 0.6
                    betAction(ctx, sizing, 0.5, "c-bet with medium hand")
                } else {
                    checkAction(0.6, "checking medium hand — not confident enough to bet")
                }
            }
            ctx.isInitiator && tier == HandStrengthTier.WEAK -> {
                if (ctx.board.wetness <= BoardWetness.DRY && instinct > 85)
                    betAction(ctx, p.betSizePotFraction * 0.6, 0.25, "dry board bluff c-bet")
                else checkAction(0.9, "giving up with weak hand")
            }
            ctx.isInitiator && tier == HandStrengthTier.NOTHING -> {
                if (ctx.board.wetness <= BoardWetness.DRY && instinct > 90)
                    betAction(ctx, p.betSizePotFraction * 0.5, 0.2, "rare air c-bet on dry board")
                else checkAction(0.95, "no hand, no bet")
            }

            // NOT the initiator — very passive, even with strong hands
            tier <= HandStrengthTier.STRONG -> {
                if (instinct > 70)
                    betAction(ctx, p.betSizePotFraction, 0.5, "leading with strong hand — unusual for us")
                else checkAction(0.6, "checking strong hand to the raiser")
            }
            tier == HandStrengthTier.MEDIUM -> {
                checkAction(0.8, "checking medium hand — hoping for free card")
            }
            else -> checkAction(0.95, "checking — nothing to bet with")
        }
    }

    private fun decideTurnNit(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile

        // ── FACING A RAISE ────────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 60) raiseAction(ctx, p.raiseMultiplier, 0.85, "re-raising turn with monster")
                    else callAction(ctx, 0.8, "calling turn raise with monster")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.board.wetness <= BoardWetness.DRY && instinct > 80)
                        callAction(ctx, 0.3, "painful call — dry board gives me hope")
                    else foldAction(0.85, "can't handle the turn raise")
                }
                else -> foldAction(0.95, "folding to turn raise")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 50)
                        raiseAction(ctx, p.raiseMultiplier, 0.8, "raising turn with monster")
                    else callAction(ctx, 0.9, "calling — want to keep them in")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.betAsFractionOfPot <= p.postFlopCallCeiling) {
                        callAction(ctx, 0.6, "calling turn with strong hand — uncomfortable though")
                    } else {
                        if (ctx.board.wetness <= BoardWetness.DRY && instinct > 60)
                            callAction(ctx, 0.35, "calling big turn bet on dry board")
                        else foldAction(0.7, "turn bet too big — folding strong hand")
                    }
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.4
                        && ctx.board.wetness <= BoardWetness.SEMI_WET
                        && instinct > 60
                    ) {
                        callAction(ctx, 0.25, "small bet on safe board — reluctant call")
                    } else {
                        foldAction(0.75, "folding medium hand on turn — there will be better spots")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8 && hasDecentOdds(ctx) && instinct > 70)
                        callAction(ctx, 0.2, "drawing on the turn — probably a mistake")
                    else foldAction(0.95, "folding weak hand on turn")
                }
                HandStrengthTier.NOTHING -> foldAction(0.98, "nothing on the turn — done")
            }
        }

        // ── CHECKED TO ────────────────────────────────────────────────
        return when {
            ctx.isInitiator && tier <= HandStrengthTier.STRONG -> {
                betAction(ctx, p.betSizePotFraction, 0.75, "double barrel with strong hand")
            }
            ctx.isInitiator && tier == HandStrengthTier.MEDIUM -> {
                if (instinct > 80 && !ctx.board.flushCompletedThisStreet && !ctx.board.straightCompletedThisStreet) {
                    betAction(ctx, p.betSizePotFraction * 0.7, 0.3, "trying to barrel turn — undersizing")
                } else {
                    checkAction(0.8, "giving up on turn with medium hand")
                }
            }
            ctx.isInitiator && tier == HandStrengthTier.WEAK && ctx.hand.totalOuts >= 12 -> {
                if (instinct > 80)
                    betAction(ctx, p.betSizePotFraction * 0.7, 0.25, "semi-bluff with big draw")
                else checkAction(0.75, "checking big draw — scared to barrel")
            }
            !ctx.isInitiator && tier <= HandStrengthTier.STRONG -> {
                if (instinct > 65)
                    betAction(ctx, p.betSizePotFraction, 0.5, "leading turn with strong hand")
                else checkAction(0.6, "check-calling with strong hand")
            }
            else -> checkAction(0.9, "checking turn — no reason to bet")
        }
    }

    private fun decideRiverNit(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile

        // ── FACING A RAISE ────────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER -> callAction(ctx, 0.9, "calling river raise with monster")
                else -> foldAction(0.95, "folding to river raise — they always have it")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> callAction(ctx, 0.95, "easy call with monster on river")
                HandStrengthTier.STRONG -> {
                    if (ctx.betAsFractionOfPot <= p.postFlopCallCeiling
                        && !ctx.board.flushCompletedThisStreet
                        && !ctx.board.straightCompletedThisStreet
                    ) {
                        if (instinct > 70)
                            callAction(ctx, 0.45, "hero call — board is safe and their line is suspect")
                        else callAction(ctx, 0.55, "calling river with strong hand")
                    } else if (ctx.betAsFractionOfPot > 0.75) {
                        if (ctx.board.wetness <= BoardWetness.DRY && instinct > 75)
                            callAction(ctx, 0.25, "painful call against big river bet on dry board")
                        else foldAction(0.8, "big river bet — folding strong hand")
                    } else {
                        foldAction(0.7, "river is scary — folding strong hand")
                    }
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.33 && instinct > 75)
                        callAction(ctx, 0.2, "tiny river bet — maybe they're bluffing")
                    else foldAction(0.85, "folding medium hand on river")
                }
                HandStrengthTier.WEAK -> foldAction(0.98, "folding weak hand on river")
                HandStrengthTier.NOTHING -> foldAction(0.99, "nothing on the river — instant fold")
            }
        }

        // ── CHECKED TO ────────────────────────────────────────────────
        return when (tier) {
            HandStrengthTier.MONSTER -> {
                val sizing = p.betSizePotFraction * 0.8
                betAction(ctx, sizing, 0.85, "value betting monster — slightly undersized")
            }
            HandStrengthTier.STRONG -> {
                if (ctx.board.wetness <= BoardWetness.SEMI_WET
                    && !ctx.board.flushCompletedThisStreet
                    && !ctx.board.straightCompletedThisStreet
                    && instinct > 55
                ) {
                    val sizing = p.betSizePotFraction * 0.65
                    betAction(ctx, sizing, 0.4, "thin value bet — might check this back normally")
                } else {
                    checkAction(0.7, "checking back strong hand — scared of check-raise")
                }
            }
            HandStrengthTier.MEDIUM -> {
                checkAction(0.95, "checking back medium hand — no way I'm betting this")
            }
            HandStrengthTier.WEAK, HandStrengthTier.NOTHING -> {
                if (instinct > 92
                    && ctx.board.wetness <= BoardWetness.SEMI_WET
                    && ctx.hand.hasNutAdvantage
                ) {
                    betAction(ctx, p.betSizePotFraction * 0.7, 0.15, "rare river bluff — have a blocker")
                } else {
                    checkAction(0.98, "checking back — not bluffing")
                }
            }
        }
    }

    // ── Helper methods ──────────────────────────────────────────────

    /**
     * Decide whether to take an action based on a base probability and instinct.
     * Higher instinct makes the nit more likely to DEVIATE from their tight default.
     */
    private fun shouldAct(baseProbability: Double, instinct: Int): Boolean {
        val modifier = (instinct - 50) * 0.006
        val adjustedProb = (baseProbability + modifier).coerceIn(0.05, 0.95)
        return Math.random() < adjustedProb
    }

    /**
     * Check if the pot odds justify calling with a draw.
     * The nit requires BETTER odds than mathematically needed (adds a comfort margin).
     */
    private fun hasDecentOdds(ctx: DecisionContext): Boolean {
        if (ctx.hand.totalOuts <= 0) return false
        val cardsRemaining = if (ctx.street == Street.FLOP) 47.0 else 46.0
        val drawProbability = ctx.hand.totalOuts.toDouble() / cardsRemaining
        val comfortMargin = 1.3
        val requiredPotOdds = drawProbability * comfortMargin
        return ctx.potOdds <= requiredPotOdds
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
