package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opt-in replenishment policy for the single-save MVP.
 * A daily budget is a spending ceiling, NOT a fixed recurring payment.
 * Actual purchases use the regular cash/bank and inventory accounting path.
 */
class CafeAutoPurchasingRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("cafe_auto_purchasing", Context.MODE_PRIVATE)
    private val purchasing = CafePurchasingRepository(app)
    private val db = GameDatabase.getInstance(app)

    data class Policy(val enabled: Boolean, val dailyBudgetCents: Long, val lastRunDay: Int)

    fun policy(): Policy = Policy(
        prefs.getBoolean("enabled", false),
        prefs.getLong("daily_budget", 20000L),
        prefs.getInt("last_day", 0)
    )

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    fun setDailyBudget(cents: Long) {
        require(cents in 0L..1000000L)
        prefs.edit().putLong("daily_budget", cents).apply()
    }

    fun enabled(itemId: String): Boolean = prefs.getBoolean("item_$itemId", true)

    fun setItemEnabled(itemId: String, enabled: Boolean) {
        require(CafePurchasingRepository.supplies.any { it.itemId == itemId })
        prefs.edit().putBoolean("item_$itemId", enabled).apply()
    }

    fun enableCoreIngredients() {
        val editor = prefs.edit()
        listOf("stock_beans_001", "stock_milk_001", "stock_croissants_001").forEach {
            editor.putBoolean("item_$it", true)
        }
        CafePurchasingRepository.supplies.forEach { editor.putBoolean("item_${it.itemId}", true) }
        editor.putBoolean("enabled", true).apply()
    }

    fun reset() { prefs.edit().clear().commit() }

    /** Called at day boundaries, once per day, after expiry and before serving customers. */
    suspend fun replenish(day: Int, sessionId: String = "debug_session_001"): Int =
        withContext(Dispatchers.IO) {
            val config = policy()
            if (!config.enabled || day <= config.lastRunDay) return@withContext 0
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext 0
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext 0
            val installed = db.operationsDao().getInventoryItems(business.id).map { it.id }.toSet()
            val products = db.cafeMenuDao().getProducts(location.id).filter { it.isAvailable }
            val required = mutableSetOf<String>()
            for (product in products) {
                if (product.fulfillment == "RESALE") {
                    product.finishedStockItemId?.let { required += it }
                } else if (product.fulfillment == "PREPARED") {
                    required += db.cafeMenuDao().getRecipe(product.id).map { it.ingredientId }
                }
            }
            val available = purchasing.stock(sessionId)
            val lookbackDays = minOf(14, (day - 1).coerceAtLeast(1))
            val history = db.cafeSalesDao().movementsSince((day - lookbackDays).coerceAtLeast(1))
            val soldByItem = history.filter { it.sourceType == "CUSTOMER_ORDER" &&
                it.quantityDelta < 0.0 }.groupBy { it.inventoryItemId }
                .mapValues { (_, rows) -> rows.sumOf { -it.quantityDelta } / lookbackDays }
            // Reserve a small baseline for each active recipe, including products
            // whose historical sales were censored by missing ingredients.
            val baseline = mutableMapOf<String, Double>()
            for (product in products) {
                val recipe = when (product.fulfillment) {
                    "RESALE" -> listOfNotNull(product.finishedStockItemId?.let { it to 1.0 })
                    "PREPARED" -> db.cafeMenuDao().getRecipe(product.id).map { it.ingredientId to it.quantity }
                    else -> emptyList()
                }
                val servings = if (product.type == "DRINK") 12.0 else 4.0
                recipe.forEach { (id, qty) ->
                    baseline[id] = (baseline[id] ?: 0.0) + qty * servings
                }
            }
            var spent = 0L
            var purchased = 0
            val candidates = CafePurchasingRepository.supplies.filter {
                enabled(it.itemId) && it.itemId in installed && it.itemId in required
            }
            val shift = if (candidates.isEmpty()) 0 else Math.floorMod(day, candidates.size)
            val rotated = candidates.drop(shift) + candidates.take(shift)
            val prioritized = rotated.sortedBy { if ((available[it.itemId] ?: 0.0) < (baseline[it.itemId] ?: 0.0)) 0 else 1 }
            for (supply in prioritized) {
                val current = available[supply.itemId] ?: 0.0
                val daily = soldByItem[supply.itemId] ?: 0.0
                val coverage = if (supply.shelfLifeDays in 1..3) 1.25 else 2.0
                val target = maxOf(baseline[supply.itemId] ?: 0.0, daily * coverage)
                val needed = kotlin.math.ceil(target - current).coerceAtMost(supply.quantity)
                if (needed <= 0.0 || supply.unitCostCents <= 0L) continue
                val affordable = (config.dailyBudgetCents - spent) / supply.unitCostCents
                val quantity = minOf(needed, affordable.toDouble())
                if (quantity <= 0.0) continue
                try {
                    spent += purchasing.purchase(supply.itemId, sessionId, quantity)
                    purchased++
                } catch (_: IllegalArgumentException) {
                    // Insufficient funds or unavailable supply.
                } catch (_: IllegalStateException) {
                    // Changed fulfillment or missing inventory.
                }
            }
            prefs.edit().putInt("last_day", day).commit()
            purchased
        }
}
