package com.example.runthebusiness.industries.cafe.simulation

/** Demand-aware safety gate for auto-purchasing supplier-sized packs.
 * Manual purchases remain unrestricted. Never extrapolate spoiled stock as demand.
 */
object CafeReplenishmentRules {
    fun expectedSellThrough(
        currentStock: Double, packQuantity: Double,
        dailyConsumed: Double, shelfLifeDays: Int
    ): Double {
        if (shelfLifeDays <= 0) return 1.0
        if (packQuantity <= 0.0) return 0.0
        val expectedUsage = dailyConsumed.coerceAtLeast(0.0) * shelfLifeDays
        return ((expectedUsage - currentStock.coerceAtLeast(0.0)) / packQuantity)
            .coerceIn(0.0, 1.0)
    }

    fun shouldAutoBuy(
        currentStock: Double, packQuantity: Double,
        dailyConsumed: Double, shelfLifeDays: Int
    ): Boolean {
        if (currentStock > packQuantity * 0.2) return false
        // Require at least 70% expected consumption before expiration.
        // With no observed sales, perishable packs are not auto-bought.
        return shelfLifeDays <= 0 ||
            expectedSellThrough(currentStock, packQuantity, dailyConsumed, shelfLifeDays) >= 0.70
    }
}
