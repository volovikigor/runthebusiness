package com.example.runthebusiness.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuStockShortageTest {
    @Test
    fun groupsSharedIngredientOnceAndKeepsDishesWithMultipleShortages() {
        val cheese = MissingSupply("cheese", "Cheese", 10.0, 0.0, "GRAM", 450L)
        val chicken = MissingSupply("chicken", "Chicken", 40.0, 0.0, "GRAM", 900L)
        val menu = listOf(
            MenuReadiness("sandwich", "Ham sandwich", "FOOD", 0, listOf(cheese)),
            MenuReadiness("salad", "Caesar salad", "FOOD", 0, listOf(cheese, chicken)),
            MenuReadiness("coffee", "Coffee", "DRINK", 2, emptyList()),
            MenuReadiness("dessert", "Dessert", "DESSERT", 0, emptyList(), unsupported = true)
        )

        val shortages = menu.stockShortages()
        assertEquals(listOf("cheese", "chicken"), shortages.map { it.supply.itemId })
        assertEquals(listOf("Caesar salad", "Ham sandwich"), shortages[0].dishes.map { it.name })
        assertEquals(listOf("Caesar salad"), shortages[1].dishes.map { it.name })
        assertEquals(450L, shortages[0].supply.supplierPackCents)
    }
}
