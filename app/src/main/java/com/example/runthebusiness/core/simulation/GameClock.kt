package com.example.runthebusiness.core.simulation

import com.example.runthebusiness.engine.model.GameTime

/** Deterministic simulation clock, independent of Android wall-clock time. */
object GameClock {
    fun advance(current: GameTime, minutes: Long): GameTime {
        require(minutes >= 0L) { "Cannot move simulation time backwards" }
        require(current.day >= 1 && current.hour in 0..23 && current.minute in 0..59) {
            "Invalid current game time"
        }
        val next = Math.addExact(current.totalMinutes, minutes)
        val day = Math.addExact(next / 1440L, 1L)
        require(day <= Int.MAX_VALUE.toLong()) { "Game day overflow" }
        return GameTime(day.toInt(), ((next % 1440L) / 60L).toInt(),
            (next % 60L).toInt())
    }
}
