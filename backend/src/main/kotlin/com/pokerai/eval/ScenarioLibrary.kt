package com.pokerai.eval

import com.pokerai.ai.Street
import com.pokerai.analysis.*
import com.pokerai.model.*
import com.pokerai.model.archetype.*

object ScenarioLibrary {

    fun all(): List<EvalScenario> = formatCompliance() + archetypeFidelity() +
        strategicCorrectness() + behavioralConsistency()

    fun byCategory(category: ScenarioCategory): List<EvalScenario> =
        all().filter { it.category == category }

    fun byDifficulty(difficulty: EvalDifficulty): List<EvalScenario> =
        all().filter { it.difficulty == difficulty }

    fun byTag(tag: String): List<EvalScenario> =
        all().filter { tag in it.tags }

    // ── Category 1: Format Compliance (10 scenarios) ────────────────────────

    fun formatCompliance(): List<EvalScenario> = listOf(

        // FC-01: Simplest possible decision — fold trash
        ScenarioBuilder.create("fc_01") {
            name = "Format: Simple fold"
            description = "NOTHING hand facing a bet. The correct action is obvious — tests pure format compliance."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "fold", "river")
            archetype(NitArchetype)
            hand(HandStrengthTier.NOTHING, "seven high, no draw")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-high")
            pot(size = 100, betToCall = 100, position = Position.CO)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            wrong(ActionType.CALL, ActionType.RAISE)
        },

        // FC-02: Simple check (no bet to call)
        ScenarioBuilder.create("fc_02") {
            name = "Format: Simple check"
            description = "NOTHING hand, not facing a bet. Tests that model outputs 'check' not 'fold'."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "check", "flop")
            archetype(NitArchetype)
            hand(HandStrengthTier.NOTHING, "eight high, no draw")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, A-high")
            pot(size = 30, betToCall = 0, position = Position.BB)
            headsUp()
            correct(ActionType.CHECK, weight = 1.0)
            wrong(ActionType.FOLD)
        },

        // FC-03: Simple call
        ScenarioBuilder.create("fc_03") {
            name = "Format: Simple call"
            description = "MONSTER hand facing a small bet. Calling is clearly correct."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "call", "river")
            archetype(NitArchetype)
            hand(HandStrengthTier.MONSTER, "set of aces", madeHand = true)
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-high")
            pot(size = 200, betToCall = 50, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 0.5)
            correct(ActionType.RAISE, weight = 1.0, minAmount = 100, maxAmount = 400)
            wrong(ActionType.FOLD)
        },

        // FC-04: Simple raise
        ScenarioBuilder.create("fc_04") {
            name = "Format: Simple raise"
            description = "MONSTER hand checked to on the river. Betting for value is obvious."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "raise", "river")
            archetype(LagArchetype)
            hand(HandStrengthTier.MONSTER, "nut flush")
            board(Street.RIVER, BoardWetness.DRY, "three hearts, otherwise dry")
            boardFlags(flushPossible = true)
            pot(size = 300, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 100, maxAmount = 600)
            correct(ActionType.CHECK, weight = 0.3)
            wrong(ActionType.FOLD)
        },

        // FC-05: Amount formatting
        ScenarioBuilder.create("fc_05") {
            name = "Format: Raise with correct amount field"
            description = "Situation requiring a raise. Tests that model includes numeric 'amount' in JSON."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "raise", "amount")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "top pair, ace kicker")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, K-high")
            pot(size = 60, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 20, maxAmount = 80)
            correct(ActionType.CHECK, weight = 0.3)
            wrong(ActionType.FOLD)
        },

        // FC-06: All-in formatting
        ScenarioBuilder.create("fc_06") {
            name = "Format: All-in action"
            description = "Short stack facing a big bet. Tests all_in format handling."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "all_in")
            archetype(NitArchetype)
            hand(HandStrengthTier.MONSTER, "pocket aces (overpair)")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, low")
            pot(size = 200, betToCall = 150, effectiveStack = 150, position = Position.BTN)
            headsUp()
            correct(ActionType.ALL_IN, weight = 1.0)
            correct(ActionType.CALL, weight = 0.8)
            wrong(ActionType.FOLD)
        },

        // FC-07: Reasoning field presence
        ScenarioBuilder.create("fc_07") {
            name = "Format: Includes reasoning"
            description = "Standard decision. Checks that the reasoning field is present and non-empty."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "reasoning")
            archetype(TagArchetype)
            hand(HandStrengthTier.MEDIUM, "second pair, tens")
            board(Street.TURN, BoardWetness.SEMI_WET, "semi-wet, two-tone")
            pot(size = 120, betToCall = 80, position = Position.CO)
            headsUp()
            correct(ActionType.CALL, weight = 0.7)
            correct(ActionType.FOLD, weight = 0.6)
            keywords("pair", "pot", "call", "fold")
        },

        // FC-08: Long board description (stress test)
        ScenarioBuilder.create("fc_08") {
            name = "Format: Complex situation"
            description = "Multiway pot with draws. Tests format compliance under information-heavy prompts."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("format", "complex", "multiway")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MEDIUM, "top pair weak kicker with flush draw")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false))
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, connected, 9-8-5 with two hearts")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 150, betToCall = 100, position = Position.MP)
            multiway()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.6, minAmount = 200, maxAmount = 400)
            correct(ActionType.FOLD, weight = 0.3)
        },

        // FC-09: Null amount for non-raise actions
        ScenarioBuilder.create("fc_09") {
            name = "Format: Null amount on fold"
            description = "Fold scenario. Amount field should be null, not a number."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "null_amount")
            archetype(NitArchetype)
            hand(HandStrengthTier.WEAK, "bottom pair, deuces")
            board(Street.TURN, BoardWetness.WET, "wet, two-tone, connected")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 200, betToCall = 150, position = Position.UTG)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            wrong(ActionType.RAISE)
        },

        // FC-10: Edge case — facing a check
        ScenarioBuilder.create("fc_10") {
            name = "Format: Check vs call distinction"
            description = "Not facing a bet. Tests that model uses 'check' not 'call' with amount 0."
            category = ScenarioCategory.FORMAT_COMPLIANCE
            difficulty = EvalDifficulty.EASY
            tags = setOf("format", "check_vs_call")
            archetype(CallingStationArchetype)
            hand(HandStrengthTier.WEAK, "bottom pair, fives")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, K-high")
            pot(size = 40, betToCall = 0, position = Position.BB)
            headsUp()
            correct(ActionType.CHECK, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.3, minAmount = 13, maxAmount = 40)
            wrong(ActionType.FOLD)
        }
    )

    // ── Category 2: Archetype Fidelity (15 scenarios) ───────────────────────

    fun archetypeFidelity(): List<EvalScenario> = listOf(

        // AF-01: Weak made hand facing a bet
        ScenarioBuilder.create("af_01") {
            name = "Fidelity: Weak hand facing flop bet"
            description = "Bottom pair facing half-pot flop bet. Nits fold, calling stations call, LAGs raise."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "weak_hand", "facing_bet", "flop")
            archetype(NitArchetype)
            hand(HandStrengthTier.WEAK, "bottom pair, fives")
            board(Street.FLOP, BoardWetness.SEMI_WET, "semi-wet, Q-9-5 two-tone")
            boardFlags(flushPossible = true)
            pot(size = 60, betToCall = 30, position = Position.CO)
            headsUp()
            correct(ActionType.FOLD, weight = 0.7)
            correct(ActionType.CALL, weight = 0.7)
            correct(ActionType.RAISE, weight = 0.4)
            distribution(NitArchetype, ActionDistribution(0.70..0.95, 0.0..0.05, 0.05..0.25, 0.0..0.05))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.10, 0.0..0.05, 0.80..1.0, 0.0..0.10))
            distribution(LagArchetype, ActionDistribution(0.10..0.30, 0.0..0.05, 0.30..0.50, 0.25..0.50))
            distribution(TagArchetype, ActionDistribution(0.40..0.65, 0.0..0.05, 0.30..0.50, 0.05..0.15))
            distribution(SharkArchetype, ActionDistribution(0.20..0.45, 0.0..0.05, 0.35..0.55, 0.10..0.30))
        },

        // AF-02: Strong hand checked to
        ScenarioBuilder.create("af_02") {
            name = "Fidelity: Strong hand checked to"
            description = "Top pair ace kicker checked to on flop. Tests aggression willingness."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "strong_hand", "checked_to", "flop")
            archetype(NitArchetype)
            hand(HandStrengthTier.STRONG, "top pair, ace kicker")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, K-7-2")
            pot(size = 50, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 17, maxAmount = 50)
            correct(ActionType.CHECK, weight = 0.4)
            wrong(ActionType.FOLD)
            distribution(NitArchetype, ActionDistribution(0.0..0.0, 0.25..0.45, 0.0..0.0, 0.55..0.75))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.0, 0.60..0.85, 0.0..0.0, 0.15..0.40))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.05..0.20, 0.0..0.0, 0.80..0.95))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.15..0.30, 0.0..0.0, 0.70..0.85))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.25..0.45, 0.0..0.0, 0.55..0.75))
        },

        // AF-03: Nothing on the river, checked to — bluff willingness
        ScenarioBuilder.create("af_03") {
            name = "Fidelity: Bluff spot on river"
            description = "Missed draw on river, checked to. Only LAGs and sharks bluff here."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.HARD
            tags = setOf("fidelity", "bluff", "river", "nothing")
            archetype(NitArchetype)
            hand(HandStrengthTier.NOTHING, "missed flush draw, nine high")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, missed flush, 9-high board")
            boardFlags(flushPossible = true)
            pot(size = 180, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.CHECK, weight = 0.7)
            correct(ActionType.RAISE, weight = 0.5, minAmount = 90, maxAmount = 360)
            distribution(NitArchetype, ActionDistribution(0.0..0.0, 0.90..1.0, 0.0..0.0, 0.0..0.10))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.0, 0.85..1.0, 0.0..0.0, 0.0..0.15))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.40..0.60, 0.0..0.0, 0.40..0.60))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.75..0.90, 0.0..0.0, 0.10..0.25))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.50..0.70, 0.0..0.0, 0.30..0.50))
        },

        // AF-04: Monster facing a river bet
        ScenarioBuilder.create("af_04") {
            name = "Fidelity: Monster facing river bet"
            description = "Set facing 2/3 pot river bet. All call or raise, but proportions differ."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "monster", "facing_bet", "river")
            archetype(NitArchetype)
            hand(HandStrengthTier.MONSTER, "set of jacks")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, J-7-3-Q-2")
            pot(size = 250, betToCall = 167, position = Position.BB)
            headsUp()
            correct(ActionType.CALL, weight = 0.6)
            correct(ActionType.RAISE, weight = 1.0, minAmount = 334, maxAmount = 750)
            wrong(ActionType.FOLD)
            distribution(NitArchetype, ActionDistribution(0.0..0.05, 0.0..0.0, 0.75..0.95, 0.05..0.25))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.05, 0.0..0.0, 0.75..0.95, 0.05..0.25))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.0..0.0, 0.25..0.45, 0.55..0.75))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.0..0.0, 0.40..0.60, 0.40..0.60))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.0..0.0, 0.35..0.55, 0.45..0.65))
        },

        // AF-05: Medium hand facing a large turn bet
        ScenarioBuilder.create("af_05") {
            name = "Fidelity: Medium hand vs large turn bet"
            description = "Second pair facing pot-sized turn bet. Tests fold discipline vs stubbornness."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "medium_hand", "facing_bet", "turn")
            archetype(NitArchetype)
            hand(HandStrengthTier.MEDIUM, "second pair, nines")
            board(Street.TURN, BoardWetness.SEMI_WET, "semi-wet, K-9-5-3 two-tone")
            boardFlags(flushPossible = true)
            pot(size = 150, betToCall = 150, position = Position.CO)
            headsUp()
            correct(ActionType.FOLD, weight = 0.8)
            correct(ActionType.CALL, weight = 0.5)
            distribution(NitArchetype, ActionDistribution(0.80..0.95, 0.0..0.0, 0.05..0.20, 0.0..0.05))
            distribution(CallingStationArchetype, ActionDistribution(0.05..0.20, 0.0..0.0, 0.75..0.95, 0.0..0.05))
            distribution(LagArchetype, ActionDistribution(0.25..0.45, 0.0..0.0, 0.35..0.55, 0.10..0.25))
            distribution(TagArchetype, ActionDistribution(0.55..0.75, 0.0..0.0, 0.25..0.45, 0.0..0.10))
            distribution(SharkArchetype, ActionDistribution(0.40..0.60, 0.0..0.0, 0.30..0.50, 0.05..0.20))
        },

        // AF-06: C-bet opportunity with air
        ScenarioBuilder.create("af_06") {
            name = "Fidelity: C-bet opportunity with air"
            description = "Preflop raiser with NOTHING on dry flop. Tests c-bet frequency by archetype."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "c_bet", "flop", "nothing")
            archetype(NitArchetype)
            hand(HandStrengthTier.NOTHING, "ace high, no draw")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, K-7-2")
            pot(size = 50, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.7, minAmount = 17, maxAmount = 50)
            correct(ActionType.CHECK, weight = 0.7)
            wrong(ActionType.FOLD)
            distribution(NitArchetype, ActionDistribution(0.0..0.0, 0.55..0.80, 0.0..0.0, 0.20..0.45))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.0, 0.80..0.95, 0.0..0.0, 0.05..0.20))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.10..0.25, 0.0..0.0, 0.75..0.90))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.35..0.55, 0.0..0.0, 0.45..0.65))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.30..0.50, 0.0..0.0, 0.50..0.70))
        },

        // AF-07: Facing a raise with a strong hand
        ScenarioBuilder.create("af_07") {
            name = "Fidelity: Strong hand facing a raise"
            description = "Top pair facing a flop raise. Nits fold, calling stations call, others mix."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.HARD
            tags = setOf("fidelity", "strong_hand", "facing_raise", "flop")
            archetype(NitArchetype)
            hand(HandStrengthTier.STRONG, "top pair, queen kicker")
            board(Street.FLOP, BoardWetness.SEMI_WET, "semi-wet, A-9-5 two-tone")
            boardFlags(flushPossible = true)
            pot(size = 200, betToCall = 120, position = Position.CO)
            situation(numBetsThisStreet = 2)
            headsUp()
            correct(ActionType.CALL, weight = 0.7)
            correct(ActionType.FOLD, weight = 0.5)
            correct(ActionType.RAISE, weight = 0.4, minAmount = 240, maxAmount = 500)
            distribution(NitArchetype, ActionDistribution(0.55..0.80, 0.0..0.0, 0.20..0.40, 0.0..0.05))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.10, 0.0..0.0, 0.80..0.95, 0.0..0.10))
            distribution(LagArchetype, ActionDistribution(0.05..0.20, 0.0..0.0, 0.40..0.60, 0.25..0.45))
            distribution(TagArchetype, ActionDistribution(0.30..0.50, 0.0..0.0, 0.40..0.60, 0.05..0.15))
            distribution(SharkArchetype, ActionDistribution(0.15..0.35, 0.0..0.0, 0.40..0.60, 0.15..0.30))
        },

        // AF-08: Flush draw facing a bet
        ScenarioBuilder.create("af_08") {
            name = "Fidelity: Flush draw facing a bet"
            description = "Flush draw facing half-pot flop bet. Calling stations call, LAGs raise (semi-bluff)."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "draw", "flush_draw", "flop")
            archetype(NitArchetype)
            hand(HandStrengthTier.MEDIUM, "no pair, flush draw (9 outs)")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false))
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, K-9-4 with two spades")
            boardFlags(flushPossible = true)
            pot(size = 80, betToCall = 40, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.6, minAmount = 80, maxAmount = 200)
            correct(ActionType.FOLD, weight = 0.3)
            distribution(NitArchetype, ActionDistribution(0.30..0.55, 0.0..0.0, 0.40..0.65, 0.0..0.10))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.10, 0.0..0.0, 0.85..1.0, 0.0..0.05))
            distribution(LagArchetype, ActionDistribution(0.0..0.10, 0.0..0.0, 0.30..0.50, 0.40..0.65))
            distribution(TagArchetype, ActionDistribution(0.10..0.25, 0.0..0.0, 0.55..0.75, 0.10..0.25))
            distribution(SharkArchetype, ActionDistribution(0.05..0.20, 0.0..0.0, 0.40..0.60, 0.25..0.45))
        },

        // AF-09: Multiway pot — aggression suppression
        ScenarioBuilder.create("af_09") {
            name = "Fidelity: Medium hand in multiway pot"
            description = "Top pair in a 4-way pot. Even aggressive archetypes should be cautious."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "multiway", "medium_hand", "flop")
            archetype(NitArchetype)
            hand(HandStrengthTier.MEDIUM, "top pair, weak kicker")
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, connected, T-9-7")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 120, betToCall = 0, position = Position.CO)
            situation(isInitiator = true)
            multiway()
            correct(ActionType.CHECK, weight = 0.8)
            correct(ActionType.RAISE, weight = 0.4, minAmount = 40, maxAmount = 120)
            wrong(ActionType.FOLD)
            distribution(NitArchetype, ActionDistribution(0.0..0.0, 0.85..1.0, 0.0..0.0, 0.0..0.15))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.0, 0.90..1.0, 0.0..0.0, 0.0..0.10))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.35..0.55, 0.0..0.0, 0.45..0.65))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.55..0.75, 0.0..0.0, 0.25..0.45))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.50..0.70, 0.0..0.0, 0.30..0.50))
        },

        // AF-10: River value bet opportunity
        ScenarioBuilder.create("af_10") {
            name = "Fidelity: River value bet opportunity"
            description = "STRONG hand on safe river, checked to. Tests thin value betting willingness."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "value_bet", "river", "strong_hand")
            archetype(NitArchetype)
            hand(HandStrengthTier.STRONG, "top pair, king kicker")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-K-7-3-2")
            pot(size = 140, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 47, maxAmount = 140)
            correct(ActionType.CHECK, weight = 0.4)
            wrong(ActionType.FOLD)
            distribution(NitArchetype, ActionDistribution(0.0..0.0, 0.40..0.65, 0.0..0.0, 0.35..0.60))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.0, 0.70..0.90, 0.0..0.0, 0.10..0.30))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.05..0.20, 0.0..0.0, 0.80..0.95))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.20..0.40, 0.0..0.0, 0.60..0.80))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.15..0.35, 0.0..0.0, 0.65..0.85))
        },

        // AF-11: Turn barrel decision
        ScenarioBuilder.create("af_11") {
            name = "Fidelity: Turn barrel with medium hand"
            description = "C-bet the flop, now turn. MEDIUM hand. Do you keep barreling?"
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.HARD
            tags = setOf("fidelity", "barrel", "turn", "medium_hand")
            archetype(NitArchetype)
            hand(HandStrengthTier.MEDIUM, "top pair, weak kicker")
            board(Street.TURN, BoardWetness.SEMI_WET, "semi-wet, K-8-3-J two-tone")
            boardFlags(flushPossible = true)
            pot(size = 100, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.6, minAmount = 33, maxAmount = 100)
            correct(ActionType.CHECK, weight = 0.6)
            wrong(ActionType.FOLD)
            distribution(NitArchetype, ActionDistribution(0.0..0.0, 0.70..0.90, 0.0..0.0, 0.10..0.30))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.0, 0.80..0.95, 0.0..0.0, 0.05..0.20))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.20..0.40, 0.0..0.0, 0.60..0.80))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.45..0.65, 0.0..0.0, 0.35..0.55))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.35..0.55, 0.0..0.0, 0.45..0.65))
        },

        // AF-12: Facing overbet
        ScenarioBuilder.create("af_12") {
            name = "Fidelity: Facing a river overbet"
            description = "STRONG hand facing a 1.5x pot overbet. Extreme pressure test."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.HARD
            tags = setOf("fidelity", "overbet", "river", "pressure")
            archetype(NitArchetype)
            hand(HandStrengthTier.STRONG, "top pair, ace kicker")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-8-4-2-K")
            pot(size = 200, betToCall = 300, position = Position.BB)
            headsUp()
            correct(ActionType.CALL, weight = 0.6)
            correct(ActionType.FOLD, weight = 0.6)
            distribution(NitArchetype, ActionDistribution(0.75..0.95, 0.0..0.0, 0.05..0.25, 0.0..0.05))
            distribution(CallingStationArchetype, ActionDistribution(0.10..0.25, 0.0..0.0, 0.70..0.90, 0.0..0.05))
            distribution(LagArchetype, ActionDistribution(0.20..0.40, 0.0..0.0, 0.40..0.60, 0.10..0.25))
            distribution(TagArchetype, ActionDistribution(0.45..0.65, 0.0..0.0, 0.30..0.50, 0.0..0.10))
            distribution(SharkArchetype, ActionDistribution(0.30..0.50, 0.0..0.0, 0.40..0.60, 0.05..0.15))
        },

        // AF-13: Air out of position
        ScenarioBuilder.create("af_13") {
            name = "Fidelity: Air out of position"
            description = "Complete air in early position. Donk bet vs check?"
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "nothing", "oop", "flop")
            archetype(NitArchetype)
            hand(HandStrengthTier.NOTHING, "six high, no draw", madeHand = false)
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, A-K-7")
            pot(size = 60, betToCall = 0, position = Position.BB)
            headsUp()
            correct(ActionType.CHECK, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.2, minAmount = 20, maxAmount = 60)
            wrong(ActionType.FOLD)
            distribution(NitArchetype, ActionDistribution(0.0..0.0, 0.95..1.0, 0.0..0.0, 0.0..0.05))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.0, 0.90..1.0, 0.0..0.0, 0.0..0.10))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.60..0.80, 0.0..0.0, 0.20..0.40))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.85..0.95, 0.0..0.0, 0.05..0.15))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.70..0.85, 0.0..0.0, 0.15..0.30))
        },

        // AF-14: Small river bet with medium hand
        ScenarioBuilder.create("af_14") {
            name = "Fidelity: Small river bet with medium hand"
            description = "Second pair facing tiny 1/4 pot river bet. Price is good but hand is marginal."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("fidelity", "small_bet", "river", "medium_hand")
            archetype(NitArchetype)
            hand(HandStrengthTier.MEDIUM, "second pair, eights")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-8-4-2-J")
            pot(size = 120, betToCall = 30, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 0.8)
            correct(ActionType.FOLD, weight = 0.4)
            correct(ActionType.RAISE, weight = 0.2, minAmount = 60, maxAmount = 200)
            distribution(NitArchetype, ActionDistribution(0.35..0.55, 0.0..0.0, 0.40..0.60, 0.0..0.05))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.05, 0.0..0.0, 0.90..1.0, 0.0..0.05))
            distribution(LagArchetype, ActionDistribution(0.10..0.25, 0.0..0.0, 0.45..0.65, 0.15..0.35))
            distribution(TagArchetype, ActionDistribution(0.20..0.40, 0.0..0.0, 0.55..0.75, 0.0..0.10))
            distribution(SharkArchetype, ActionDistribution(0.15..0.30, 0.0..0.0, 0.50..0.70, 0.10..0.25))
        },

        // AF-15: Monster on wet board
        ScenarioBuilder.create("af_15") {
            name = "Fidelity: Monster on wet board"
            description = "Flopped set on a wet board. Fast-play vs slow-play decision."
            category = ScenarioCategory.ARCHETYPE_FIDELITY
            difficulty = EvalDifficulty.HARD
            tags = setOf("fidelity", "monster", "wet_board", "flop")
            archetype(NitArchetype)
            hand(HandStrengthTier.MONSTER, "set of nines")
            board(Street.FLOP, BoardWetness.VERY_WET, "very wet, T-9-8 two-tone, highly connected")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 80, betToCall = 0, position = Position.CO)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 27, maxAmount = 80)
            correct(ActionType.CHECK, weight = 0.3)
            wrong(ActionType.FOLD)
            distribution(NitArchetype, ActionDistribution(0.0..0.0, 0.20..0.40, 0.0..0.0, 0.60..0.80))
            distribution(CallingStationArchetype, ActionDistribution(0.0..0.0, 0.50..0.75, 0.0..0.0, 0.25..0.50))
            distribution(LagArchetype, ActionDistribution(0.0..0.0, 0.05..0.20, 0.0..0.0, 0.80..0.95))
            distribution(TagArchetype, ActionDistribution(0.0..0.0, 0.10..0.25, 0.0..0.0, 0.75..0.90))
            distribution(SharkArchetype, ActionDistribution(0.0..0.0, 0.20..0.40, 0.0..0.0, 0.60..0.80))
        }
    )

    // ── Category 3: Strategic Correctness (40 scenarios) ────────────────────

    fun strategicCorrectness(): List<EvalScenario> = listOf(

        // ── EASY (10 scenarios) ──

        // SE-01: Fold trash to a bet
        ScenarioBuilder.create("se_01") {
            name = "Easy: Fold trash to a bet"
            description = "NOTHING hand on the river facing a pot-sized bet. Clear fold."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "fold", "river", "nothing")
            archetype(TagArchetype)
            hand(HandStrengthTier.NOTHING, "jack high, no draw")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-K-8-4-2")
            pot(size = 150, betToCall = 150, position = Position.BB)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            wrong(ActionType.CALL, ActionType.RAISE)
            keywords("fold", "nothing", "no equity")
        },

        // SE-02: Bet a monster when checked to
        ScenarioBuilder.create("se_02") {
            name = "Easy: Value bet monster on river"
            description = "Full house on the river, checked to. Betting for value is obvious."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "raise", "river", "monster", "value_bet")
            archetype(LagArchetype)
            hand(HandStrengthTier.MONSTER, "full house, aces full of kings")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-K-A-7-3")
            boardFlags(paired = true)
            pot(size = 280, betToCall = 0, position = Position.CO)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 100, maxAmount = 350)
            correct(ActionType.CHECK, weight = 0.2)
            wrong(ActionType.FOLD)
            keywords("value", "bet", "full house", "monster")
        },

        // SE-03: Don't fold when you can check
        ScenarioBuilder.create("se_03") {
            name = "Easy: Check, don't fold"
            description = "NOTHING hand not facing a bet. Must check, cannot fold when there's nothing to call."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "check", "flop", "nothing")
            archetype(NitArchetype)
            hand(HandStrengthTier.NOTHING, "ten high, no draw")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, A-Q-7")
            pot(size = 40, betToCall = 0, position = Position.BB)
            headsUp()
            correct(ActionType.CHECK, weight = 1.0)
            wrong(ActionType.FOLD)
            keywords("check", "free card")
        },

        // SE-04: Call with the nuts
        ScenarioBuilder.create("se_04") {
            name = "Easy: Call with the nuts"
            description = "Nut flush facing all-in on the river. Must call."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "call", "river", "monster", "all_in")
            archetype(TagArchetype)
            hand(HandStrengthTier.MONSTER, "nut flush, ace-high flush")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, three clubs, K-9-4-7-2")
            boardFlags(flushPossible = true)
            pot(size = 400, betToCall = 500, effectiveStack = 500, position = Position.CO)
            headsUp()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.ALL_IN, weight = 1.0)
            wrong(ActionType.FOLD)
            keywords("nuts", "call", "flush")
        },

        // SE-05: Call a small bet with a strong hand
        ScenarioBuilder.create("se_05") {
            name = "Easy: Call small bet with overpair"
            description = "Overpair facing a 1/3 pot flop bet. Easy call or raise."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "call", "flop", "strong_hand")
            archetype(SharkArchetype)
            hand(HandStrengthTier.STRONG, "overpair, pocket kings")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, T-6-3")
            pot(size = 60, betToCall = 20, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 0.7)
            correct(ActionType.RAISE, weight = 1.0, minAmount = 40, maxAmount = 120)
            wrong(ActionType.FOLD)
            keywords("overpair", "strong", "raise", "value")
        },

        // SE-06: Fold NOTHING on river facing a bet
        ScenarioBuilder.create("se_06") {
            name = "Easy: Fold missed draw on river"
            description = "Missed OESD on the river facing 2/3 pot bet. No showdown value."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "fold", "river", "nothing", "missed_draw")
            archetype(NitArchetype)
            hand(HandStrengthTier.NOTHING, "missed straight draw, eight high")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, K-J-4-2-9")
            pot(size = 180, betToCall = 120, position = Position.MP)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            wrong(ActionType.CALL)
            keywords("fold", "missed", "no equity", "draw missed")
        },

        // SE-07: Bet for value with the best possible hand
        ScenarioBuilder.create("se_07") {
            name = "Easy: Value bet with the stone cold nuts"
            description = "Straight flush on the river, checked to. Maximum value extraction."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "raise", "river", "monster", "nuts")
            archetype(LagArchetype)
            hand(HandStrengthTier.MONSTER, "straight flush")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, 5h-6h-7h-2c-Ks")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 350, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 120, maxAmount = 700)
            wrong(ActionType.FOLD)
            keywords("value", "bet", "nuts", "maximum")
        },

        // SE-08: Don't bluff a calling station
        ScenarioBuilder.create("se_08") {
            name = "Easy: Don't bluff a calling station"
            description = "NOTHING hand on river against a known calling station. Bluffing is burning money."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "check", "river", "nothing", "calling_station")
            archetype(SharkArchetype)
            hand(HandStrengthTier.NOTHING, "queen high, no draw")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-8-4-3-2")
            pot(size = 160, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.CHECK, weight = 1.0)
            wrong(ActionType.RAISE)
            keywords("check", "bluff", "calling station", "no fold equity")
        },

        // SE-09: Call getting great pot odds with a draw
        ScenarioBuilder.create("se_09") {
            name = "Easy: Pot odds call with flush draw"
            description = "Flush draw (9 outs) facing a tiny 1/5 pot bet on the flop. Math demands a call."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "call", "flop", "draw", "pot_odds")
            archetype(CallingStationArchetype)
            hand(HandStrengthTier.WEAK, "no pair, flush draw")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false))
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, Q-8-3 with two diamonds")
            boardFlags(flushPossible = true)
            pot(size = 50, betToCall = 10, position = Position.CO)
            headsUp()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.5, minAmount = 20, maxAmount = 60)
            wrong(ActionType.FOLD)
            keywords("pot odds", "draw", "flush", "call", "outs")
        },

        // SE-10: Fold a weak hand to an all-in
        ScenarioBuilder.create("se_10") {
            name = "Easy: Fold weak hand to all-in"
            description = "Bottom pair facing a massive all-in for 5x the pot on the river. Easy fold."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EASY
            tags = setOf("easy", "fold", "river", "weak_hand", "all_in")
            archetype(TagArchetype)
            hand(HandStrengthTier.WEAK, "bottom pair, threes")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-K-9-7-3")
            pot(size = 100, betToCall = 500, effectiveStack = 500, position = Position.BB)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            wrong(ActionType.CALL)
            keywords("fold", "overpay", "weak hand", "all-in")
        },

        // ── MEDIUM (15 scenarios) ──

        // SM-01: C-bet a dry board as aggressor
        ScenarioBuilder.create("sm_01") {
            name = "Medium: C-bet dry board"
            description = "Preflop raiser with medium hand on dry A-high flop, heads-up. C-bet opportunity."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "c_bet", "flop", "medium_hand")
            archetype(TagArchetype)
            hand(HandStrengthTier.MEDIUM, "middle pair, nines")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, A-7-3")
            pot(size = 50, betToCall = 0, position = Position.CO)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.8, minAmount = 17, maxAmount = 50)
            correct(ActionType.CHECK, weight = 0.6)
            wrong(ActionType.FOLD)
            keywords("c-bet", "continuation", "initiative", "position")
        },

        // SM-02: Fold weak hand to turn barrel
        ScenarioBuilder.create("sm_02") {
            name = "Medium: Fold to turn barrel"
            description = "WEAK hand (no draws) facing 2/3 pot turn bet. Villain double-barreling."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "fold", "turn", "weak_hand", "barrel")
            archetype(NitArchetype)
            hand(HandStrengthTier.WEAK, "third pair, sevens")
            board(Street.TURN, BoardWetness.SEMI_WET, "semi-wet, A-J-7-K two-tone")
            boardFlags(flushPossible = true)
            pot(size = 120, betToCall = 80, position = Position.BB)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            correct(ActionType.CALL, weight = 0.3)
            wrong(ActionType.RAISE)
            keywords("fold", "barrel", "dominated", "weak hand")
        },

        // SM-03: Call with correct pot odds (flush draw)
        ScenarioBuilder.create("sm_03") {
            name = "Medium: Pot odds call with flush draw"
            description = "Flush draw (9 outs) facing 1/4 pot flop bet. Getting great price to draw."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "call", "flop", "draw", "pot_odds")
            archetype(SharkArchetype)
            hand(HandStrengthTier.WEAK, "no pair, flush draw (9 outs)")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false))
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, K-T-4 with two hearts")
            boardFlags(flushPossible = true)
            pot(size = 80, betToCall = 20, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.6, minAmount = 40, maxAmount = 100)
            wrong(ActionType.FOLD)
            keywords("pot odds", "draw", "flush", "implied odds")
        },

        // SM-04: Value bet river with strong hand
        ScenarioBuilder.create("sm_04") {
            name = "Medium: Value bet river"
            description = "Top pair top kicker on safe river, checked to in position."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "raise", "river", "strong_hand", "value_bet")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "top pair, ace kicker")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-7-4-2-9")
            pot(size = 160, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 55, maxAmount = 160)
            correct(ActionType.CHECK, weight = 0.3)
            wrong(ActionType.FOLD)
            keywords("value", "bet", "top pair", "thin value")
        },

        // SM-05: Fold to a 3-bet without a premium hand
        ScenarioBuilder.create("sm_05") {
            name = "Medium: Fold to 3-bet"
            description = "MEDIUM hand facing a large re-raise. Not strong enough to continue."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "fold", "preflop", "medium_hand", "3bet")
            archetype(TagArchetype)
            hand(HandStrengthTier.MEDIUM, "KJ offsuit, marginal hand")
            board(Street.PREFLOP, BoardWetness.DRY)
            pot(size = 90, betToCall = 70, position = Position.UTG)
            situation(numBetsThisStreet = 2)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            correct(ActionType.CALL, weight = 0.3)
            wrong(ActionType.RAISE)
            keywords("fold", "3-bet", "dominated", "position")
        },

        // SM-06: Semi-bluff with a combo draw
        ScenarioBuilder.create("sm_06") {
            name = "Medium: Semi-bluff combo draw"
            description = "Flush draw + OESD (~15 outs) facing flop bet. Tons of equity."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "draw", "semi_bluff", "flop", "combo_draw")
            archetype(LagArchetype)
            hand(HandStrengthTier.MEDIUM, "no pair, flush draw + open-ended straight draw")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false), DrawInfo(DrawType.OESD, 6, false))
            board(Street.FLOP, BoardWetness.VERY_WET, "very wet, T-9-3 two-tone with two clubs")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 90, betToCall = 60, position = Position.CO)
            headsUp()
            correct(ActionType.CALL, weight = 0.8)
            correct(ActionType.RAISE, weight = 1.0, minAmount = 120, maxAmount = 250)
            wrong(ActionType.FOLD)
            keywords("semi-bluff", "equity", "draw", "outs", "fold equity")
        },

        // SM-07: Check back for pot control with medium hand
        ScenarioBuilder.create("sm_07") {
            name = "Medium: Pot control on turn"
            description = "Second pair on turn, checked to on wet board. Pot control is prudent."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "check", "turn", "medium_hand", "pot_control")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MEDIUM, "second pair, jacks")
            board(Street.TURN, BoardWetness.WET, "wet, two-tone, K-J-8-5 with two spades")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 110, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.CHECK, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.4, minAmount = 37, maxAmount = 110)
            wrong(ActionType.FOLD)
            keywords("pot control", "showdown value", "check back", "medium hand")
        },

        // SM-08: Fold overpair on a 4-straight board
        ScenarioBuilder.create("sm_08") {
            name = "Medium: Fold overpair on straight board"
            description = "Overpair QQ on a 4-to-a-straight river board facing a bet. Board too scary."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "fold", "river", "strong_hand", "scary_board")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "overpair, pocket queens")
            board(Street.RIVER, BoardWetness.WET, "wet, 5-6-7-8-K four to a straight")
            boardFlags(straightPossible = true, straightCompletedThisStreet = true)
            pot(size = 200, betToCall = 150, position = Position.HJ)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            correct(ActionType.CALL, weight = 0.3)
            wrong(ActionType.RAISE)
            keywords("fold", "straight", "board texture", "overpair", "scary board")
        },

        // SM-09: Value bet thin on safe river
        ScenarioBuilder.create("sm_09") {
            name = "Medium: Thin value bet on dry river"
            description = "Top pair on a dry river, checked to heads-up. Thin value bet."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "raise", "river", "strong_hand", "thin_value")
            archetype(SharkArchetype)
            hand(HandStrengthTier.STRONG, "top pair, queen kicker")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-8-3-2-5")
            pot(size = 130, betToCall = 0, position = Position.CO)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 45, maxAmount = 130)
            correct(ActionType.CHECK, weight = 0.4)
            wrong(ActionType.FOLD)
            keywords("value", "bet", "thin", "top pair", "position")
        },

        // SM-10: Fold to river bet when draw misses
        ScenarioBuilder.create("sm_10") {
            name = "Medium: Fold missed OESD on river"
            description = "Missed OESD on river, opponent bets 2/3 pot. Zero showdown value."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "fold", "river", "nothing", "missed_draw")
            archetype(NitArchetype)
            hand(HandStrengthTier.NOTHING, "missed straight draw, seven high")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, A-T-6-3-K")
            pot(size = 170, betToCall = 113, position = Position.BB)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            wrong(ActionType.CALL, ActionType.RAISE)
            keywords("fold", "missed", "draw", "no showdown value")
        },

        // SM-11: Call turn bet with strong draw
        ScenarioBuilder.create("sm_11") {
            name = "Medium: Call turn with nut flush draw"
            description = "Nut flush draw (9 outs) on the turn facing 2/3 pot bet. Close to correct price."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "call", "turn", "draw", "nut_flush_draw")
            archetype(CallingStationArchetype)
            hand(HandStrengthTier.WEAK, "no pair, nut flush draw")
            draws(DrawInfo(DrawType.NUT_FLUSH_DRAW, 9, true))
            board(Street.TURN, BoardWetness.WET, "wet, two-tone, Q-9-5-3 with three hearts")
            boardFlags(flushPossible = true)
            pot(size = 180, betToCall = 120, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.5, minAmount = 240, maxAmount = 450)
            wrong(ActionType.FOLD)
            keywords("pot odds", "implied odds", "nut draw", "flush")
        },

        // SM-12: Check-raise with a monster
        ScenarioBuilder.create("sm_12") {
            name = "Medium: Check-raise with a set"
            description = "Flopped set facing a flop bet out of position. Check-raise for value."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "raise", "flop", "monster", "check_raise")
            archetype(LagArchetype)
            hand(HandStrengthTier.MONSTER, "set of sevens")
            board(Street.FLOP, BoardWetness.SEMI_WET, "semi-wet, Q-7-4 two-tone")
            boardFlags(flushPossible = true)
            pot(size = 70, betToCall = 35, position = Position.BB)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 70, maxAmount = 175)
            correct(ActionType.CALL, weight = 0.6)
            wrong(ActionType.FOLD)
            keywords("check-raise", "set", "value", "raise", "build pot")
        },

        // SM-13: Fold in multiway pot with marginal hand
        ScenarioBuilder.create("sm_13") {
            name = "Medium: Fold marginal in multiway"
            description = "Bottom pair in a 4-way pot facing bet and call. Way too marginal."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "fold", "flop", "weak_hand", "multiway")
            archetype(NitArchetype)
            hand(HandStrengthTier.WEAK, "bottom pair, fours")
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, A-J-4 with two hearts")
            boardFlags(flushPossible = true)
            pot(size = 100, betToCall = 50, position = Position.UTG)
            multiway()
            correct(ActionType.FOLD, weight = 1.0)
            correct(ActionType.CALL, weight = 0.2)
            wrong(ActionType.RAISE)
            keywords("fold", "multiway", "marginal", "dominated")
        },

        // SM-14: Bet big on wet board for protection
        ScenarioBuilder.create("sm_14") {
            name = "Medium: Protection bet on wet flop"
            description = "Overpair on a wet flop, checked to. Need to bet big to protect."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "raise", "flop", "strong_hand", "protection")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "overpair, pocket jacks")
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, T-8-6 with two diamonds")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 70, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 35, maxAmount = 80)
            correct(ActionType.CHECK, weight = 0.2)
            wrong(ActionType.FOLD)
            keywords("protection", "bet", "draws", "wet board", "deny equity")
        },

        // SM-15: Fold top pair to river raise
        ScenarioBuilder.create("sm_15") {
            name = "Medium: Fold TPTK to river raise"
            description = "You bet river with TPTK, opponent raises. River raises are almost always strong."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("medium", "fold", "river", "strong_hand", "facing_raise")
            archetype(SharkArchetype)
            hand(HandStrengthTier.STRONG, "top pair, ace kicker")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, Q-8-3-5-2")
            pot(size = 300, betToCall = 200, position = Position.CO)
            situation(numBetsThisStreet = 2)
            headsUp()
            correct(ActionType.FOLD, weight = 0.7)
            correct(ActionType.CALL, weight = 0.5)
            wrong(ActionType.RAISE)
            keywords("fold", "river raise", "one pair", "strength")
        },

        // ── HARD (10 scenarios) ──

        // SH-01: Thin value bet on river
        ScenarioBuilder.create("sh_01") {
            name = "Hard: Thin value bet"
            description = "Second pair on a dry river, checked to. Opponent is passive — thin value?"
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "raise", "river", "medium_hand", "thin_value")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MEDIUM, "second pair, nines")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-9-4-2-6")
            pot(size = 140, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.6, minAmount = 47, maxAmount = 100)
            correct(ActionType.CHECK, weight = 0.6)
            wrong(ActionType.FOLD)
            keywords("thin value", "showdown", "position", "passive opponent")
        },

        // SH-02: Call or fold to river overbet
        ScenarioBuilder.create("sh_02") {
            name = "Hard: River overbet decision"
            description = "TPTK facing 1.5x pot overbet on the river. Polarizing bet — call or fold?"
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "call", "fold", "river", "overbet", "strong_hand")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "top pair, ace kicker")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-7-3-2-5")
            pot(size = 200, betToCall = 300, position = Position.BB)
            headsUp()
            correct(ActionType.CALL, weight = 0.5)
            correct(ActionType.FOLD, weight = 0.5)
            keywords("overbet", "polarized", "bluff catcher", "range")
        },

        // SH-03: Semi-bluff check-raise
        ScenarioBuilder.create("sh_03") {
            name = "Hard: Semi-bluff check-raise"
            description = "Flush draw + gutshot facing c-bet out of position. Semi-bluff raise is advanced."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "raise", "flop", "draw", "semi_bluff", "check_raise")
            archetype(LagArchetype)
            hand(HandStrengthTier.WEAK, "no pair, flush draw + gutshot")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false), DrawInfo(DrawType.GUTSHOT, 3, false))
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, J-9-4 with two spades")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 70, betToCall = 35, position = Position.BB)
            headsUp()
            correct(ActionType.CALL, weight = 0.6)
            correct(ActionType.RAISE, weight = 0.7, minAmount = 70, maxAmount = 175)
            correct(ActionType.FOLD, weight = 0.2)
            keywords("semi-bluff", "check-raise", "fold equity", "equity", "draws")
        },

        // SH-04: Bluff the turn when scare card comes
        ScenarioBuilder.create("sh_04") {
            name = "Hard: Bluff on scare card"
            description = "Missed draw, but turn is an ace (scare card). Aggressor can represent it."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "raise", "turn", "bluff", "scare_card")
            archetype(LagArchetype)
            hand(HandStrengthTier.NOTHING, "missed draw, ten high")
            board(Street.TURN, BoardWetness.SEMI_WET, "semi-wet, 8-5-3-A, ace on turn")
            pot(size = 100, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.6, minAmount = 50, maxAmount = 100)
            correct(ActionType.CHECK, weight = 0.5)
            wrong(ActionType.FOLD)
            keywords("bluff", "scare card", "represent", "fold equity", "ace")
        },

        // SH-05: Float in position with nothing
        ScenarioBuilder.create("sh_05") {
            name = "Hard: Float in position"
            description = "Ace high facing a weak c-bet in position. Float to take pot away later."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "call", "flop", "nothing", "float")
            archetype(SharkArchetype)
            hand(HandStrengthTier.NOTHING, "ace high, no draw")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, 9-6-2")
            pot(size = 50, betToCall = 15, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 0.6)
            correct(ActionType.FOLD, weight = 0.4)
            correct(ActionType.RAISE, weight = 0.3, minAmount = 30, maxAmount = 60)
            keywords("float", "position", "ace high", "take away", "weak bet")
        },

        // SH-06: Fold an overpair on a coordinated board
        ScenarioBuilder.create("sh_06") {
            name = "Hard: Fold overpair on scary board"
            description = "Overpair on a 3-flush, 3-straight board facing big bet. Discipline test."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "fold", "flop", "strong_hand", "scary_board")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "overpair, pocket kings")
            board(Street.FLOP, BoardWetness.VERY_WET, "very wet, Jh-Th-9h three to flush and straight")
            boardFlags(flushPossible = true, straightPossible = true, monotone = true)
            pot(size = 120, betToCall = 100, position = Position.MP)
            headsUp()
            correct(ActionType.FOLD, weight = 0.6)
            correct(ActionType.CALL, weight = 0.5)
            wrong(ActionType.RAISE)
            keywords("fold", "discipline", "wet board", "overcards", "draws heavy")
        },

        // SH-07: Triple barrel as a bluff
        ScenarioBuilder.create("sh_07") {
            name = "Hard: Triple barrel bluff"
            description = "You've bet flop and turn with air. River brick — complete the bluff or give up?"
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "raise", "river", "bluff", "triple_barrel")
            archetype(LagArchetype)
            hand(HandStrengthTier.NOTHING, "missed draw, jack high")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, K-T-4-7-2 brick river")
            pot(size = 260, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.5, minAmount = 130, maxAmount = 400)
            correct(ActionType.CHECK, weight = 0.5)
            wrong(ActionType.FOLD)
            keywords("bluff", "triple barrel", "story", "fold equity", "range")
        },

        // SH-08: Call a river bet with a bluff-catcher
        ScenarioBuilder.create("sh_08") {
            name = "Hard: Bluff-catch on river"
            description = "Middle pair on river, opponent bets 2/3 pot. Classic bluff-catching spot."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "call", "river", "medium_hand", "bluff_catcher")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MEDIUM, "middle pair, tens")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, A-T-6-3-8")
            pot(size = 220, betToCall = 147, position = Position.CO)
            headsUp()
            correct(ActionType.CALL, weight = 0.5)
            correct(ActionType.FOLD, weight = 0.5)
            keywords("bluff catcher", "range", "pot odds", "river", "polarized")
        },

        // SH-09: Delayed c-bet on turn
        ScenarioBuilder.create("sh_09") {
            name = "Hard: Delayed c-bet"
            description = "Checked flop with ace high, turn checked to again. Delayed c-bet opportunity."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "raise", "turn", "nothing", "delayed_cbet")
            archetype(TagArchetype)
            hand(HandStrengthTier.NOTHING, "ace high, backdoor nothing")
            board(Street.TURN, BoardWetness.DRY, "dry, rainbow, Q-8-3-5")
            pot(size = 60, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.6, minAmount = 20, maxAmount = 60)
            correct(ActionType.CHECK, weight = 0.5)
            wrong(ActionType.FOLD)
            keywords("delayed c-bet", "weakness", "initiative", "position")
        },

        // SH-10: Size your river bet appropriately
        ScenarioBuilder.create("sh_10") {
            name = "Hard: River bet sizing"
            description = "Nut straight on dry river, checked to. Maximize value with correct sizing."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.HARD
            tags = setOf("hard", "raise", "river", "monster", "sizing")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MONSTER, "nut straight")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, 7-8-9-2-3")
            boardFlags(straightPossible = true)
            pot(size = 300, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 200, maxAmount = 600)
            correct(ActionType.CHECK, weight = 0.1)
            wrong(ActionType.FOLD)
            keywords("sizing", "value", "overbet", "polarized", "maximize")
        },

        // ── EXPERT (5 scenarios) ──

        // SX-01: Overbet river for value
        ScenarioBuilder.create("sx_01") {
            name = "Expert: River overbet for value"
            description = "Nut straight on very dry board. Overbetting maximizes value against capped range."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EXPERT
            tags = setOf("expert", "raise", "river", "monster", "overbet")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MONSTER, "nut straight")
            board(Street.RIVER, BoardWetness.DRY, "very dry, rainbow, 4-5-6-K-A")
            boardFlags(straightPossible = true)
            pot(size = 200, betToCall = 0, effectiveStack = 800, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 200, maxAmount = 500)
            correct(ActionType.CHECK, weight = 0.1)
            wrong(ActionType.FOLD)
            keywords("overbet", "polarized", "range advantage", "nut advantage", "sizing")
        },

        // SX-02: Trap a LAG with a monster
        ScenarioBuilder.create("sx_02") {
            name = "Expert: Trap a LAG"
            description = "Flopped set against a known LAG. Slow-play to let them hang themselves."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EXPERT
            tags = setOf("expert", "check", "call", "flop", "monster", "trap")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MONSTER, "flopped set of aces")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, A-7-2")
            pot(size = 60, betToCall = 0, position = Position.BB)
            headsUp()
            correct(ActionType.CHECK, weight = 0.7)
            correct(ActionType.RAISE, weight = 0.5, minAmount = 20, maxAmount = 60)
            wrong(ActionType.FOLD)
            keywords("trap", "slow-play", "deception", "LAG", "let them bluff")
        },

        // SX-03: Bluff raise the river
        ScenarioBuilder.create("sx_03") {
            name = "Expert: River bluff raise"
            description = "Missed draw, opponent bets half pot. We have nut flush blocker — bluff raise?"
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EXPERT
            tags = setOf("expert", "raise", "river", "bluff", "blocker")
            archetype(LagArchetype)
            hand(HandStrengthTier.NOTHING, "missed draw, ace of spades (blocker)")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, 3s-7s-Ts-Kd-2h three spades")
            boardFlags(flushPossible = true)
            pot(size = 250, betToCall = 125, position = Position.CO)
            headsUp()
            correct(ActionType.FOLD, weight = 0.5)
            correct(ActionType.RAISE, weight = 0.4, minAmount = 250, maxAmount = 600)
            correct(ActionType.CALL, weight = 0.1)
            keywords("blocker", "bluff raise", "nut blocker", "polarized", "fold equity")
        },

        // SX-04: Check-call with the nuts for deception
        ScenarioBuilder.create("sx_04") {
            name = "Expert: Slow-play the nuts"
            description = "Nut flush on flop facing a bet. Flat call to disguise and let opponent keep barreling."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EXPERT
            tags = setOf("expert", "call", "flop", "monster", "slow_play", "deception")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MONSTER, "nut flush")
            board(Street.FLOP, BoardWetness.WET, "wet, monotone, 8s-5s-2s all spades")
            boardFlags(flushPossible = true, monotone = true)
            pot(size = 70, betToCall = 50, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 0.6)
            correct(ActionType.RAISE, weight = 0.6, minAmount = 100, maxAmount = 200)
            wrong(ActionType.FOLD)
            keywords("slow-play", "deception", "disguise", "trap", "let them barrel")
        },

        // SX-05: Fold a strong hand to a river check-raise
        ScenarioBuilder.create("sx_05") {
            name = "Expert: Fold top two to river check-raise"
            description = "You bet river with top two pair, opponent check-raises 3x. River check-raises are almost always the nuts."
            category = ScenarioCategory.STRATEGIC_CORRECTNESS
            difficulty = EvalDifficulty.EXPERT
            tags = setOf("expert", "fold", "river", "strong_hand", "check_raise")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "top two pair, aces and kings")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, A-K-7-4-9 two-tone")
            boardFlags(flushPossible = true)
            pot(size = 450, betToCall = 400, position = Position.BTN)
            situation(numBetsThisStreet = 2)
            headsUp()
            correct(ActionType.FOLD, weight = 0.6)
            correct(ActionType.CALL, weight = 0.4)
            wrong(ActionType.RAISE)
            keywords("fold", "check-raise", "river", "strength", "discipline", "top two")
        }
    )

    // ── Category 4: Behavioral Consistency (20 scenarios) ───────────────────

    fun behavioralConsistency(): List<EvalScenario> = listOf(

        // BC-01: Based on SE-01 (Fold trash to a bet)
        ScenarioBuilder.create("bc_01") {
            name = "Consistency: Fold trash to a bet"
            description = "NOTHING hand on the river facing a pot-sized bet. Clear fold."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.EASY
            tags = setOf("consistency", "fold", "river", "nothing")
            archetype(TagArchetype)
            hand(HandStrengthTier.NOTHING, "jack high, no draw")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-K-8-4-2")
            pot(size = 150, betToCall = 150, position = Position.BB)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            wrong(ActionType.CALL, ActionType.RAISE)
            keywords("fold", "nothing", "no equity")
        },

        // BC-02: Based on SE-02 (Value bet monster on river)
        ScenarioBuilder.create("bc_02") {
            name = "Consistency: Value bet monster on river"
            description = "Full house on the river, checked to. Betting for value is obvious."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.EASY
            tags = setOf("consistency", "raise", "river", "monster", "value_bet")
            archetype(LagArchetype)
            hand(HandStrengthTier.MONSTER, "full house, aces full of kings")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, A-K-A-7-3")
            boardFlags(paired = true)
            pot(size = 280, betToCall = 0, position = Position.CO)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 100, maxAmount = 350)
            correct(ActionType.CHECK, weight = 0.2)
            wrong(ActionType.FOLD)
            keywords("value", "bet", "full house", "monster")
        },

        // BC-03: Based on SE-05 (Call small bet with overpair)
        ScenarioBuilder.create("bc_03") {
            name = "Consistency: Call small bet with overpair"
            description = "Overpair facing a 1/3 pot flop bet. Easy call or raise."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.EASY
            tags = setOf("consistency", "call", "flop", "strong_hand")
            archetype(SharkArchetype)
            hand(HandStrengthTier.STRONG, "overpair, pocket kings")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, T-6-3")
            pot(size = 60, betToCall = 20, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 0.7)
            correct(ActionType.RAISE, weight = 1.0, minAmount = 40, maxAmount = 120)
            wrong(ActionType.FOLD)
            keywords("overpair", "strong", "raise", "value")
        },

        // BC-04: Based on SE-09 (Pot odds call with flush draw)
        ScenarioBuilder.create("bc_04") {
            name = "Consistency: Pot odds call with flush draw"
            description = "Flush draw (9 outs) facing a tiny 1/5 pot bet on the flop. Math demands a call."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.EASY
            tags = setOf("consistency", "call", "flop", "draw", "pot_odds")
            archetype(CallingStationArchetype)
            hand(HandStrengthTier.WEAK, "no pair, flush draw")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false))
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, Q-8-3 with two diamonds")
            boardFlags(flushPossible = true)
            pot(size = 50, betToCall = 10, position = Position.CO)
            headsUp()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.5, minAmount = 20, maxAmount = 60)
            wrong(ActionType.FOLD)
            keywords("pot odds", "draw", "flush", "call", "outs")
        },

        // BC-05: Based on SM-01 (C-bet dry board)
        ScenarioBuilder.create("bc_05") {
            name = "Consistency: C-bet dry board"
            description = "Preflop raiser with medium hand on dry A-high flop, heads-up. C-bet opportunity."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("consistency", "c_bet", "flop", "medium_hand")
            archetype(TagArchetype)
            hand(HandStrengthTier.MEDIUM, "middle pair, nines")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, A-7-3")
            pot(size = 50, betToCall = 0, position = Position.CO)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.8, minAmount = 17, maxAmount = 50)
            correct(ActionType.CHECK, weight = 0.6)
            wrong(ActionType.FOLD)
            keywords("c-bet", "continuation", "initiative", "position")
        },

        // BC-06: Based on SM-03 (Pot odds call with flush draw)
        ScenarioBuilder.create("bc_06") {
            name = "Consistency: Pot odds call with flush draw"
            description = "Flush draw (9 outs) facing 1/4 pot flop bet. Getting great price to draw."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("consistency", "call", "flop", "draw", "pot_odds")
            archetype(SharkArchetype)
            hand(HandStrengthTier.WEAK, "no pair, flush draw (9 outs)")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false))
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, K-T-4 with two hearts")
            boardFlags(flushPossible = true)
            pot(size = 80, betToCall = 20, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.6, minAmount = 40, maxAmount = 100)
            wrong(ActionType.FOLD)
            keywords("pot odds", "draw", "flush", "implied odds")
        },

        // BC-07: Based on SM-04 (Value bet river)
        ScenarioBuilder.create("bc_07") {
            name = "Consistency: Value bet river"
            description = "Top pair top kicker on safe river, checked to in position."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("consistency", "raise", "river", "strong_hand", "value_bet")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "top pair, ace kicker")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-7-4-2-9")
            pot(size = 160, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 55, maxAmount = 160)
            correct(ActionType.CHECK, weight = 0.3)
            wrong(ActionType.FOLD)
            keywords("value", "bet", "top pair", "thin value")
        },

        // BC-08: Based on SM-06 (Semi-bluff combo draw)
        ScenarioBuilder.create("bc_08") {
            name = "Consistency: Semi-bluff combo draw"
            description = "Flush draw + OESD (~15 outs) facing flop bet. Tons of equity."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("consistency", "draw", "semi_bluff", "flop", "combo_draw")
            archetype(LagArchetype)
            hand(HandStrengthTier.MEDIUM, "no pair, flush draw + open-ended straight draw")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false), DrawInfo(DrawType.OESD, 6, false))
            board(Street.FLOP, BoardWetness.VERY_WET, "very wet, T-9-3 two-tone with two clubs")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 90, betToCall = 60, position = Position.CO)
            headsUp()
            correct(ActionType.CALL, weight = 0.8)
            correct(ActionType.RAISE, weight = 1.0, minAmount = 120, maxAmount = 250)
            wrong(ActionType.FOLD)
            keywords("semi-bluff", "equity", "draw", "outs", "fold equity")
        },

        // BC-09: Based on SM-07 (Pot control on turn)
        ScenarioBuilder.create("bc_09") {
            name = "Consistency: Pot control on turn"
            description = "Second pair on turn, checked to on wet board. Pot control is prudent."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("consistency", "check", "turn", "medium_hand", "pot_control")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MEDIUM, "second pair, jacks")
            board(Street.TURN, BoardWetness.WET, "wet, two-tone, K-J-8-5 with two spades")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 110, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.CHECK, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.4, minAmount = 37, maxAmount = 110)
            wrong(ActionType.FOLD)
            keywords("pot control", "showdown value", "check back", "medium hand")
        },

        // BC-10: Based on SM-08 (Fold overpair on straight board)
        ScenarioBuilder.create("bc_10") {
            name = "Consistency: Fold overpair on straight board"
            description = "Overpair QQ on a 4-to-a-straight river board facing a bet. Board too scary."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("consistency", "fold", "river", "strong_hand", "scary_board")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "overpair, pocket queens")
            board(Street.RIVER, BoardWetness.WET, "wet, 5-6-7-8-K four to a straight")
            boardFlags(straightPossible = true, straightCompletedThisStreet = true)
            pot(size = 200, betToCall = 150, position = Position.HJ)
            headsUp()
            correct(ActionType.FOLD, weight = 1.0)
            correct(ActionType.CALL, weight = 0.3)
            wrong(ActionType.RAISE)
            keywords("fold", "straight", "board texture", "overpair", "scary board")
        },

        // BC-11: Based on SM-11 (Call turn with nut flush draw)
        ScenarioBuilder.create("bc_11") {
            name = "Consistency: Call turn with nut flush draw"
            description = "Nut flush draw (9 outs) on the turn facing 2/3 pot bet. Close to correct price."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("consistency", "call", "turn", "draw", "nut_flush_draw")
            archetype(CallingStationArchetype)
            hand(HandStrengthTier.WEAK, "no pair, nut flush draw")
            draws(DrawInfo(DrawType.NUT_FLUSH_DRAW, 9, true))
            board(Street.TURN, BoardWetness.WET, "wet, two-tone, Q-9-5-3 with three hearts")
            boardFlags(flushPossible = true)
            pot(size = 180, betToCall = 120, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 1.0)
            correct(ActionType.RAISE, weight = 0.5, minAmount = 240, maxAmount = 450)
            wrong(ActionType.FOLD)
            keywords("pot odds", "implied odds", "nut draw", "flush")
        },

        // BC-12: Based on SM-12 (Check-raise with a set)
        ScenarioBuilder.create("bc_12") {
            name = "Consistency: Check-raise with a set"
            description = "Flopped set facing a flop bet out of position. Check-raise for value."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.MEDIUM
            tags = setOf("consistency", "raise", "flop", "monster", "check_raise")
            archetype(LagArchetype)
            hand(HandStrengthTier.MONSTER, "set of sevens")
            board(Street.FLOP, BoardWetness.SEMI_WET, "semi-wet, Q-7-4 two-tone")
            boardFlags(flushPossible = true)
            pot(size = 70, betToCall = 35, position = Position.BB)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 70, maxAmount = 175)
            correct(ActionType.CALL, weight = 0.6)
            wrong(ActionType.FOLD)
            keywords("check-raise", "set", "value", "raise", "build pot")
        },

        // BC-13: Based on SH-01 (Thin value bet)
        ScenarioBuilder.create("bc_13") {
            name = "Consistency: Thin value bet"
            description = "Second pair on a dry river, checked to. Opponent is passive — thin value?"
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.HARD
            tags = setOf("consistency", "raise", "river", "medium_hand", "thin_value")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MEDIUM, "second pair, nines")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-9-4-2-6")
            pot(size = 140, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.6, minAmount = 47, maxAmount = 100)
            correct(ActionType.CHECK, weight = 0.6)
            wrong(ActionType.FOLD)
            keywords("thin value", "showdown", "position", "passive opponent")
        },

        // BC-14: Based on SH-02 (River overbet decision)
        ScenarioBuilder.create("bc_14") {
            name = "Consistency: River overbet decision"
            description = "TPTK facing 1.5x pot overbet on the river. Polarizing bet — call or fold?"
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.HARD
            tags = setOf("consistency", "call", "fold", "river", "overbet", "strong_hand")
            archetype(TagArchetype)
            hand(HandStrengthTier.STRONG, "top pair, ace kicker")
            board(Street.RIVER, BoardWetness.DRY, "dry, rainbow, K-7-3-2-5")
            pot(size = 200, betToCall = 300, position = Position.BB)
            headsUp()
            correct(ActionType.CALL, weight = 0.5)
            correct(ActionType.FOLD, weight = 0.5)
            keywords("overbet", "polarized", "bluff catcher", "range")
        },

        // BC-15: Based on SH-03 (Semi-bluff check-raise)
        ScenarioBuilder.create("bc_15") {
            name = "Consistency: Semi-bluff check-raise"
            description = "Flush draw + gutshot facing c-bet out of position. Semi-bluff raise is advanced."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.HARD
            tags = setOf("consistency", "raise", "flop", "draw", "semi_bluff", "check_raise")
            archetype(LagArchetype)
            hand(HandStrengthTier.WEAK, "no pair, flush draw + gutshot")
            draws(DrawInfo(DrawType.FLUSH_DRAW, 9, false), DrawInfo(DrawType.GUTSHOT, 3, false))
            board(Street.FLOP, BoardWetness.WET, "wet, two-tone, J-9-4 with two spades")
            boardFlags(flushPossible = true, straightPossible = true)
            pot(size = 70, betToCall = 35, position = Position.BB)
            headsUp()
            correct(ActionType.CALL, weight = 0.6)
            correct(ActionType.RAISE, weight = 0.7, minAmount = 70, maxAmount = 175)
            correct(ActionType.FOLD, weight = 0.2)
            keywords("semi-bluff", "check-raise", "fold equity", "equity", "draws")
        },

        // BC-16: Based on SH-05 (Float in position)
        ScenarioBuilder.create("bc_16") {
            name = "Consistency: Float in position"
            description = "Ace high facing a weak c-bet in position. Float to take pot away later."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.HARD
            tags = setOf("consistency", "call", "flop", "nothing", "float")
            archetype(SharkArchetype)
            hand(HandStrengthTier.NOTHING, "ace high, no draw")
            board(Street.FLOP, BoardWetness.DRY, "dry, rainbow, 9-6-2")
            pot(size = 50, betToCall = 15, position = Position.BTN)
            headsUp()
            correct(ActionType.CALL, weight = 0.6)
            correct(ActionType.FOLD, weight = 0.4)
            correct(ActionType.RAISE, weight = 0.3, minAmount = 30, maxAmount = 60)
            keywords("float", "position", "ace high", "take away", "weak bet")
        },

        // BC-17: Based on SH-08 (Bluff-catch on river)
        ScenarioBuilder.create("bc_17") {
            name = "Consistency: Bluff-catch on river"
            description = "Middle pair on river, opponent bets 2/3 pot. Classic bluff-catching spot."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.HARD
            tags = setOf("consistency", "call", "river", "medium_hand", "bluff_catcher")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MEDIUM, "middle pair, tens")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, A-T-6-3-8")
            pot(size = 220, betToCall = 147, position = Position.CO)
            headsUp()
            correct(ActionType.CALL, weight = 0.5)
            correct(ActionType.FOLD, weight = 0.5)
            keywords("bluff catcher", "range", "pot odds", "river", "polarized")
        },

        // BC-18: Based on SH-09 (Delayed c-bet)
        ScenarioBuilder.create("bc_18") {
            name = "Consistency: Delayed c-bet"
            description = "Checked flop with ace high, turn checked to again. Delayed c-bet opportunity."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.HARD
            tags = setOf("consistency", "raise", "turn", "nothing", "delayed_cbet")
            archetype(TagArchetype)
            hand(HandStrengthTier.NOTHING, "ace high, backdoor nothing")
            board(Street.TURN, BoardWetness.DRY, "dry, rainbow, Q-8-3-5")
            pot(size = 60, betToCall = 0, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 0.6, minAmount = 20, maxAmount = 60)
            correct(ActionType.CHECK, weight = 0.5)
            wrong(ActionType.FOLD)
            keywords("delayed c-bet", "weakness", "initiative", "position")
        },

        // BC-19: Based on SX-01 (River overbet for value)
        ScenarioBuilder.create("bc_19") {
            name = "Consistency: River overbet for value"
            description = "Nut straight on very dry board. Overbetting maximizes value against capped range."
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.EXPERT
            tags = setOf("consistency", "raise", "river", "monster", "overbet")
            archetype(SharkArchetype)
            hand(HandStrengthTier.MONSTER, "nut straight")
            board(Street.RIVER, BoardWetness.DRY, "very dry, rainbow, 4-5-6-K-A")
            boardFlags(straightPossible = true)
            pot(size = 200, betToCall = 0, effectiveStack = 800, position = Position.BTN)
            situation(isInitiator = true)
            headsUp()
            correct(ActionType.RAISE, weight = 1.0, minAmount = 200, maxAmount = 500)
            correct(ActionType.CHECK, weight = 0.1)
            wrong(ActionType.FOLD)
            keywords("overbet", "polarized", "range advantage", "nut advantage", "sizing")
        },

        // BC-20: Based on SX-03 (River bluff raise)
        ScenarioBuilder.create("bc_20") {
            name = "Consistency: River bluff raise"
            description = "Missed draw, opponent bets half pot. We have nut flush blocker — bluff raise?"
            category = ScenarioCategory.BEHAVIORAL_CONSISTENCY
            difficulty = EvalDifficulty.EXPERT
            tags = setOf("consistency", "raise", "river", "bluff", "blocker")
            archetype(LagArchetype)
            hand(HandStrengthTier.NOTHING, "missed draw, ace of spades (blocker)")
            board(Street.RIVER, BoardWetness.SEMI_WET, "semi-wet, 3s-7s-Ts-Kd-2h three spades")
            boardFlags(flushPossible = true)
            pot(size = 250, betToCall = 125, position = Position.CO)
            headsUp()
            correct(ActionType.FOLD, weight = 0.5)
            correct(ActionType.RAISE, weight = 0.4, minAmount = 250, maxAmount = 600)
            correct(ActionType.CALL, weight = 0.1)
            keywords("blocker", "bluff raise", "nut blocker", "polarized", "fold equity")
        }
    )
}
