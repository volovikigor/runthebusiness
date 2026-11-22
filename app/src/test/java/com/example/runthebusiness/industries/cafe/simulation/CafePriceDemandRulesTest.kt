package com.example.runthebusiness.industries.cafe.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CafePriceDemandRulesTest {
    @Test fun referencePriceAlwaysConverts() {
        assertTrue(CafePriceDemandRules.accepts("arrival_1", "espresso_001", 220L))
    }

    @Test fun sameArrivalAndPriceAlwaysHaveSameOutcome() {
        val first = CafePriceDemandRules.accepts("arrival_99", "cappuccino_001", 700L)
        assertEquals(first, CafePriceDemandRules.accepts("arrival_99", "cappuccino_001", 700L))
    }

    @Test fun raisingPriceNeverImprovesConversionForSameArrival() {
        for (i in 1..100) {
            val id = "arrival_$i"
            val expensive = CafePriceDemandRules.accepts(id, "croissant_001", 800L)
            val cheaper = CafePriceDemandRules.accepts(id, "croissant_001", 290L)
            assertTrue(!expensive || cheaper)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPriceIsRejected() {
        CafePriceDemandRules.accepts("arrival_1", "espresso_001", 0L)
    }
}
