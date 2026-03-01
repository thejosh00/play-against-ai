package com.pokerai.ai

object HandRankings {

    /**
     * All 169 unique starting hands ordered strongest to weakest (standard preflop ranking).
     * Pairs > suited > offsuit at equivalent strength. Based on widely-accepted preflop equity rankings.
     */
    val RANKED_HANDS: List<String> = listOf(
        // Tier 1: Premium pairs and AK
        "AA", "KK", "QQ", "JJ", "AKs",
        // Tier 2: Strong pairs and broadway suited
        "AQs", "TT", "AKo", "AJs", "KQs",
        // Tier 3: Medium pairs and strong suited
        "99", "ATs", "AQo", "KJs", "QJs",
        "JTs", "88", "KTs", "AJo", "QTs",
        // Tier 4: Lower pairs, suited connectors, offsuit broadway
        "A9s", "KQo", "77", "T9s", "A8s",
        "K9s", "98s", "A5s", "KJo", "A7s",
        "A4s", "Q9s", "66", "J9s", "A6s",
        "A3s", "QJo", "87s", "KTo", "A2s",
        // Tier 5: Speculative suited, offsuit aces/broadway
        "55", "T8s", "K8s", "76s", "JTo",
        "97s", "ATo", "Q8s", "K7s", "J8s",
        "65s", "44", "86s", "K6s", "54s",
        "QTo", "T7s", "K5s", "75s", "96s",
        // Tier 6: Marginal hands
        "33", "K4s", "64s", "J7s", "Q7s",
        "K3s", "85s", "53s", "K2s", "A9o",
        "43s", "Q6s", "T6s", "22", "74s",
        "J6s", "Q5s", "95s", "94s", "63s", "K9o",
        // Tier 7: Weak suited, offsuit middling
        "52s", "Q4s", "84s", "83s", "42s", "Q3s",
        "J5s", "T5s", "Q2s", "A8o", "73s",
        "J4s", "93s", "T4s", "62s", "J3s",
        "32s", "J2s", "A5o", "T3s", "A7o",
        // Tier 8: Weak offsuit
        "T2s", "82s", "92s", "A4o", "98o",
        "A6o", "A3o", "J9o", "87o", "72s",
        "Q9o", "A2o", "T9o", "76o", "K8o",
        "65o", "97o", "J8o", "54o", "86o",
        // Tier 9: Junk
        "Q8o", "T8o", "K7o", "75o", "96o",
        "K6o", "J7o", "64o", "Q7o", "53o",
        "K5o", "85o", "43o", "K4o", "T7o",
        "Q6o", "74o", "K3o", "Q5o", "K2o",
        "95o", "94o", "63o", "Q4o", "84o", "83o", "42o",
        "Q3o", "J6o", "T6o", "Q2o", "52o",
        "J5o", "73o", "J4o", "93o", "T5o",
        "32o", "J3o", "62o", "T4o", "82o",
        "J2o", "T3o", "92o", "T2o", "72o"
    )

    /**
     * All 169 unique starting hands ordered by heads-up equity vs a random hand.
     * Ace-x and King-x offsuit hands rank much higher than in multiway rankings;
     * suited connectors and small pairs drop.
     */
    val HU_RANKED_HANDS: List<String> = listOf(
        // Tier 1: Premium pairs and AK
        "AA", "KK", "QQ", "JJ", "AKs",
        // Tier 2: Strong broadways and TT-99
        "AQs", "TT", "AKo", "AJs", "KQs",
        "ATs", "AQo", "99", "KJs", "A9s",
        "KTs", "AJo", "A8s", "KQo", "A7s",
        // Tier 3: Ace-x and 88; ace-high wins unimproved HU
        "88", "ATo", "A6s", "A5s", "A4s",
        "A3s", "A2s", "QJs", "QTs", "A9o",
        "A8o", "A7o", "KJo", "K9s", "77",
        "K8s", "K7s", "A6o", "A5o", "JTs",
        // Tier 4: Ace-low offsuit, king suited/offsuit
        "QJo", "K6s", "KTo", "A4o", "K5s",
        "66", "A3o", "K4s", "A2o", "K3s",
        "K2s", "T9s", "Q9s", "J9s", "98s",
        "Q8s", "J8s", "QTo", "K9o",
        // Tier 5: Suited connectors, low pairs, king-x offsuit
        "JTo", "T8s", "87s", "55", "K8o",
        "97s", "76s", "Q7s", "K7o", "Q6s",
        "44", "K6o", "J7s", "Q5s", "65s",
        "86s", "K5o", "T7s", "Q4s", "K4o",
        "96s", "Q3s", "Q9o", "J9o", "33",
        "K3o", "54s", "Q2s", "75s", "K2o",
        // Tier 6: Marginal hands
        "T9o", "J6s", "85s", "Q8o", "64s",
        "J5s", "22", "98o", "87o", "J4s",
        "T6s", "53s", "J3s", "Q7o", "43s",
        "95s", "93s", "74s", "J2s", "76o",
        "Q6o", "T5s", "94s", "63s", "84s",
        "Q5o", "52s",
        // Tier 7: Weak hands
        "65o", "T4s", "86o", "Q4o", "42s",
        "T3s", "73s", "97o", "Q3o", "54o",
        "83s", "J8o", "T2s", "Q2o", "75o",
        "92s", "32s", "82s", "96o", "J7o",
        "T8o", "62s", "72s", "64o", "85o",
        // Tier 8: Junk
        "53o", "J6o", "43o", "T7o", "74o",
        "J5o", "95o", "63o", "84o", "J4o",
        "52o", "94o", "42o", "T6o", "J3o",
        "32o", "73o", "83o", "J2o", "T5o",
        "62o", "T4o", "93o", "72o", "82o",
        "92o", "T3o", "T2o"
    )

    // All 169 Hands ranked by the equity they have when shoving
    val PUSH_FOLD_RANKED_HANDS: List<String> = listOf(
        // Pairs dominate push/fold because guaranteed equity
        "AA", "KK", "QQ", "JJ", "TT", "AKs",
        "AKo", "99", "AQs", "AQo", "AJs",
        "88", "ATs", "AJo", "KQs", "77",
        "A9s", "ATo", "KJs", "KQo", "A8s",
        "66", "KTs", "A7s", "A9o", "KJo",
        "A5s", "A6s", "55", "A4s", "KTo",
        "A8o", "A3s", "QJs", "K9s", "A2s",
        "QTs", "A7o", "44", "K8s", "A6o",
        "A5o", "JTs", "K7s", "QJo", "A4o",
        "Q9s", "K9o", "K6s", "A3o", "33",
        "J9s", "QTo", "A2o", "K5s", "T9s",
        "K8o", "K4s", "Q8s", "K3s", "98s",
        "K7o", "J8s", "22", "K2s", "T8s",
        "K6o", "Q7s", "87s", "JTo", "Q9o",
        "K5o", "76s", "Q6s", "97s", "J9o",
        "K4o", "65s", "T9o", "86s", "K3o",
        "Q5s", "J7s", "K2o", "54s", "Q8o",
        "Q4s", "T7s", "96s", "J8o", "75s",
        "98o", "Q3s", "64s", "87o", "Q2s",
        "85s", "53s", "J6s", "T8o", "76o",
        "Q7o", "43s", "97o", "J5s", "95s",
        "74s", "Q6o", "86o", "65o", "T6s",
        "J4s", "Q5o", "63s", "84s", "54o",
        "92s", "J3s", "T7o", "96o", "Q4o",
        "52s", "75o", "93s", "42s", "J2s",
        "82s", "T5s", "Q3o", "73s", "64o",
        "85o", "Q2o", "53o", "T4s", "J7o",
        "32s", "94o", "43o", "J6o", "T3s",
        "83o", "72s", "62s", "T2s", "J5o",
        "74o", "84o", "J4o", "52o", "95o",
        "T6o", "63o", "42o", "J3o", "93o",
        "92o", "73o", "32o", "82o", "J2o",
        "T5o", "62o", "72o", "T4o", "83o",
        "T3o", "T2o"
    )

    private val indexMap: Map<String, Int> = RANKED_HANDS.withIndex().associate { (i, hand) -> hand to i }
    private val huIndexMap: Map<String, Int> = HU_RANKED_HANDS.withIndex().associate { (i, hand) -> hand to i }
    private val pushFoldIndexMap: Map<String, Int> = PUSH_FOLD_RANKED_HANDS.withIndex().associate { (i, hand) -> hand to i }

    fun indexOf(hand: String): Int =
        indexMap[hand] ?: error("Unknown hand notation: $hand")

    fun huIndexOf(hand: String): Int =
        huIndexMap[hand] ?: error("Unknown hand notation: $hand")

    fun pushFoldIndexOf(hand: String): Int =
        pushFoldIndexMap[hand] ?: error("Unknown hand notation: $hand")

    fun topN(n: Int): Set<String> {
        val clamped = n.coerceIn(0, RANKED_HANDS.size)
        return RANKED_HANDS.subList(0, clamped).toSet()
    }

}
