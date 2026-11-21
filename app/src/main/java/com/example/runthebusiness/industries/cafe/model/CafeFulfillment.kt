package com.example.runthebusiness.industries.cafe.model

/** How the saleable item enters inventory or is prepared. Independent of serving time. */
enum class CafeFulfillment {
    RESALE,          // Purchased finished goods, e.g. croissants and bottled water
    PREPARED,        // Made to order from ingredients, e.g. cappuccino
    BATCH_PRODUCED   // Produced in advance; sell from finished-goods inventory
}

object CafeFulfillmentRules {
    fun validate(fulfillment: CafeFulfillment, recipeIngredientCount: Int, stockItemId: String?) {
        when (fulfillment) {
            CafeFulfillment.RESALE, CafeFulfillment.BATCH_PRODUCED -> {
                require(recipeIngredientCount == 0) { "Finished goods cannot consume raw recipe ingredients at sale" }
                require(!stockItemId.isNullOrBlank()) { "Finished goods need a stock item" }
            }
            CafeFulfillment.PREPARED -> {
                require(recipeIngredientCount > 0) { "Made-to-order products need a recipe" }
                require(stockItemId == null) { "Made-to-order products are not finished-goods stock" }
            }
        }
    }
}
