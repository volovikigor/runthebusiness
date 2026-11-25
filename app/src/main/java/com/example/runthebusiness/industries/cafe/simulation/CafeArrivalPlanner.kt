package com.example.runthebusiness.industries.cafe.simulation

import com.example.runthebusiness.engine.model.GameTime

/** Deterministic, bounded arrival model. No random global state, reproducible across restarts. */
object CafeArrivalPlanner {
    data class Arrival(val day: Int, val hour: Int, val slot: Int, val guests: Int) {
        val key: String get() = "arrival_${day}_${hour}_${slot}"
        val time: GameTime get() = GameTime(day, hour, slot * 15)
    }

    fun plan(from: GameTime, to: GameTime, openingHour: Int, closingHour: Int,
             seats: Int): List<Arrival> {
        require(to >= from && seats >= 0)
        require(openingHour in 0..23 && closingHour in 1..24 && openingHour < closingHour)
        require(to.totalMinutes - from.totalMinutes <= 1440) { "Advance at most one day at a time" }
        if (seats == 0) return emptyList()
        val result = mutableListOf<Arrival>()
        var tick = ((from.totalMinutes / 15L) + 1L) * 15L
        while (tick <= to.totalMinutes) {
            val day = (tick / 1440L).toInt() + 1
            val hour = ((tick % 1440L) / 60L).toInt()
            val slot = ((tick % 60L) / 15L).toInt()
            if (hour in openingHour until closingHour) {
                val busy = hour in 8..10 || hour in 12..14
                // Keep one group per 15-minute slot and some quiet off-peak slots.
                val cycle = Math.floorMod(day * 31L + hour * 7L + slot * 13L, 7L).toInt()
                val guests = if (busy) {
                    if (cycle % 3 == 0) 3 else 4
                } else if (cycle == 1) 0 else 3
                if (guests > 0) result += Arrival(day, hour, slot, minOf(guests, seats))
            }
            tick += 15L
        }
        return result
    }
}
