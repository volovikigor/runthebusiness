package com.example.runthebusiness.industries.cafe.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CafeStaffCapacityRulesTest {
    @Test fun twoWorkersProvideThirtyMinutesPerSlot() {
        assertEquals(30.0, CafeStaffCapacityRules.slotCapacity(listOf(1.0, 1.0)), 0.001)
    }

    @Test fun extraWorkerIncreasesCapacity() {
        val one = CafeStaffCapacityRules.slotCapacity(listOf(1.0))
        val two = CafeStaffCapacityRules.slotCapacity(listOf(1.0, 1.0))
        assertFalse(CafeStaffCapacityRules.canServe(12.0, 8.0, one))
        assertTrue(CafeStaffCapacityRules.canServe(12.0, 8.0, two))
    }

    @Test fun inactiveStaffProvidesNoCapacity() {
        assertEquals(0.0, CafeStaffCapacityRules.slotCapacity(emptyList()), 0.001)
        assertFalse(CafeStaffCapacityRules.canServe(0.0, 2.0, 0.0))
    }
}
