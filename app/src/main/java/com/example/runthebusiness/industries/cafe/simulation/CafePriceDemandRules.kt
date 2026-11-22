package com.example.runthebusiness.industries.cafe.simulation

/** MVP deterministic purchase conversion, not a calibrated demand forecast.
 * A price above the reference price lowers purchase probability; cheaper items
 * cannot create additional arrivals in this first version.
 */
object CafePriceDemandRules {
    fun referencePriceCents(productId: String): Long = when (productId) {
        "espresso_001" -> 220L
        "cappuccino_001" -> 350L
        "croissant_001" -> 290L
        else -> 300L
    }

    fun accepts(arrivalId: String, productId: String, priceCents: Long): Boolean {
        require(priceCents > 0L)
        val reference = referencePriceCents(productId)
        val probabilityPercent = (reference * 100L / priceCents).coerceIn(10L, 100L)
        // Stable across restarts; use a non-negative remainder even for MIN_VALUE hashes.
        val bucket = Math.floorMod("$arrivalId:$productId".hashCode(), 100)
        return bucket < probabilityPercent
    }
}
