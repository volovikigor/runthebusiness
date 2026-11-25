package com.example.runthebusiness.industries.cafe.simulation

import com.example.runthebusiness.engine.model.GameTime

/** Deterministic, bounded arrival model. No random global state, reproducible across restarts. */
object CafeArrivalPlanner {
    data class Promotion(val productId: String, val weight: Int)

    data class Arrival(
        val day: Int, val hour: Int, val slot: Int, val guests: Int,
        val preferredProductId: String? = null
    ) {
        val key: String get() = "arrival_${day}_${hour}_${slot}" +
            if (preferredProductId == null) "" else "_promo"
        val time: GameTime get() = GameTime(day, hour, slot * 15)
    }

    fun plan(from: GameTime, to: GameTime, openingHour: Int, closingHour: Int,
             seats: Int, promotions: List<Promotion> = emptyList()): List<Arrival> {
        require(to >= from && seats >= 0)
        require(openingHour in 0..23 && closingHour in 1..24 && openingHour < closingHour)
        require(to.totalMinutes - from.totalMinutes <= 1440) { "Advance at most one day at a time" }
        if (seats == 0) return emptyList()
        val result = mutableListOf<Arrival>()
        val active = promotions.filter { it.weight > 0 }.sortedBy { it.productId }
        val totalWeight = active.sumOf { it.weight }
        fun promotedProduct(tick: Long): String? {
            if (totalWeight == 0) return null
            val draw = Math.floorMod("promotion:$tick".hashCode(), 100)
            if (draw >= totalWeight) return null
            var choice = Math.floorMod("$tick:product".hashCode(), totalWeight)
            for (promotion in active) {
                if (choice < promotion.weight) return promotion.productId
                choice -= promotion.weight
            }
            return null
        }
        var tick = ((from.totalMinutes / 15L) + 1L) * 15L
        while (tick <= to.totalMinutes) {
            val day = (tick / 1440L).toInt() + 1
            val hour = ((tick % 1440L) / 60L).toInt()
            val slot = ((tick % 60L) / 15L).toInt()
            if (hour in openingHour until closingHour) {
                val busy = if (CafePriceDemandRules.isWeekend(day)) hour in 9..11 || hour in 14..16
                    else hour in 8..10 || hour in 12..14
                // Keep the baseline at one group per slot and some quiet off-peak slots.
                val cycle = Math.floorMod(day * 31L + hour * 7L + slot * 13L, 7L).toInt()
                val guests = if (busy) {
                    if (cycle % 3 == 0) 3 else 4
                } else if (cycle == 1) 0 else 3
                if (guests > 0) result += Arrival(day, hour, slot, minOf(guests, seats))
                promotedProduct(tick)?.let { productId ->
                    val groupSize = if (busy) 3 else 2
                    result += Arrival(day, hour, slot, minOf(groupSize, seats), productId)
                }
            }
            tick += 15L
        }
        return result
    }
}
