package com.example.runthebusiness.industries.cafe.simulation

/** Deterministic MVP demand. Prices are compared with each product's opening price,
 * not a universal €3 reference (which unfairly penalized salads and sandwiches).
 * This is a gameplay heuristic, not an empirical demand model.
 */
object CafePriceDemandRules {
    private val referencePrices = mapOf(
        "espresso_001" to 220L, "cappuccino_001" to 350L,
        "croissant_001" to 290L, "americano_001" to 280L,
        "latte_001" to 390L, "pain_chocolat_001" to 320L,
        "muffin_001" to 340L, "cheesecake_001" to 490L,
        "carrot_cake_001" to 450L, "brownie_001" to 400L,
        "ham_cheese_001" to 590L, "chicken_sandwich_001" to 750L,
        "caesar_salad_001" to 900L, "med_salad_001" to 850L
    )

    fun referencePriceCents(productId: String): Long = referencePrices[productId] ?: 300L

    fun accepts(arrivalId: String, productId: String, priceCents: Long): Boolean {
        require(priceCents > 0L)
        val reference = referencePriceCents(productId)
        // Discounting below reference does not generate extra arrivals.
        val probabilityPercent = (reference * 100L / priceCents).coerceIn(10L, 100L)
        val bucket = Math.floorMod("$arrivalId:$productId".hashCode(), 100)
        return bucket < probabilityPercent
    }

    /** Per-group intent, stable for the same arrival across process restarts. */
    fun wantsCategory(arrivalId: String, hour: Int, category: String): Boolean {
        val chance = when (category) {
            "FOOD" -> when (hour) {
                in 11..14 -> 75
                in 15..17 -> 35
                else -> 8
            }
            "DESSERT" -> when (hour) {
                in 7..10 -> 12
                in 11..14 -> 18
                in 15..18 -> 65
                else -> 30
            }
            "PASTRY" -> when (hour) {
                in 7..10 -> 65
                in 11..14 -> 22
                else -> 38
            }
            else -> 100
        }
        return Math.floorMod("$arrivalId:$category:intent".hashCode(), 100) < chance
    }
}
