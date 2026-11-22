package com.example.runthebusiness.core.simulation

import com.example.runthebusiness.engine.model.GameTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GameClockRegressionTest {
    @Test fun fifteenMinutesAdvancesWithinDay() {
        assertEquals(GameTime(1, 9, 15), GameClock.advance(GameTime(1, 9, 0), 15L))
    }

    @Test fun advancingPastMidnightIncrementsDay() {
        assertEquals(GameTime(2, 0, 10), GameClock.advance(GameTime(1, 23, 50), 20L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTimeIsRejected() {
        GameClock.advance(GameTime(1, 8, 0), -1L)
    }
}
