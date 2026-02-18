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

    private val indexMap: Map<String, Int> = RANKED_HANDS.withIndex().associate { (i, hand) -> hand to i }

    fun indexOf(hand: String): Int =
        indexMap[hand] ?: error("Unknown hand notation: $hand")

    fun topN(n: Int): Set<String> {
        val clamped = n.coerceIn(0, RANKED_HANDS.size)
        return RANKED_HANDS.subList(0, clamped).toSet()
    }

}
