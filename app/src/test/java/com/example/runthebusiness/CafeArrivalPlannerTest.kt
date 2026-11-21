package com.example.runthebusiness

import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.industries.cafe.simulation.CafeArrivalPlanner
import org.junit.Assert.*
import org.junit.Test

class CafeArrivalPlannerTest {
    @Test fun splitIntervalsYieldSameArrivals() {
        val a = GameTime(1, 8, 0)
        val b = GameTime(1, 9, 0)
        val c = GameTime(1, 10, 0)
        val together = CafeArrivalPlanner.plan(a, c, 8, 17, 24)
        val split = CafeArrivalPlanner.plan(a, b, 8, 17, 24) +
            CafeArrivalPlanner.plan(b, c, 8, 17, 24)
        assertEquals(together, split)
        assertEquals(together.map { it.key }.distinct().size, together.size)
    }

    @Test fun closedHoursDoNotGenerateGuests() {
        assertTrue(CafeArrivalPlanner.plan(GameTime(1, 18, 0), GameTime(1, 20, 0),
            8, 17, 24).isEmpty())
    }

    @Test fun zeroCapacityBlocksArrivals() {
        assertTrue(CafeArrivalPlanner.plan(GameTime(1, 8, 0), GameTime(1, 10, 0),
            8, 17, 0).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanOneDayAdvance() {
        CafeArrivalPlanner.plan(GameTime(1, 8, 0), GameTime(3, 8, 0), 8, 17, 24)
    }
}
