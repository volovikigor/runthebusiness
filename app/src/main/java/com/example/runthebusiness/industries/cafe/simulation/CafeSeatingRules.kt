package com.example.runthebusiness.industries.cafe.simulation

/** Simplified seating model: each arrival group stays for the same average visit duration. */
object CafeSeatingRules {
    data class Visit(val minute: Long, val guests: Int, val served: Boolean)

    fun availableSeats(now: Long, seats: Int, visitMinutes: Int, history: List<Visit>): Int {
        require(seats >= 0 && visitMinutes > 0)
        val occupied = history.filter {
            it.served && it.minute <= now && now - it.minute < visitMinutes
        }.sumOf { it.guests }
        return (seats - occupied).coerceAtLeast(0)
    }
}
