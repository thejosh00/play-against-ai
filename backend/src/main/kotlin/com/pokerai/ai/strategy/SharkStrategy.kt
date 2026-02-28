package com.pokerai.ai.strategy

import com.pokerai.ai.*
import com.pokerai.analysis.BoardWetness
import com.pokerai.analysis.HandStrengthTier
import com.pokerai.analysis.PotType
import com.pokerai.model.Action
import com.pokerai.model.Position

/**
 * Coded postflop strategy for the Shark archetype.
 *
 * The shark is the most skilled and adaptive archetype:
 * - Balanced between value and bluffs
 * - Uses advanced lines: check-raises, delayed c-bets, overbets, traps
 * - Maximally exploits opponent types
 * - Mixes actions intentionally to avoid predictability
 * - Thinks in ranges and adjusts frequencies
 *
 * CRITICAL DESIGN NOTE: The shark uses LOWER confidence scores than other
 * archetypes, especially for creative/non-standard plays. This pushes more
 * decisions to the LLM, which is intentional — the shark benefits most from
 * the LLM's contextual reasoning. The coded strategy handles clear-cut spots
 * and provides a strong "suggestion" for the LLM to build on.
 *
 * Instinct (1-100) for the shark controls action MIXING:
 * - Low instinct (1-33): takes the "standard" line (bet for value, fold nothing)
 * - Medium instinct (34-66): takes the "creative" line (check-raise, trap, delayed bet)
 * - High instinct (67-100): takes the "aggressive" line (overbets, bluffs, thin value)
 *
 * This is different from other archetypes where instinct controls "tight vs loose."
 * For the shark, instinct controls WHICH LINE to take, not how tight to play.
 */
class SharkStrategy : ArchetypeStrategy {

    override fun decide(ctx: DecisionContext): ActionDecision {
        val effectiveInstinct = adjustInstinct(ctx)

        return when (ctx.street) {
            Street.PREFLOP -> error("ArchetypeStrategy should not be called preflop — use PreFlopStrategy")
            Street.FLOP -> decideFlopShark(ctx, effectiveInstinct)
            Street.TURN -> decideTurnShark(ctx, effectiveInstinct)
            Street.RIVER -> decideRiverShark(ctx, effectiveInstinct)
        }
    }

    // ── Instinct adjustment ──────────────────────────────────────────

    private fun adjustInstinct(ctx: DecisionContext): Int {
        var instinct = ctx.instinct

        // Position: shark is VERY position-aware.
        if (ctx.position in listOf(Position.BTN, Position.CO)) {
            instinct += 6
        } else if (ctx.position in listOf(Position.SB, Position.UTG)) {
            instinct -= 5
        }

        // Opponent type: MAXIMUM adjustment — larger than any other archetype.
        ctx.bettorRead?.let { bettor ->
            when (bettor.playerType) {
                OpponentType.TIGHT_PASSIVE -> instinct += 15   // attack relentlessly
                OpponentType.TIGHT_AGGRESSIVE -> instinct -= 5  // respect, but look for traps
                OpponentType.LOOSE_PASSIVE -> instinct -= 12    // never bluff, thin value instead
                OpponentType.LOOSE_AGGRESSIVE -> instinct -= 8  // trap, let them hang themselves
                OpponentType.UNKNOWN -> {}
            }
        }

        // When not facing a bet — opponent adjustment for betting decisions
        if (!ctx.facingBet && ctx.opponents.isNotEmpty()) {
            val primaryOpponent = ctx.opponents.firstOrNull()
            primaryOpponent?.let { opp ->
                when (opp.playerType) {
                    OpponentType.TIGHT_PASSIVE -> instinct += 12
                    OpponentType.LOOSE_PASSIVE -> instinct -= 10
                    OpponentType.LOOSE_AGGRESSIVE -> instinct -= 8   // trap more vs LAGs
                    OpponentType.TIGHT_AGGRESSIVE -> {}
                    OpponentType.UNKNOWN -> {}
                }
            }
        }

        // Session and table image: the shark self-adjusts.
        ctx.sessionStats?.let { session ->
            when {
                session.resultBB > 40.0 -> instinct -= 3  // been active, tighten slightly
                session.resultBB < -20.0 -> instinct += 3  // need to pick up pots
            }
        }

        // Recent showdowns: shark adjusts image based on what was shown.
        ctx.sessionStats?.recentShowdowns?.let { showdowns ->
            val recent = showdowns.firstOrNull { it.handsAgo <= 5 }
            if (recent != null) {
                when (recent.event) {
                    ShowdownEvent.CALLED_AND_LOST -> instinct -= 8
                    ShowdownEvent.CALLED_AND_WON -> instinct += 5
                    ShowdownEvent.GOT_BLUFFED -> instinct += 5
                    ShowdownEvent.SAW_OPPONENT_BLUFF -> instinct += 3
                    ShowdownEvent.SAW_BIG_POT_LOSS -> {}
                }
            }
        }

        // SPR awareness
        if (ctx.spr < 2.0) {
            instinct += 5
        }

        // Follow through on prior-street aggression when not facing a bet
        if (ctx.wasAggressorThisHand && !ctx.facingBet) {
            instinct += 20
        }

        return instinct.coerceIn(1, 100)
    }

    // ── Board texture and opponent helpers ────────────────────────────

    private fun isGoodCbetBoard(ctx: DecisionContext): Boolean {
        val board = ctx.board
        return when {
            board.highCard.value >= 12 && board.wetness <= BoardWetness.SEMI_WET -> true
            board.paired && board.wetness <= BoardWetness.SEMI_WET -> true
            board.wetness == BoardWetness.DRY -> true
            else -> false
        }
    }

    /**
     * Should the shark check-raise on this flop? Check-raises work best on:
     * - Wet boards where the opponent will c-bet a wide range
     * - Against opponents who c-bet frequently (TAG, LAG)
     * - Out of position (check-raise requires acting first)
     */
    fun isGoodCheckRaiseSpot(ctx: DecisionContext): Boolean {
        if (ctx.position in listOf(Position.BTN, Position.CO)) return false
        if (!ctx.facingBet || ctx.street != Street.FLOP) return false
        if (ctx.board.wetness == BoardWetness.DRY) return false

        val opponent = ctx.bettorRead ?: return false
        return opponent.playerType in listOf(
            OpponentType.LOOSE_AGGRESSIVE,
            OpponentType.TIGHT_AGGRESSIVE
        ) || opponent.aggressionFrequency > 0.35
    }

    /**
     * Should the shark trap (check a strong hand to induce a bluff)?
     * Trapping works against aggressive opponents, not passive ones.
     */
    fun shouldTrap(ctx: DecisionContext): Boolean {
        if (ctx.potType != PotType.HEADS_UP) return false
        if (ctx.hand.tier > HandStrengthTier.STRONG) return false

        val opponent = ctx.opponents.firstOrNull() ?: return false
        return opponent.playerType in listOf(
            OpponentType.LOOSE_AGGRESSIVE,
            OpponentType.TIGHT_AGGRESSIVE
        ) || opponent.aggressionFrequency > 0.40
    }

    /**
     * Should the shark use an overbet? Overbets work when:
     * - The board is relatively dry (opponent doesn't have many strong hands)
     * - It's the river or turn (overbets on the flop are unusual)
     * - Heads-up pot
     */
    fun isGoodOverbetSpot(ctx: DecisionContext): Boolean {
        if (ctx.street == Street.FLOP) return false
        if (ctx.potType != PotType.HEADS_UP) return false
        return ctx.board.wetness <= BoardWetness.SEMI_WET
    }

    /**
     * Should the shark bluff this spot? More nuanced than the LAG version.
     * Balances bluffs and adjusts heavily by opponent type.
     */
    private fun shouldBluff(instinct: Int, ctx: DecisionContext, baseThreshold: Int): Boolean {
        var threshold = baseThreshold

        when (ctx.potType) {
            PotType.HEADS_UP -> {}
            PotType.THREE_WAY -> threshold += 20
            PotType.MULTIWAY -> threshold += 40
        }

        // Against calling stations: don't bluff
        ctx.bettorRead?.let { bettor ->
            if (bettor.playerType == OpponentType.LOOSE_PASSIVE) threshold += 25
        }
        if (!ctx.facingBet) {
            ctx.opponents.firstOrNull()?.let { opp ->
                if (opp.playerType == OpponentType.LOOSE_PASSIVE) threshold += 25
            }
        }

        // Against nits: bluff more (lower threshold)
        ctx.bettorRead?.let { bettor ->
            if (bettor.playerType == OpponentType.TIGHT_PASSIVE) threshold -= 10
        }
        if (!ctx.facingBet) {
            ctx.opponents.firstOrNull()?.let { opp ->
                if (opp.playerType == OpponentType.TIGHT_PASSIVE) threshold -= 10
            }
        }

        return instinct > threshold
    }

    private fun isGoodBarrelCard(ctx: DecisionContext): Boolean {
        return ctx.board.flushCompletedThisStreet
            || ctx.board.straightCompletedThisStreet
            || ctx.board.boardPairedThisStreet
            || ctx.board.highCard.value >= 13
    }

    // ── Sizing strategy ──────────────────────────────────────────────

    /**
     * Choose a bet size. The shark's sizing is STRATEGIC — designed to
     * create problems for opponents, not correlated with hand strength.
     *
     * Key difference from other archetypes: the shark uses the SAME sizing
     * for value and bluffs in each spot. This is what "balanced sizing" means.
     */
    private fun chooseSizing(ctx: DecisionContext, isPolarized: Boolean): Double {
        val p = ctx.profile

        return when {
            isPolarized && isGoodOverbetSpot(ctx) -> p.betSizePotFraction * 1.4
            ctx.board.wetness <= BoardWetness.DRY -> p.betSizePotFraction * 0.6
            ctx.board.wetness == BoardWetness.SEMI_WET -> p.betSizePotFraction
            else -> p.betSizePotFraction * 1.15
        }
    }

    // ── Flop handler ─────────────────────────────────────────────────

    private fun decideFlopShark(
        ctx: DecisionContext,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile
        val tier = ctx.hand.tier

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.NUTS ->
                    raiseAction(ctx, p.raiseMultiplier, 0.95, "re-raising the nuts")
                HandStrengthTier.MONSTER -> {
                    if (instinct > 50)
                        raiseAction(ctx, p.raiseMultiplier, 0.7, "re-raising for value — unbalancing slightly")
                    else callAction(ctx, 0.7, "calling raise with monster — trapping")
                }
                HandStrengthTier.STRONG -> {
                    callAction(ctx, 0.6, "calling the raise — strong hand plays well")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.hand.totalOuts >= 5 || ctx.betAsFractionOfPot <= 0.5)
                        callAction(ctx, 0.45, "calling raise — hand has potential")
                    else if (instinct > 60)
                        callAction(ctx, 0.3, "calling raise — floating for the turn")
                    else foldAction(0.5, "folding medium to raise — not enough equity")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 9)
                        callAction(ctx, 0.4, "calling raise with big draw")
                    else foldAction(0.6, "folding weak to raise")
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 85 && ctx.potType == PotType.HEADS_UP && ctx.board.wetness <= BoardWetness.DRY)
                        raiseAction(ctx, p.raiseMultiplier, 0.1, "re-bluff raise — maximum pressure")
                    else foldAction(0.7, "folding nothing to raise")
                }
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            // CHECK-RAISE decision: the shark's signature move on the flop.
            if (isGoodCheckRaiseSpot(ctx)) {
                when (tier) {
                    HandStrengthTier.NUTS -> {
                        return raiseAction(ctx, p.raiseMultiplier, 0.95, "check-raising the nuts")
                    }
                    HandStrengthTier.MONSTER -> {
                        if (instinct > 45)
                            return raiseAction(ctx, p.raiseMultiplier, 0.55, "check-raising monster for value")
                    }
                    HandStrengthTier.STRONG -> {
                        if (instinct > 65)
                            return raiseAction(ctx, p.raiseMultiplier, 0.4, "check-raising strong hand")
                    }
                    HandStrengthTier.WEAK -> {
                        if (ctx.hand.totalOuts >= 9 && instinct > 50)
                            return raiseAction(ctx, p.raiseMultiplier, 0.35, "check-raise semi-bluff with big draw")
                    }
                    else -> {} // fall through to standard facing-bet logic
                }
            }

            // Standard facing-bet logic
            return when (tier) {
                HandStrengthTier.NUTS ->
                    raiseAction(ctx, p.raiseMultiplier, 0.95, "raising the nuts for value")
                HandStrengthTier.MONSTER -> {
                    if (instinct > 60)
                        raiseAction(ctx, p.raiseMultiplier, 0.65, "raising for value")
                    else callAction(ctx, 0.75, "calling with monster — disguising strength")
                }
                HandStrengthTier.STRONG -> {
                    if (instinct > 70 && ctx.potType == PotType.HEADS_UP)
                        raiseAction(ctx, p.raiseMultiplier, 0.45, "raising for value with strong hand")
                    else callAction(ctx, 0.65, "calling with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.6)
                        callAction(ctx, 0.55, "calling with medium hand")
                    else if (ctx.hand.totalOuts >= 5)
                        callAction(ctx, 0.4, "calling — have outs")
                    else foldAction(0.45, "folding medium hand to large bet")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8)
                        callAction(ctx, 0.5, "calling with a draw")
                    else if (ctx.hand.totalOuts >= 4 && ctx.betAsFractionOfPot <= 0.4)
                        callAction(ctx, 0.35, "calling small bet with weak draw")
                    else if (instinct > 60 && ctx.potType == PotType.HEADS_UP
                        && ctx.position in listOf(Position.BTN, Position.CO))
                        callAction(ctx, 0.25, "floating in position")
                    else foldAction(0.6, "folding weak hand")
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 55 && ctx.potType == PotType.HEADS_UP
                        && ctx.position in listOf(Position.BTN, Position.CO))
                        callAction(ctx, 0.2, "floating with nothing in position")
                    else foldAction(0.7, "folding nothing")
                }
            }
        }

        // ── CHECKED TO ────────────────────────────────────────────

        // TRAP decision: check a strong hand to induce a bluff?
        if (shouldTrap(ctx) && tier <= HandStrengthTier.STRONG && tier >= HandStrengthTier.MONSTER) {
            if (instinct in 30..65) {
                return checkAction(0.4, "trapping — checking strong hand to induce opponent's bluff")
            }
        }

        // DELAYED C-BET: check flop with medium hand, planning to bet the turn.
        if (ctx.isInitiator && tier == HandStrengthTier.MEDIUM && instinct in 35..55) {
            if (ctx.board.wetness <= BoardWetness.SEMI_WET) {
                return checkAction(0.35, "delayed c-bet line — checking flop to bet turn")
            }
        }

        // Standard c-bet / bet decision
        if (ctx.isInitiator) {
            val goodBoard = isGoodCbetBoard(ctx)
            val sizing = chooseSizing(ctx, false)

            return when (tier) {
                HandStrengthTier.NUTS -> {
                    betAction(ctx, sizing, 0.95, "c-betting the nuts for value")
                }
                HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                    betAction(ctx, sizing, 0.75, "c-betting for value")
                }
                HandStrengthTier.MEDIUM -> {
                    if (goodBoard || instinct > 48) {
                        betAction(ctx, sizing, 0.55, "c-betting medium hand")
                    } else {
                        checkAction(0.45, "checking medium on bad board")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 5 && (goodBoard || instinct > 40)) {
                        betAction(ctx, sizing, 0.5, "semi-bluff c-bet")
                    } else if (goodBoard && shouldBluff(instinct, ctx, 40)) {
                        betAction(ctx, sizing, 0.35, "bluff c-bet")
                    } else {
                        checkAction(0.5, "checking weak hand")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (goodBoard && shouldBluff(instinct, ctx, 35)) {
                        betAction(ctx, sizing, 0.3, "bluff c-bet")
                    } else {
                        checkAction(0.55, "checking nothing")
                    }
                }
            }
        }

        // NOT the initiator
        return when (tier) {
            HandStrengthTier.NUTS -> {
                betAction(ctx, chooseSizing(ctx, false), 0.95, "betting the nuts for value")
            }
            HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                if (shouldTrap(ctx) && instinct in 30..60) {
                    checkAction(0.35, "trapping — checking strong hand as non-initiator")
                } else {
                    betAction(ctx, chooseSizing(ctx, false), 0.6, "betting for value")
                }
            }
            HandStrengthTier.MEDIUM -> {
                if (ctx.board.wetness >= BoardWetness.WET && instinct > 50) {
                    betAction(ctx, chooseSizing(ctx, false) * 0.75, 0.4, "betting for protection")
                } else {
                    checkAction(0.5, "checking medium")
                }
            }
            HandStrengthTier.WEAK -> {
                if (ctx.hand.totalOuts >= 8 && instinct > 45)
                    betAction(ctx, chooseSizing(ctx, false), 0.35, "betting a draw")
                else checkAction(0.55, "checking weak")
            }
            HandStrengthTier.NOTHING -> {
                if (ctx.potType == PotType.HEADS_UP && instinct > 60
                    && ctx.position in listOf(Position.BTN, Position.CO)
                    && ctx.board.wetness <= BoardWetness.SEMI_WET
                ) {
                    betAction(ctx, chooseSizing(ctx, false), 0.25, "position stab")
                } else {
                    checkAction(0.6, "checking nothing")
                }
            }
        }
    }

    // ── Turn handler ─────────────────────────────────────────────────

    private fun decideTurnShark(
        ctx: DecisionContext,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile
        val tier = ctx.hand.tier
        val goodCard = isGoodBarrelCard(ctx)

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.NUTS ->
                    raiseAction(ctx, p.raiseMultiplier, 0.95, "re-raising turn with the nuts")
                HandStrengthTier.MONSTER ->
                    if (instinct > 45) raiseAction(ctx, p.raiseMultiplier, 0.7, "re-raising turn monster")
                    else callAction(ctx, 0.75, "calling turn raise with monster")
                HandStrengthTier.STRONG ->
                    callAction(ctx, 0.55, "calling turn raise with strong hand")
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.5 && instinct > 50)
                        callAction(ctx, 0.3, "calling turn raise — price is right")
                    else foldAction(0.5, "folding medium to turn raise")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 10)
                        callAction(ctx, 0.3, "calling turn raise with big draw")
                    else foldAction(0.65, "folding to turn raise")
                }
                HandStrengthTier.NOTHING -> foldAction(0.75, "folding nothing to turn raise")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.NUTS ->
                    raiseAction(ctx, p.raiseMultiplier, 0.95, "raising turn with the nuts")
                HandStrengthTier.MONSTER -> {
                    if (instinct > 40)
                        raiseAction(ctx, p.raiseMultiplier, 0.65, "raising turn for value")
                    else callAction(ctx, 0.75, "calling turn — setting up river")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.board.wetness >= BoardWetness.WET && instinct > 55)
                        raiseAction(ctx, p.raiseMultiplier, 0.4, "raising to deny equity on wet turn")
                    else callAction(ctx, 0.6, "calling turn with strong hand")
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.55)
                        callAction(ctx, 0.5, "calling turn with medium hand")
                    else if (ctx.hand.totalOuts >= 5)
                        callAction(ctx, 0.35, "calling — have outs")
                    else foldAction(0.45, "folding medium to big turn bet")
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8 && hasDecentOdds(ctx))
                        callAction(ctx, 0.45, "calling turn draw — odds are there")
                    else if (instinct > 65 && ctx.potType == PotType.HEADS_UP && goodCard)
                        callAction(ctx, 0.2, "floating turn scare card — bluffing river")
                    else foldAction(0.6, "folding weak on turn")
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 75 && ctx.potType == PotType.HEADS_UP && goodCard)
                        callAction(ctx, 0.15, "floating turn — river bluff planned")
                    else foldAction(0.7, "folding nothing on turn")
                }
            }
        }

        // ── CHECKED TO ────────────────────────────────────────────

        // Delayed c-bet: if we checked the flop as initiator, this is the turn bet.
        val delayedCbet = ctx.isInitiator && !ctx.isAggressor
            && ctx.actions.flopAggressor == null

        if (ctx.isInitiator || ctx.isAggressor || delayedCbet) {
            val sizing = if (delayedCbet && isGoodOverbetSpot(ctx)) {
                chooseSizing(ctx, true)
            } else {
                chooseSizing(ctx, false)
            }

            return when (tier) {
                HandStrengthTier.NUTS -> {
                    betAction(ctx, sizing, 0.95, "betting the nuts on turn")
                }
                HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                    betAction(ctx, sizing, 0.7, if (delayedCbet) "delayed c-bet for value" else "double barrel for value")
                }
                HandStrengthTier.MEDIUM -> {
                    if (goodCard || instinct > 48) {
                        betAction(ctx, sizing, 0.45, "barreling turn with medium hand")
                    } else {
                        checkAction(0.45, "checking turn — bad card for medium hand")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (ctx.hand.totalOuts >= 8) {
                        betAction(ctx, sizing, 0.45, "semi-bluff turn barrel")
                    } else if (goodCard && shouldBluff(instinct, ctx, 45)) {
                        betAction(ctx, sizing, 0.25, "bluff barrel on scare card")
                    } else {
                        checkAction(0.5, "giving up on turn")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (goodCard && shouldBluff(instinct, ctx, 40)) {
                        betAction(ctx, sizing, 0.2, "bluff barrel turn scare card")
                    } else if (delayedCbet && ctx.board.wetness <= BoardWetness.SEMI_WET
                        && shouldBluff(instinct, ctx, 45)) {
                        betAction(ctx, sizing, 0.2, "delayed c-bet bluff")
                    } else {
                        checkAction(0.55, "checking nothing on turn")
                    }
                }
            }
        }

        // Not the aggressor, not delayed c-bet
        return when (tier) {
            HandStrengthTier.NUTS -> {
                betAction(ctx, chooseSizing(ctx, false), 0.95, "betting the nuts on turn")
            }
            HandStrengthTier.MONSTER, HandStrengthTier.STRONG -> {
                val oppIsAggressive = ctx.opponents.firstOrNull()?.let {
                    it.playerType in listOf(OpponentType.LOOSE_AGGRESSIVE, OpponentType.TIGHT_AGGRESSIVE)
                } ?: false

                if (oppIsAggressive && instinct in 30..55)
                    checkAction(0.35, "checking turn to induce opponent's bluff")
                else betAction(ctx, chooseSizing(ctx, false), 0.6, "betting turn for value")
            }
            HandStrengthTier.MEDIUM -> {
                if (instinct > 55 && ctx.board.wetness >= BoardWetness.WET)
                    betAction(ctx, chooseSizing(ctx, false) * 0.7, 0.35, "betting for protection")
                else checkAction(0.5, "checking turn")
            }
            else -> checkAction(0.6, "checking turn")
        }
    }

    // ── River handler ────────────────────────────────────────────────

    private fun decideRiverShark(
        ctx: DecisionContext,
        instinct: Int
    ): ActionDecision {
        val p = ctx.profile
        val tier = ctx.hand.tier
        val polarizedSpot = isGoodOverbetSpot(ctx)

        // ── FACING A RAISE ────────────────────────────────────────
        if (ctx.facingRaise) {
            return when (tier) {
                HandStrengthTier.NUTS ->
                    raiseAction(ctx, p.raiseMultiplier, 0.95, "re-raising river with the nuts")
                HandStrengthTier.MONSTER -> callAction(ctx, 0.8, "calling river raise with monster")
                HandStrengthTier.STRONG -> {
                    if (instinct > 45 && ctx.board.wetness <= BoardWetness.SEMI_WET)
                        callAction(ctx, 0.35, "hero call — board is safe, their line is suspect")
                    else foldAction(0.45, "folding to river raise")
                }
                else -> foldAction(0.65, "folding to river raise")
            }
        }

        // ── FACING A BET ──────────────────────────────────────────
        if (ctx.facingBet) {
            return when (tier) {
                HandStrengthTier.NUTS ->
                    raiseAction(ctx, p.raiseMultiplier, 0.95, "raising river with the nuts — max value")
                HandStrengthTier.MONSTER -> {
                    raiseAction(ctx, p.raiseMultiplier, 0.7, "raising river for max value")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.betAsFractionOfPot <= 1.0) {
                        callAction(ctx, 0.6, "calling river with strong hand")
                    } else {
                        if (!ctx.board.flushCompletedThisStreet && !ctx.board.straightCompletedThisStreet)
                            callAction(ctx, 0.4, "calling overbet — board is safe")
                        else foldAction(0.45, "folding to overbet — draws got there")
                    }
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.betAsFractionOfPot <= 0.4)
                        callAction(ctx, 0.5, "calling small river bet")
                    else if (ctx.betAsFractionOfPot <= 0.7 && instinct > 45)
                        callAction(ctx, 0.35, "calling river — opponent could be bluffing")
                    else foldAction(0.45, "folding medium to large river bet")
                }
                HandStrengthTier.WEAK -> {
                    if (instinct > 75 && ctx.potType == PotType.HEADS_UP
                        && ctx.hand.hasNutAdvantage && polarizedSpot)
                        raiseAction(ctx, p.raiseMultiplier * 1.2, 0.1, "river bluff raise — polarized")
                    else foldAction(0.6, "folding weak on river")
                }
                HandStrengthTier.NOTHING -> {
                    if (instinct > 80 && ctx.potType == PotType.HEADS_UP
                        && ctx.hand.hasNutAdvantage && polarizedSpot)
                        raiseAction(ctx, p.raiseMultiplier * 1.3, 0.08, "river bluff raise with nothing — ultimate move")
                    else foldAction(0.7, "folding nothing on river")
                }
            }
        }

        // ── CHECKED TO ────────────────────────────────────────────
        if (ctx.isInitiator || ctx.isAggressor) {
            return when (tier) {
                HandStrengthTier.NUTS -> {
                    val sizing = if (polarizedSpot) chooseSizing(ctx, true)
                        else chooseSizing(ctx, false) * 1.1
                    betAction(ctx, sizing, 0.95, "river value bet with the nuts")
                }
                HandStrengthTier.MONSTER -> {
                    val sizing = if (polarizedSpot && instinct > 50) {
                        chooseSizing(ctx, true)
                    } else {
                        chooseSizing(ctx, false) * 1.05
                    }
                    betAction(ctx, sizing, 0.75, "river value bet with monster")
                }
                HandStrengthTier.STRONG -> {
                    if (ctx.board.flushCompletedThisStreet || ctx.board.straightCompletedThisStreet) {
                        if (instinct > 40)
                            betAction(ctx, chooseSizing(ctx, false) * 0.6, 0.45,
                                "value betting strong on scary river — smaller sizing")
                        else checkAction(0.4, "checking strong — draws got there")
                    } else {
                        betAction(ctx, chooseSizing(ctx, false), 0.65, "river value bet")
                    }
                }
                HandStrengthTier.MEDIUM -> {
                    if (ctx.board.wetness <= BoardWetness.SEMI_WET
                        && !ctx.board.flushCompletedThisStreet
                        && !ctx.board.straightCompletedThisStreet
                        && instinct > 40
                    ) {
                        betAction(ctx, chooseSizing(ctx, false) * 0.55, 0.35,
                            "thin river value bet — shark goes for it")
                    } else {
                        checkAction(0.5, "checking medium on river")
                    }
                }
                HandStrengthTier.WEAK -> {
                    if (!ctx.hand.madeHand && ctx.potType == PotType.HEADS_UP
                        && shouldBluff(instinct, ctx, 40)
                    ) {
                        val sizing = if (polarizedSpot) chooseSizing(ctx, true)
                            else chooseSizing(ctx, false) * 0.85
                        betAction(ctx, sizing, 0.2, "river bluff — missed draw")
                    } else {
                        checkAction(0.55, "checking weak on river")
                    }
                }
                HandStrengthTier.NOTHING -> {
                    if (ctx.potType == PotType.HEADS_UP && shouldBluff(instinct, ctx, 35)) {
                        val sizing = if (polarizedSpot) chooseSizing(ctx, true)
                            else chooseSizing(ctx, false)
                        betAction(ctx, sizing, 0.15, "river bluff — polarized sizing")
                    } else {
                        checkAction(0.6, "checking nothing on river")
                    }
                }
            }
        }

        // Not the aggressor — checked to on river
        return when (tier) {
            HandStrengthTier.NUTS -> {
                betAction(ctx, chooseSizing(ctx, polarizedSpot), 0.95, "value betting river with the nuts")
            }
            HandStrengthTier.MONSTER -> {
                betAction(ctx, chooseSizing(ctx, polarizedSpot), 0.65, "value betting river")
            }
            HandStrengthTier.STRONG -> {
                betAction(ctx, chooseSizing(ctx, false), 0.55, "value betting river")
            }
            HandStrengthTier.MEDIUM -> {
                if (ctx.board.wetness <= BoardWetness.SEMI_WET && instinct > 45) {
                    betAction(ctx, chooseSizing(ctx, false) * 0.5, 0.3, "thin value on river")
                } else {
                    checkAction(0.55, "checking river")
                }
            }
            HandStrengthTier.WEAK, HandStrengthTier.NOTHING -> {
                if (instinct > 65 && ctx.potType == PotType.HEADS_UP
                    && ctx.board.wetness <= BoardWetness.SEMI_WET) {
                    betAction(ctx, chooseSizing(ctx, false), 0.2, "delayed bluff on river")
                } else {
                    checkAction(0.65, "checking river")
                }
            }
        }
    }

    // ── Helper methods ───────────────────────────────────────────────

    /**
     * Check pot odds. The shark uses EXACT pot odds — no comfort margin.
     */
    private fun hasDecentOdds(ctx: DecisionContext): Boolean {
        if (ctx.hand.totalOuts <= 0) return false
        val cardsRemaining = if (ctx.street == Street.FLOP) 47.0 else 46.0
        val drawProbability = ctx.hand.totalOuts.toDouble() / cardsRemaining
        return ctx.potOdds <= drawProbability
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
