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

    fun openingPriceCents(productId: String): Long = when (productId) {
        "espresso_001" -> 225L
        "cappuccino_001" -> 375L
        "americano_001" -> 295L
        "latte_001" -> 420L
        "cheesecake_001" -> 500L
        else -> referencePriceCents(productId)
    }

    fun referencePriceCents(productId: String): Long = referencePrices[productId] ?: 300L

    fun discountTrafficWeight(productId: String, priceCents: Long): Int {
        val opening = openingPriceCents(productId)
        return ((opening - priceCents).coerceAtLeast(0L) * 300 / opening).coerceAtMost(100L).toInt()
    }

    data class MenuPrice(val productId: String, val category: String, val cents: Long)

    private fun acceptancePercent(productId: String, priceCents: Long): Int {
        require(priceCents > 0L)
        return (openingPriceCents(productId) * 100L / priceCents).coerceIn(10L, 100L).toInt()
    }

    fun accepts(arrivalId: String, productId: String, priceCents: Long): Boolean {
        val bucket = Math.floorMod("$arrivalId:$productId".hashCode(), 100)
        return bucket < acceptancePercent(productId, priceCents)
    }

    fun categoryPriceResistance(menu: List<MenuPrice>): Map<String, Double> =
        menu.groupBy { it.category }.mapValues { (_, options) ->
            options.map { 100 - acceptancePercent(it.productId, it.cents) }.average()
        }

    fun missedVisitPercent(resistance: Map<String, Double>, hour: Int): Int {
        val lunch = hour in 11..14
        val score = (resistance["DRINK"] ?: 0.0) * 0.65 +
            (resistance["FOOD"] ?: 0.0) * (if (lunch) 0.45 else 0.15) +
            (resistance["DESSERT"] ?: 0.0) * (if (hour >= 15) 0.30 else 0.15) +
            (resistance["PASTRY"] ?: 0.0) * (if (hour < 11) 0.30 else 0.15)
        return score.toInt().coerceAtMost(45)
    }

    fun isWeekend(day: Int): Boolean = (day - 1) % 7 >= 5

    /** Per-guest intent, stable for the same arrival across process restarts. */
    fun wantsCategory(arrivalId: String, hour: Int, category: String, day: Int = 1): Boolean {
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
        val weeklyChange = when (category) {
            "FOOD" -> if (isWeekend(day)) -10 else 4
            "DESSERT" -> if (isWeekend(day)) 12 else -5
            else -> 0
        }
        return Math.floorMod("$arrivalId:$category:intent".hashCode(), 100) < (chance + weeklyChange)
    }
}
