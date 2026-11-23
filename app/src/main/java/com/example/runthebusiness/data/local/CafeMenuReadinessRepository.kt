package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

data class MenuReadiness(
    val productId: String,
    val name: String,
    val category: String,
    val servings: Int,
    val missing: List<MissingSupply>
) {
    val ready: Boolean get() = servings > 0
}

data class MissingSupply(
    val itemId: String,
    val name: String,
    val required: Double,
    val available: Double,
    val unit: String,
    val supplierPackCents: Long?
)

/** Actual recipe-based servings, not the menu's isAvailable flag. */
class CafeMenuReadinessRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun snapshot(sessionId: String = "debug_session_001"): List<MenuReadiness> =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext emptyList()
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext emptyList()
            val products = db.cafeMenuDao().getProducts(location.id).filter { it.isAvailable }
            val items = db.operationsDao().getInventoryItems(business.id).associateBy { it.id }
            val stock = db.operationsDao().getInventoryBatches(business.id)
                .groupBy { it.inventoryItemId }
                .mapValues { (_, rows) -> rows.sumOf { it.quantityRemaining.coerceAtLeast(0.0) } }
            val supplies = CafePurchasingRepository.supplies.associateBy { it.itemId }
            products.map { product ->
                val recipe = when (product.fulfillment) {
                    "RESALE" -> listOfNotNull(product.finishedStockItemId?.let { it to 1.0 })
                    "PREPARED" -> db.cafeMenuDao().getRecipe(product.id)
                        .map { it.ingredientId to it.quantity }
                    else -> emptyList()
                }
                val missing = recipe.filter { (id, qty) ->
                    qty > 0.0 && (stock[id] ?: 0.0) + 1e-8 < qty
                }.map { (id, qty) ->
                    MissingSupply(
                        itemId = id,
                        name = items[id]?.name ?: id,
                        required = qty,
                        available = stock[id] ?: 0.0,
                        unit = items[id]?.unit ?: "",
                        supplierPackCents = supplies[id]?.costCents
                    )
                }
                val servings = if (recipe.isEmpty() || product.fulfillment == "BATCH_PRODUCED") 0
                    else recipe.minOf { (id, qty) ->
                        if (qty <= 0.0) 0 else floor(((stock[id] ?: 0.0) + 1e-8) / qty)
                            .coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
                    }
                MenuReadiness(product.id, product.name, product.type, servings, missing)
            }.sortedWith(compareBy<MenuReadiness> { it.ready }.thenBy { it.category }.thenBy { it.name })
        }
}
