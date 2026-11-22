package com.example.runthebusiness.industries.cafe.simulation

import org.junit.Assert.assertEquals
import org.junit.Test

class CafeSeatingRulesRegressionTest {
    @Test fun occupiedSeatsExpireAfterVisitDuration() {
        val visits = listOf(CafeSeatingRules.Visit(minute = 100L, guests = 3, served = true))
        assertEquals(5, CafeSeatingRules.availableSeats(144L, 8, 45, visits))
        assertEquals(8, CafeSeatingRules.availableSeats(145L, 8, 45, visits))
    }

    @Test fun rejectedGroupsDoNotOccupySeats() {
        val visits = listOf(CafeSeatingRules.Visit(minute = 100L, guests = 3, served = false))
        assertEquals(8, CafeSeatingRules.availableSeats(110L, 8, 45, visits))
    }

    @Test fun capacityNeverBecomesNegative() {
        val visits = listOf(CafeSeatingRules.Visit(minute = 100L, guests = 9, served = true))
        assertEquals(0, CafeSeatingRules.availableSeats(110L, 8, 45, visits))
    }
}
