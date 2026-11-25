package com.example.runthebusiness.industries.cafe.simulation

/** Deterministic MVP demand. Prices are compared with each product's opening price,
 * not a universal €3 reference (which unfairly penalized salads and sandwiches).
 * This is a gameplay heuristic, not an empirical demand model.
 */
object CafePriceDemandRules {
    private val referencePrices = mapOf(
        "espresso_001" to 260L, "cappuccino_001" to 410L,
        "croissant_001" to 340L, "americano_001" to 330L,
        "latte_001" to 460L, "pain_chocolat_001" to 375L,
        "muffin_001" to 400L, "cheesecake_001" to 575L,
        "carrot_cake_001" to 530L, "brownie_001" to 470L,
        "ham_cheese_001" to 695L, "chicken_sandwich_001" to 880L,
        "caesar_salad_001" to 1060L, "med_salad_001" to 1000L
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
                in 11..14 -> 82
                in 15..17 -> 42
                else -> 10
            }
            "DESSERT" -> when (hour) {
                in 7..10 -> 15
                in 11..14 -> 22
                in 15..18 -> 73
                else -> 34
            }
            "PASTRY" -> when (hour) {
                in 7..10 -> 73
                in 11..14 -> 27
                else -> 44
            }
            else -> 100
        }
        return Math.floorMod("$arrivalId:$category:intent".hashCode(), 100) < chance
    }
}
