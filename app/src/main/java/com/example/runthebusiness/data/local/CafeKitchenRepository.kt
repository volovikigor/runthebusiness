package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.CafeRecipeItemEntity
import com.example.runthebusiness.data.local.entity.InventoryItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional migration of four supplier-ready meals to made-to-order recipes.
 * A product is converted only after its existing finished-goods stock is consumed.
 * This prevents deleting paid-for inventory or silently changing its valuation.
 */
class CafeKitchenRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    data class Ingredient(
        val id: String, val name: String, val unit: String,
        val pack: Double, val costCents: Long, val shelfDays: Int
    )

    companion object {
        val ingredients = listOf(
            Ingredient("kitchen_bread", "Sandwich bread", "pcs", 40.0, 45L, 4),
            Ingredient("kitchen_ham", "Ham", "g", 2000.0, 2L, 5),
            Ingredient("kitchen_cheese", "Cheese", "g", 2000.0, 2L, 7),
            Ingredient("kitchen_chicken", "Cooked chicken", "g", 2000.0, 3L, 3),
            Ingredient("kitchen_lettuce", "Lettuce", "g", 2000.0, 1L, 3),
            Ingredient("kitchen_tomato", "Tomato", "g", 2000.0, 1L, 4),
            Ingredient("kitchen_dressing", "Salad dressing", "ml", 2000.0, 1L, 14)
        )
        val recipes: Map<String, List<Pair<String, Double>>> = mapOf(
            "ham_cheese_001" to listOf("kitchen_bread" to 2.0,
                "kitchen_ham" to 70.0, "kitchen_cheese" to 30.0),
            "chicken_sandwich_001" to listOf("kitchen_bread" to 2.0,
                "kitchen_chicken" to 90.0, "kitchen_lettuce" to 30.0,
                "kitchen_tomato" to 40.0),
            "caesar_salad_001" to listOf("kitchen_chicken" to 100.0,
                "kitchen_lettuce" to 130.0, "kitchen_cheese" to 25.0,
                "kitchen_dressing" to 35.0),
            "med_salad_001" to listOf("kitchen_lettuce" to 130.0,
                "kitchen_tomato" to 120.0, "kitchen_cheese" to 60.0,
                "kitchen_dressing" to 35.0)
        )
    }

    data class Result(val converted: Int, val waitingForStock: List<String>)

    suspend fun installed(sessionId: String = "debug_session_001"): Boolean =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext false
            db.operationsDao().getInventoryItems(business.id)
                .any { it.id == "kitchen_bread" }
        }

    suspend fun activate(sessionId: String = "debug_session_001"): Result =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val business = db.gameSetupDao().getBusinesses(sessionId).single()
                val location = db.businessLocationDao().getLocations(business.id).single()
                val operations = db.operationsDao()
                val menu = db.cafeMenuDao()
                val existingIds = operations.getInventoryItems(business.id).map { it.id }.toSet()
                val missing = ingredients.filter { it.id !in existingIds }.map {
                    InventoryItemEntity(it.id, location.id, it.name,
                        when (it.unit) {
                            "pcs" -> "PIECE"
                            "g" -> "GRAM"
                            else -> "MILLILITER"
                        }, it.pack * 0.2, true, it.shelfDays)
                }
                if (missing.isNotEmpty()) operations.insertInventoryItems(missing)
                val products = menu.getProducts(location.id).associateBy { it.id }
                val stock = operations.getInventoryBatches(business.id)
                    .groupBy { it.inventoryItemId }
                    .mapValues { (_, rows) -> rows.sumOf { it.quantityRemaining } }
                var converted = 0
                val waiting = mutableListOf<String>()
                recipes.forEach { (productId, recipe) ->
                    val product = products[productId] ?: return@forEach
                    if (product.fulfillment == "PREPARED") return@forEach
                    if (product.fulfillment != "RESALE") return@forEach
                    val oldStock = product.finishedStockItemId
                    if (oldStock != null && (stock[oldStock] ?: 0.0) > 0.000001) {
                        waiting += product.name
                        return@forEach
                    }
                    menu.deleteRecipe(productId)
                    menu.insertRecipes(recipe.map { (item, amount) ->
                        CafeRecipeItemEntity(productId, item, amount)
                    })
                    check(menu.setFulfillment(productId, "PREPARED", null) == 1)
                    converted++
                }
                Result(converted, waiting)
            }
        }
}
