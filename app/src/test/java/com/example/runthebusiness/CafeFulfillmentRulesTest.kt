package com.example.runthebusiness

import com.example.runthebusiness.industries.cafe.model.CafeFulfillment
import com.example.runthebusiness.industries.cafe.model.CafeFulfillmentRules
import org.junit.Test

class CafeFulfillmentRulesTest {
    @Test fun purchasedCroissantIsFinishedStock() {
        CafeFulfillmentRules.validate(CafeFulfillment.RESALE, 0, "croissant_stock")
    }

    @Test fun cappuccinoRequiresIngredients() {
        CafeFulfillmentRules.validate(CafeFulfillment.PREPARED, 2, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun resaleCannotConsumeRawRecipeAtSale() {
        CafeFulfillmentRules.validate(CafeFulfillment.RESALE, 1, "croissant_stock")
    }

    @Test(expected = IllegalArgumentException::class)
    fun resaleRequiresStockLink() {
        CafeFulfillmentRules.validate(CafeFulfillment.RESALE, 0, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun preparedProductRequiresRecipe() {
        CafeFulfillmentRules.validate(CafeFulfillment.PREPARED, 0, null)
    }
}
