package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CafeDiagnostics(
    val totalArrivals: Int,
    val pending: Int,
    val served: Int,
    val noSeats: Int,
    val noStock: Int,
    val menuCount: Int,
    val readyProducts: Int,
    val realIngredientsInstalled: Boolean,
    val productIssues: List<String>
)

class CafeDiagnosticsRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)

    suspend fun inspect(sessionId: String = "debug_session_001"): CafeDiagnostics? =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext null
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext null
            val history = db.cafeArrivalDao().history(location.id)
            val products = db.cafeMenuDao().getProducts(location.id)
            val stock = db.operationsDao().getInventoryBatches(business.id)
            val items = db.operationsDao().getInventoryItems(business.id)
            val issues = mutableListOf<String>()
            var ready = 0
            products.forEach { product ->
                val recipe = if (product.fulfillment == "RESALE") {
                    listOfNotNull(product.finishedStockItemId?.let { it to 1.0 })
                } else {
                    db.cafeMenuDao().getRecipe(product.id).map { it.ingredientId to it.quantity }
                }
                val missing = recipe.filter { (id, qty) ->
                    stock.filter { it.inventoryItemId == id }.sumOf { it.quantityRemaining } + 1e-8 < qty
                }
                if (!product.isAvailable) {
                    issues += "${product.name}: disabled"
                } else if (recipe.isEmpty()) {
                    issues += "${product.name}: no usable recipe"
                } else if (product.fulfillment == "BATCH_PRODUCED") {
                    issues += "${product.name}: batch production not supported"
                } else if (missing.isNotEmpty()) {
                    issues += "${product.name}: insufficient stock (${missing.joinToString { it.first }})"
                } else {
                    ready++
                }
            }
            CafeDiagnostics(
                totalArrivals = history.size,
                pending = history.count { it.status == "ARRIVED" },
                served = history.count { it.status == "SERVED" },
                noSeats = history.count { it.status == "NO_SEATS" },
                noStock = history.count { it.status == "NO_STOCK" },
                menuCount = products.size,
                readyProducts = ready,
                realIngredientsInstalled = items.any { it.id == "stock_beans_001" },
                productIssues = issues
            )
        }
}
