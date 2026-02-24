package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.BoardWetness
import com.pokerai.analysis.HandStrengthTier
import com.pokerai.analysis.PotType
import com.pokerai.model.Action
import com.pokerai.model.Position

/**
 * Coded postflop strategy for the TAG (Tight Aggressive) archetype.
 *
 * The TAG plays a solid, value-oriented style:
 * - C-bets selectively (55-70%) based on hand + board
 * - Barrels turns with strong hands and good draws
 * - Value bets rivers but not super thin
 * - Bluffs mostly with semi-bluffs (draws), rarely with pure air
 * - Folds to aggression more readily than the LAG
 * - More honest than the LAG — betting range is mostly value
 *
 * The TAG's main leak is predictability: they're readable because
 * their actions correlate with hand strength more transparently
 * than the LAG's.
 *
 * Instinct (1-100):
 * - Low instinct (1-30): plays even tighter — approaches nit territory
 * - Medium instinct (31-65): standard TAG play
 * - High instinct (66-85): slightly more aggressive — approaches LAG territory
 * - Very high instinct (86-100): uncharacteristically loose — might bluff or hero call
 */
class TagStrategy : ArchetypeStrategy {

    override fun decide(ctx: DecisionContext): ActionDecision {
        val effectiveInstinct = adjustInstinct(ctx)
        val effectiveTier = adjustTierForPotType(ctx)

        return when (ctx.street) {
            Street.PREFLOP -> error("ArchetypeStrategy should not be called preflop — use PreFlopStrategy")
            Street.FLOP -> decideFlopTag(ctx, effectiveTier, effectiveInstinct)
            Street.TURN -> decideTurnTag(ctx, effectiveTier, effectiveInstinct)
            Street.RIVER -> decideRiverTag(ctx, effectiveTier, effectiveInstinct)
        }
    }

    // ── Instinct adjustment ──────────────────────────────────────────

    private fun adjustInstinct(ctx: DecisionContext): Int {
        var instinct = ctx.instinct

        // Position: TAG is moderately position-aware. Not as extreme as LAG.
        if (ctx.position in listOf(Position.BTN, Position.CO)) {
            instinct += 5
        } else if (ctx.position in listOf(Position.SB, Position.UTG, Position.UTG1)) {
            instinct -= 3
        }

        // Board texture: TAG adjusts slightly. Wet boards → more cautious with marginal hands.
        if (ctx.board.wetness == BoardWetness.VERY_WET) {
            instinct -= 5
        }

        // Multiway: TAG plays tighter multiway (like the nit, but less extreme).
        if (ctx.potType == PotType.MULTIWAY) {
            instinct -= 5
        } else if (ctx.potType == PotType.THREE_WAY) {
            instinct -= 3
        }

        // Opponent type: moderate adjustments (half of what the LAG does).
        ctx.bettorRead?.let { bettor ->
            when (bettor.playerType) {
                OpponentType.TIGHT_PASSIVE -> instinct += 5     // bluff slightly more vs nits
                OpponentType.TIGHT_AGGRESSIVE -> instinct -= 5  // respect TAGs
                OpponentType.LOOSE_PASSIVE -> instinct -= 5     // don't bluff calling stations
                OpponentType.LOOSE_AGGRESSIVE -> instinct += 3  // call down a bit more vs LAGs
                OpponentType.UNKNOWN -> {}
            }
        }

        // When checked to and deciding whether to bet
        if (!ctx.facingBet && ctx.opponents.isNotEmpty()) {
            val primaryOpponent = ctx.opponents.firstOrNull()
            primaryOpponent?.let { opp ->
                when (opp.playerType) {
                    OpponentType.TIGHT_PASSIVE -> instinct += 5
                    OpponentType.LOOSE_PASSIVE -> instinct -= 5
                    else -> {}
                }
            }
        }

        // Session: TAG is slightly affected by session results, but stays disciplined.
        ctx.sessionStats?.let { session ->
            when {
                session.resultBB > 30.0 -> instinct += 3    // slight confidence boost
                session.resultBB < -30.0 -> instinct -= 5   // tighten up when losing
            }
        }

        // Recent showdowns
        ctx.sessionStats?.recentShowdowns?.let { showdowns ->
            val recent = showdowns.firstOrNull { it.handsAgo <= 5 }
            if (recent != null) {
                when (recent.event) {
                    ShowdownEvent.GOT_BLUFFED -> instinct += 5    // slightly more willing to call
                    ShowdownEvent.CALLED_AND_LOST -> instinct -= 5 // slightly tighter
                    ShowdownEvent.CALLED_AND_WON -> instinct += 3
                    ShowdownEvent.SAW_OPPONENT_BLUFF -> instinct += 3
                    ShowdownEvent.SAW_BIG_POT_LOSS -> instinct -= 3
                }
            }
        }

        // SPR: low SPR makes TAG more willing to commit with strong hands
        if (ctx.spr < 3.0 && ctx.hand.tier <= HandStrengthTier.STRONG) {
            instinct += 5
        }

        return instinct.coerceIn(1, 100)
    }

    // ── Tier adjustment ──────────────────────────────────────────────

    private fun adjustTierForPotType(ctx: DecisionContext): HandStrengthTier {
        val tier = ctx.hand.tier
        if (ctx.potType == PotType.MULTIWAY) {
            return when (tier) {
                HandStrengthTier.MONSTER -> HandStrengthTier.MONSTER
                HandStrengthTier.STRONG -> HandStrengthTier.STRONG
                HandStrengthTier.MEDIUM -> HandStrengthTier.WEAK
                HandStrengthTier.WEAK -> HandStrengthTier.NOTHING
                HandStrengthTier.NOTHING -> HandStrengthTier.NOTHING
            }
        }
        if (ctx.potType == PotType.THREE_WAY) {
            return when (tier) {
                HandStrengthTier.MONSTER -> HandStrengthTier.MONSTER
                HandStrengthTier.STRONG -> HandStrengthTier.STRONG
                HandStrengthTier.MEDIUM -> HandStrengthTier.MEDIUM
                HandStrengthTier.WEAK -> HandStrengthTier.NOTHING
                HandStrengthTier.NOTHING -> HandStrengthTier.NOTHING
            }
        }
        return tier
    }

    // ── Board texture helpers ────────────────────────────────────────

    /**
     * Is this a good c-bet board for the TAG?
     * TAG c-bets a narrower set of boards than the LAG.
     * Good boards: high-card dry boards (raiser's range advantage)
     * Bad boards: low connected wet boards (caller's range advantage)
     */
    private fun isGoodCbetBoard(ctx: DecisionContext): Boolean {
        val board = ctx.board
        return when {
            // Ace or king high + dry/semi-wet: great c-bet board
            board.highCard.value >= 12 && board.wetness <= BoardWetness.SEMI_WET -> true
            // Dry paired boards: good c-bet board
            board.paired && board.wetness <= BoardWetness.SEMI_WET -> true
            // Dry rainbow low boards: decent c-bet board
            board.wetness == BoardWetness.DRY -> true
            // Very wet: TAG doesn't c-bet light here
            board.wetness == BoardWetness.VERY_WET -> false
            // Moderately wet: context dependent
            else -> false
        }
    }

    /**
     * Did a scare card come that the TAG can use to continue betting?
     * TAG only continues on legitimate scare cards — not as aggressively as the LAG.
     */
    private fun isGoodBarrelCard(ctx: DecisionContext): Boolean {
        return ctx.board.flushCompletedThisStreet
            || ctx.board.straightCompletedThisStreet
            || (ctx.board.boardPairedThisStreet && ctx.hand.tier <= HandStrengthTier.STRONG)
            || ctx.board.highCard.value >= 13  // ace or king comes
    }

    // ── Sizing strategy ──────────────────────────────────────────────

    /**
     * Choose a bet size. TAG uses moderate sizing that's slightly
     * correlated with hand strength (the TAG leak).
     */
    private fun chooseSizing(ctx: DecisionContext, tier: HandStrengthTier): Double {
        val p = ctx.profile

        // Base sizing from profile
        val baseSizing = p.betSizePotFraction

        // Board-based adjustment (smaller on dry, bigger on wet — standard)
        val boardModifier = when (ctx.board.wetness) {
            BoardWetness.DRY -> 0.85
            BoardWetness.SEMI_WET -> 1.0
            BoardWetness.WET -> 1.1
            BoardWetness.VERY_WET -> 1.2
        }

        // Hand strength correlation (the TAG's sizing tell — subtle)
        val strengthModifier = when (tier) {
            HandStrengthTier.MONSTER -> 1.05      // slightly bigger with monsters
            HandStrengthTier.STRONG -> 1.0        // standard with strong
            HandStrengthTier.MEDIUM -> 0.9        // slightly smaller with medium
            HandStrengthTier.WEAK -> 0.85         // smaller with draws/bluffs
            HandStrengthTier.NOTHING -> 0.85      // same as weak (don't make bluffs obvious)
        }

        return baseSizing * boardModifier * strengthModifier
    }

    // ── Flop handler ─────────────────────────────────────────────────

    private fun decideFlopTag(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 45)
                        raiseAction(ctx, p.raiseMultiplier, 0.8, "re-raising with monster")
                    else callAction(ctx, 0.85, "calling flop raise with monster — keeping them in")
                }
                HandStrengthTier.STRONG -> {
                    callAction(ctx, 0.65, "calling flop raise with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.hand.totalOuts >= 8 && ctx.betAsFractionOfPot <= 0.5)
                        callAction(ctx, 0.4, "calling raise — have outs")
                    else if (instinct > 65 && ctx.board.wetness <= BoardWetness.SEMI_WET)
                        callAction(ctx, 0.3, "calling raise on dry board — hand might be good")
                    else foldAction(0.6, "folding medium hand to flop raise")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 9)
                        callAction(ctx, 0.35, "calling raise with big draw")
                    else foldAction(0.75, "folding weak hand to raise")
                }
                HandStrengthTier.NOTHING -> foldAction(0.85, "folding to raise")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 40)
                        raiseAction(ctx, p.raiseMultiplier, 0.75, "raising for value with monster")
                    else callAction(ctx, 0.8, "calling with monster — slowplay")
                }
                HandStrengthTier.STRONG -> {
                    if (instinct > 65 && ctx.potType == PotType.HEADS_UP)
                        raiseAction(ctx, p.raiseMultiplier, 0.5, "raising strong hand for value")
                    else callAction(ctx, 0.7, "calling with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.65)
                        callAction(ctx, 0.6, "calling with medium hand")
                    else if (ctx.hand.totalOuts >= 5 && ctx.betAsFractionOfPot <= 0.85)
                        callAction(ctx, 0.45, "calling — have some outs")
                    else foldAction(0.55, "bet too big for medium hand")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8 && hasDecentOdds(ctx))
                        callAction(ctx, 0.5, "calling with a draw — odds are there")
                    else if (ctx.hand.totalOuts >= 5 && ctx.betAsFractionOfPot <= 0.4)
                        callAction(ctx, 0.35, "calling small bet with a weak draw")
                    else foldAction(0.65, "folding weak hand")
                }
                HandStrengthTier.NOTHING -> {
                    foldAction(0.85, "folding nothing to a bet")
                }
            }
        }

        // ── CHECKED TO (C-BET DECISION) ─────────────────────────
        if (ctx.isInitiator) {
            val goodBoard = isGoodCbetBoard(ctx)
            val sizing = chooseSizing(ctx, tier)

            return when (tier) {
                HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                    betAction(ctx, sizing, 0.8, "c-betting for value")
                }
                HandStrengthTier.MEDIUM -> {
                    if (goodBoard || instinct > 50) {
                        betAction(ctx, sizing, 0.6, "c-betting medium hand for protection")
                    } else {
                        checkAction(0.55, "checking medium hand — board is bad for c-betting")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8 && goodBoard) {
                        betAction(ctx, sizing, 0.55, "semi-bluff c-bet with a draw")
                    } else if (ctx.hand.totalOuts >= 5 && goodBoard && instinct > 55) {
                        betAction(ctx, sizing, 0.4, "c-bet with a weak draw on a good board")
                    } else {
                        checkAction(0.65, "checking weak hand — not a good semi-bluff spot")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (goodBoard && ctx.potType == PotType.HEADS_UP && instinct > 65) {
                        betAction(ctx, sizing, 0.3, "bluff c-bet on a great board")
                    } else {
                        checkAction(0.75, "checking nothing — not bluffing here")
                    }
                }
            }
        }

        // NOT the initiator — checked to us.
        return when (tier) {
            HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                betAction(ctx, chooseSizing(ctx, tier), 0.65, "betting for value")
            }
            HandStrengthTier.MEDIUM -> {
                if (ctx.board.wetness >= BoardWetness.WET && instinct > 50) {
                    betAction(ctx, chooseSizing(ctx, tier) * 0.8, 0.4, "betting medium for protection on wet board")
                } else {
                    checkAction(0.6, "checking medium hand")
                }
            }
            HandStrengthTier.WEAK -> {
                if (ctx.hand.totalOuts >= 8 && instinct > 60) {
                    betAction(ctx, chooseSizing(ctx, tier), 0.35, "betting a draw")
                } else {
                    checkAction(0.7, "checking weak hand")
                }
            }
            HandStrengthTier.NOTHING -> {
                checkAction(0.85, "checking nothing")
            }
        }
    }

    // ── Turn handler ─────────────────────────────────────────────────

    private fun decideTurnTag(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile
        val goodBarrel = isGoodBarrelCard(ctx)

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER ->
                    raiseAction(ctx, p.raiseMultiplier, 0.8, "re-raising turn with monster")
                HandStrengthTier.STRONG -> {
                    callAction(ctx, 0.55, "calling turn raise with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.board.wetness <= BoardWetness.DRY && instinct > 65)
                        callAction(ctx, 0.25, "reluctant call on dry turn")
                    else foldAction(0.65, "folding medium hand to turn raise")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 10)
                        callAction(ctx, 0.3, "calling turn raise with big draw")
                    else foldAction(0.75, "folding to turn raise")
                }
                HandStrengthTier.NOTHING -> foldAction(0.9, "folding nothing to turn raise")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 40)
                        raiseAction(ctx, p.raiseMultiplier, 0.75, "raising turn with monster")
                    else callAction(ctx, 0.8, "calling turn with monster — setting up river")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.board.wetness >= BoardWetness.WET && instinct > 60)
                        raiseAction(ctx, p.raiseMultiplier, 0.45, "raising turn to protect on wet board")
                    else callAction(ctx, 0.65, "calling turn with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.5)
                        callAction(ctx, 0.5, "calling reasonable turn bet with medium hand")
                    else if (ctx.hand.totalOuts >= 5 && ctx.betAsFractionOfPot <= 0.65)
                        callAction(ctx, 0.35, "calling turn — have outs")
                    else foldAction(0.55, "folding medium hand to big turn bet")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8 && hasDecentOdds(ctx))
                        callAction(ctx, 0.4, "calling turn draw with correct odds")
                    else foldAction(0.7, "folding weak hand on turn")
                }
                HandStrengthTier.NOTHING -> foldAction(0.85, "folding nothing on turn")
            }
        }

        // ── CHECKED TO (DOUBLE BARREL DECISION) ──────────────────
        if (ctx.isInitiator || ctx.isAggressor) {
            val sizing = chooseSizing(ctx, tier)

            return when (tier) {
                HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                    betAction(ctx, sizing, 0.75, "double barreling for value")
                }
                HandStrengthTier.MEDIUM -> {
                    if (goodBarrel && instinct > 45) {
                        betAction(ctx, sizing, 0.5, "barreling turn with medium hand — good card")
                    } else if (ctx.board.wetness >= BoardWetness.WET && instinct > 55) {
                        betAction(ctx, sizing, 0.4, "barreling for protection on wet turn")
                    } else {
                        checkAction(0.55, "checking back medium hand — bad turn card")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8) {
                        betAction(ctx, sizing, 0.45, "semi-bluff barrel with a good draw")
                    } else if (ctx.hand.totalOuts >= 5 && goodBarrel && instinct > 60) {
                        betAction(ctx, sizing, 0.3, "semi-bluff on a good turn card")
                    } else {
                        checkAction(0.65, "giving up with weak draw — turn isn't good enough")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (goodBarrel && ctx.potType == PotType.HEADS_UP && instinct > 75) {
                        betAction(ctx, sizing, 0.2, "rare bluff barrel on turn scare card")
                    } else {
                        checkAction(0.8, "giving up on turn — nothing to barrel with")
                    }
                }
            }
        }

        // Not the aggressor — checked to us on the turn.
        return when (tier) {
            HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                betAction(ctx, chooseSizing(ctx, tier), 0.65, "betting turn for value")
            }
            HandStrengthTier.MEDIUM -> {
                if (instinct > 55 && ctx.board.wetness >= BoardWetness.WET) {
                    betAction(ctx, chooseSizing(ctx, tier) * 0.75, 0.4, "betting medium for protection")
                } else {
                    checkAction(0.6, "checking turn")
                }
            }
            else -> checkAction(0.7, "checking turn")
        }
    }

    // ── River handler ────────────────────────────────────────────────

    private fun decideRiverTag(
        ctx: DecisionContext,
        tier: HandStrengthTier,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.MONSTER -> callAction(ctx, 0.85, "calling river raise with monster")
                HandStrengthTier.STRONG -> {
                    if (instinct > 60 && ctx.board.wetness <= BoardWetness.SEMI_WET)
                        callAction(ctx, 0.35, "hero calling river raise on safe board")
                    else foldAction(0.6, "folding to river raise — probably beat")
                }
                else -> foldAction(0.75, "folding to river raise")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    if (instinct > 35)
                        raiseAction(ctx, p.raiseMultiplier, 0.7, "raising river for value")
                    else callAction(ctx, 0.85, "calling river with monster")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.betAsFractionOfPot <= p.postFlopCallCeiling) {
                        callAction(ctx, 0.65, "calling river with strong hand")
                    } else {
                        if (instinct > 50
                            && !ctx.board.flushCompletedThisStreet
                            && !ctx.board.straightCompletedThisStreet
                        ) {
                            callAction(ctx, 0.4, "calling big river bet — board is safe")
                        } else {
                            foldAction(0.55, "folding to big river bet — draws got there")
                        }
                    }
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.4 && instinct > 45) {
                        callAction(ctx, 0.4, "calling small river bet with medium hand")
                    } else if (ctx.betAsFractionOfPot <= 0.6 && instinct > 65) {
                        callAction(ctx, 0.3, "calling medium river bet — might be a bluff")
                    } else {
                        foldAction(0.55, "folding medium hand on river")
                    }
                }
                HandStrengthTier.WEAK -> foldAction(0.75, "folding weak hand on river")
                HandStrengthTier.NOTHING -> foldAction(0.9, "folding nothing on river")
            }
        }

        // ── CHECKED TO ────────────────────────────────────────────
        if (ctx.isInitiator || ctx.isAggressor) {
            return when (tier) {
                HandStrengthTier.MONSTER -> {
                    betAction(ctx, chooseSizing(ctx, tier) * 1.05, 0.8, "river value bet with monster")
                }
                HandStrengthTier.STRONG -> {
                    if (!ctx.board.flushCompletedThisStreet
                        && !ctx.board.straightCompletedThisStreet
                    ) {
                        betAction(ctx, chooseSizing(ctx, tier), 0.6, "river value bet with strong hand")
                    } else if (instinct > 55) {
                        betAction(ctx, chooseSizing(ctx, tier) * 0.7, 0.4, "river value bet on scary board — smaller sizing")
                    } else {
                        checkAction(0.55, "checking back strong hand — board is too scary")
                    }
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.board.wetness == BoardWetness.DRY
                        && !ctx.board.flushCompletedThisStreet
                        && !ctx.board.straightCompletedThisStreet
                        && instinct > 60
                    ) {
                        betAction(ctx, chooseSizing(ctx, tier) * 0.65, 0.3, "thin river value bet on bone-dry board")
                    } else {
                        checkAction(0.7, "checking back medium hand — not confident enough to value bet")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (!ctx.hand.madeHand
                        && ctx.hand.totalOuts == 0
                        && ctx.potType == PotType.HEADS_UP
                        && instinct > 60
                        && ctx.hand.hasNutAdvantage
                    ) {
                        betAction(ctx, chooseSizing(ctx, tier) * 0.9, 0.2, "river bluff with missed draw — have blockers")
                    } else {
                        checkAction(0.75, "checking back weak hand")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (ctx.potType == PotType.HEADS_UP
                        && instinct > 80
                        && ctx.hand.hasNutAdvantage
                    ) {
                        betAction(ctx, chooseSizing(ctx, tier), 0.15, "rare river bluff")
                    } else {
                        checkAction(0.85, "checking nothing on river")
                    }
                }
            }
        }

        // Not the aggressor — checked to on the river.
        return when (tier) {
            HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                betAction(ctx, chooseSizing(ctx, tier), 0.6, "value betting river")
            }
            HandStrengthTier.MEDIUM -> {
                if (ctx.board.wetness == BoardWetness.DRY && instinct > 60) {
                    betAction(ctx, chooseSizing(ctx, tier) * 0.6, 0.3, "thin value on dry river")
                } else {
                    checkAction(0.65, "checking river")
                }
            }
            else -> checkAction(0.8, "checking river")
        }
    }

    // ── Helper methods ───────────────────────────────────────────────

    /**
     * Check if pot odds justify calling with a draw.
     * TAG uses correct pot odds (no comfort margin like the nit, no ignoring odds like the calling station).
     * Small 10% comfort margin.
     */
    private fun hasDecentOdds(ctx: DecisionContext): Boolean {
        if (ctx.hand.totalOuts <= 0) return false
        val cardsRemaining = if (ctx.street == Street.FLOP) 47.0 else 46.0
        val drawProbability = ctx.hand.totalOuts.toDouble() / cardsRemaining
        val comfortMargin = 1.1
        val requiredPotOdds = drawProbability * comfortMargin
        return ctx.potOdds <= requiredPotOdds
    }

    // ── Action construction helpers ──────────────────────────────────

    private fun foldAction(confidence: Double, reasoning: String) =
        ActionDecision(Action.fold(), confidence, reasoning)

    private fun checkAction(confidence: Double, reasoning: String) =
        ActionDecision(Action.check(), confidence, reasoning)

    private fun callAction(ctx: DecisionContext, confidence: Double, reasoning: String) =
        ActionDecision(Action.call(ctx.betToCall), confidence, reasoning)

    private fun betAction(ctx: DecisionContext, potFraction: Double, confidence: Double, reasoning: String): ActionDecision {
        val amount = (ctx.potSize * potFraction).toInt().coerceAtLeast(ctx.suggestedSizes.minRaise)
        return ActionDecision(Action.raise(amount), confidence, reasoning)
    }

    private fun raiseAction(ctx: DecisionContext, multiplier: Double, confidence: Double, reasoning: String): ActionDecision {
        val raiseToAmount = (ctx.betToCall * multiplier).toInt().coerceAtLeast(ctx.suggestedSizes.minRaise)
        return ActionDecision(Action.raise(raiseToAmount), confidence, reasoning)
    }
}
