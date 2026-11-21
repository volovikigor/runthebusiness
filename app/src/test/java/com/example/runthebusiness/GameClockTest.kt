package com.example.runthebusiness

import com.example.runthebusiness.core.simulation.GameClock
import com.example.runthebusiness.engine.model.GameTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GameClockTest {
    @Test fun crossesMidnight() {
        val result = GameClock.advance(GameTime(1, 23, 50), 25L)
        assertEquals(2, result.day)
        assertEquals(0, result.hour)
        assertEquals(15, result.minute)
    }

    @Test fun advancesMultipleDays() {
        val result = GameClock.advance(GameTime(3, 8, 0), 2880L)
        assertEquals(5, result.day)
        assertEquals(8, result.hour)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeAdvance() {
        GameClock.advance(GameTime(), -1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTime() {
        GameClock.advance(GameTime(1, 24, 0), 10L)
    }
}
