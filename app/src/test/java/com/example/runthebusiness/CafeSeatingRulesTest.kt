package com.example.runthebusiness

import com.example.runthebusiness.industries.cafe.simulation.CafeSeatingRules
import org.junit.Assert.assertEquals
import org.junit.Test

class CafeSeatingRulesTest {
    @Test fun seatsReleasedAfterVisit() {
        val visits = listOf(CafeSeatingRules.Visit(480, 3, true))
        assertEquals(1, CafeSeatingRules.availableSeats(490, 4, 45, visits))
        assertEquals(4, CafeSeatingRules.availableSeats(525, 4, 45, visits))
    }

    @Test fun rejectedGuestsDoNotOccupySeats() {
        val visits = listOf(CafeSeatingRules.Visit(480, 4, false))
        assertEquals(4, CafeSeatingRules.availableSeats(490, 4, 45, visits))
    }

    @Test fun occupiedSeatsNeverGoNegative() {
        val visits = listOf(CafeSeatingRules.Visit(480, 10, true))
        assertEquals(0, CafeSeatingRules.availableSeats(490, 4, 45, visits))
    }
}
