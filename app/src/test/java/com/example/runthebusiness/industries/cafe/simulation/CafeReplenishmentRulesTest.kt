package com.example.runthebusiness.industries.cafe.simulation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CafeReplenishmentRulesTest {
    @Test fun largeCakePackIsNotOrderedWhenDemandIsTooLow() {
        assertFalse(CafeReplenishmentRules.shouldAutoBuy(0.0, 20.0, 1.0, 4))
    }

    @Test fun highDemandSupportsPerishableReorder() {
        assertTrue(CafeReplenishmentRules.shouldAutoBuy(0.0, 20.0, 5.0, 4))
    }

    @Test fun existingStockReducesExpectedSellThrough() {
        assertFalse(CafeReplenishmentRules.shouldAutoBuy(3.0, 20.0, 3.5, 4))
    }

    @Test fun noObservedDemandPreventsRepeatPerishableOrder() {
        assertFalse(CafeReplenishmentRules.shouldAutoBuy(0.0, 30.0, 0.0, 3))
    }

    @Test fun nonPerishableStockStillUsesReorderThreshold() {
        assertTrue(CafeReplenishmentRules.shouldAutoBuy(0.0, 1000.0, 0.0, 0))
        assertFalse(CafeReplenishmentRules.shouldAutoBuy(500.0, 1000.0, 0.0, 0))
    }
}
