package com.example.runthebusiness.industries.cafe.simulation

import com.example.runthebusiness.engine.model.GameTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CafeArrivalPlannerRegressionTest {
    @Test fun closedHoursProduceNoVisitors() {
        val arrivals = CafeArrivalPlanner.plan(GameTime(1, 17, 0), GameTime(1, 19, 0), 8, 17, 24)
        assertTrue(arrivals.isEmpty())
    }

    @Test fun openHoursProduceReproducibleArrivals() {
        val from = GameTime(1, 8, 0)
        val to = GameTime(1, 10, 0)
        val first = CafeArrivalPlanner.plan(from, to, 8, 17, 24)
        val second = CafeArrivalPlanner.plan(from, to, 8, 17, 24)
        assertEquals(first, second)
        assertTrue(first.isNotEmpty())
        assertTrue(first.all { it.hour in 8 until 17 && it.guests in 1..3 })
        assertEquals(first.size, first.map { it.key }.distinct().size)
    }

    @Test fun zeroSeatsPreventsArrivals() {
        assertTrue(CafeArrivalPlanner.plan(GameTime(1, 8, 0), GameTime(1, 9, 0), 8, 17, 0).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun longerThanOneDayIsRejected() {
        CafeArrivalPlanner.plan(GameTime(1, 8, 0), GameTime(2, 8, 15), 8, 17, 24)
    }
}
