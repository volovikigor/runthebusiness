package com.example.runthebusiness.industries.cafe.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CafePriceDemandRulesTest {
    @Test fun openingPriceAlwaysConverts() {
        assertTrue(CafePriceDemandRules.accepts("arrival_1", "espresso_001", 225L))
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

    @Test fun expandedMenuUsesItsOwnReferencePrices() {
        assertEquals(1060L, CafePriceDemandRules.referencePriceCents("caesar_salad_001"))
        assertEquals(880L, CafePriceDemandRules.referencePriceCents("chicken_sandwich_001"))
        assertEquals(575L, CafePriceDemandRules.referencePriceCents("cheesecake_001"))
        assertTrue(CafePriceDemandRules.accepts("lunch_1", "caesar_salad_001", 1060L))
    }

    @Test fun lunchDemandForFoodExceedsBreakfastDemand() {
        val lunch = (1..1000).count {
            CafePriceDemandRules.wantsCategory("group_$it", 12, "FOOD")
        }
        val breakfast = (1..1000).count {
            CafePriceDemandRules.wantsCategory("group_$it", 9, "FOOD")
        }
        assertTrue(lunch > breakfast)
    }

    @Test fun afternoonDessertDemandExceedsBreakfastDemand() {
        val afternoon = (1..1000).count {
            CafePriceDemandRules.wantsCategory("group_$it", 16, "DESSERT")
        }
        val breakfast = (1..1000).count {
            CafePriceDemandRules.wantsCategory("group_$it", 9, "DESSERT")
        }
        assertTrue(afternoon > breakfast)
        assertEquals(
            CafePriceDemandRules.wantsCategory("group_42", 16, "DESSERT"),
            CafePriceDemandRules.wantsCategory("group_42", 16, "DESSERT")
        )
    }

    @Test fun weekendsShiftLunchDemandTowardDessert() {
        val guests = (1..1000).map { "guest_$it" }
        fun interested(day: Int, category: String) =
            guests.count { CafePriceDemandRules.wantsCategory(it, 13, category, day) }

        assertTrue(interested(1, "FOOD") > interested(6, "FOOD"))
        assertTrue(interested(6, "DESSERT") > interested(1, "DESSERT"))
        assertEquals(interested(6, "DESSERT"), interested(13, "DESSERT"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPriceIsRejected() {
        CafePriceDemandRules.accepts("arrival_1", "espresso_001", 0L)
    }
}
