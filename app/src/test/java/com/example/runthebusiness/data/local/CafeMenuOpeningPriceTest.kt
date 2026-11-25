package com.example.runthebusiness.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CafeMenuOpeningPriceTest {
    @Test
    fun offersLowerDefaultsOnlyForPricesStillAtTheirFormerDefault() {
        assertEquals(225L, lowerOpeningPrice("espresso_001", 260L))
        assertEquals(500L, lowerOpeningPrice("cheesecake_001", 575L))
        assertNull(lowerOpeningPrice("espresso_001", 270L))
        assertNull(lowerOpeningPrice("espresso_001", 225L))
        assertNull(lowerOpeningPrice("croissant_001", 340L))
    }
}
