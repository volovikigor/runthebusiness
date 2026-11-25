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
        assertTrue(first.all { it.hour in 8 until 17 && it.guests in 1..4 })
        assertEquals(first.size, first.map { it.key }.distinct().size)
    }

    @Test fun averageArrivalsNearOneHundredTwentyGuestsPerFullDay() {
        val dailyGuests = (2..101).map { day ->
            CafeArrivalPlanner.plan(
                GameTime(day, 7, 45), GameTime(day, 17, 0), 8, 17, 24
            ).sumOf { it.guests }
        }
        val average = dailyGuests.average()
        assertTrue("Average arriving guests/day: $average", average in 113.0..121.0)
        assertTrue("Day-to-day variation required", dailyGuests.toSet().size > 3)
        assertTrue("Do not guarantee 117 arrivals", dailyGuests.any { it < 117 })
        assertTrue("Do not cap at 117 arrivals", dailyGuests.any { it > 117 })
    }

    @Test fun discountsAddTargetedGroupsWithoutReplacingRegularArrivals() {
        val from = GameTime(1, 7, 45)
        val middle = GameTime(1, 12, 0)
        val to = GameTime(1, 17, 0)
        val product = "latte_001"
        val price = CafePriceDemandRules.openingPriceCents(product)
        assertEquals(0, CafePriceDemandRules.discountTrafficWeight(product, price))

        fun promoted(amount: Long) = listOf(CafeArrivalPlanner.Promotion(
            product, CafePriceDemandRules.discountTrafficWeight(product, amount)
        ))
        val regular = CafeArrivalPlanner.plan(from, to, 8, 17, 24)
        val light = CafeArrivalPlanner.plan(from, to, 8, 17, 24, promoted(380L))
        val deep = CafeArrivalPlanner.plan(from, to, 8, 17, 24, promoted(280L))
        assertEquals(regular, deep.filter { it.preferredProductId == null })
        assertTrue(light.size > regular.size)
        assertTrue(deep.size > light.size)
        assertTrue(deep.filter { it.preferredProductId != null }.all { it.preferredProductId == product })
        assertEquals(deep.size, deep.map { it.key }.distinct().size)
        assertEquals(deep, CafeArrivalPlanner.plan(from, middle, 8, 17, 24, promoted(280L)) +
            CafeArrivalPlanner.plan(middle, to, 8, 17, 24, promoted(280L)))
    }

    @Test fun higherPricesReduceRegularTrafficAndAlternativesSoftenTheDrop() {
        val from = GameTime(1, 7, 45)
        val to = GameTime(1, 17, 0)
        val regular = CafeArrivalPlanner.plan(from, to, 8, 17, 24)
        fun visits(vararg menu: CafePriceDemandRules.MenuPrice) = CafeArrivalPlanner.plan(
            from, to, 8, 17, 24, priceResistance = CafePriceDemandRules.categoryPriceResistance(menu.toList())
        )
        val espresso = CafePriceDemandRules.MenuPrice("espresso_001", "DRINK", 225L)
        assertEquals(regular, visits(espresso))
        val expensive = espresso.copy(cents = 900L)
        val fewer = visits(expensive)
        assertTrue(fewer.size < regular.size)
        assertTrue(regular.containsAll(fewer))
        val alternative = CafePriceDemandRules.MenuPrice("latte_001", "DRINK", 420L)
        assertTrue(visits(expensive, alternative).size > fewer.size)
    }

    @Test fun zeroSeatsPreventsArrivals() {
        assertTrue(CafeArrivalPlanner.plan(GameTime(1, 8, 0), GameTime(1, 9, 0), 8, 17, 0).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun longerThanOneDayIsRejected() {
        CafeArrivalPlanner.plan(GameTime(1, 8, 0), GameTime(2, 8, 15), 8, 17, 24)
    }
}
