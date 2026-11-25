package com.example.runthebusiness.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CafePriceInsightsTest {
    @Test
    fun comparesOnlyCompletedDaysAndSkipsTheMixedPriceDay() {
        assertNull(comparablePriceWindows(today = 11, changeDay = 10, previousChangeDay = null))
        assertEquals((9..9) to (11..11), comparablePriceWindows(12, 10, null))
        assertEquals((3..9) to (11..17), comparablePriceWindows(28, 10, null))
    }

    @Test
    fun stopsBeforeAnEarlierChange() {
        assertEquals((8..9) to (11..12), comparablePriceWindows(20, 10, 7))
        assertNull(comparablePriceWindows(20, 10, 9))
    }
}
