package com.example.runthebusiness.data.local

import android.content.Context
import com.example.runthebusiness.industries.cafe.simulation.CafeReplenishmentRules
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
            val allMovements = db.cafeSalesDao().movementsSince(1)
            val history = allMovements.filter { it.gameDay >= (day - lookbackDays).coerceAtLeast(1) }
            val soldByItem = history.filter { it.sourceType == "CUSTOMER_ORDER" &&
                it.quantityDelta < 0.0 }.groupBy { it.inventoryItemId }
                .mapValues { (_, rows) -> rows.sumOf { -it.quantityDelta } / lookbackDays }
            // Distinguish initial stocking from repeatedly expiring unsold packs.
            val previouslyPurchased = allMovements
                .filter { it.sourceType == "INVENTORY_PURCHASE" }
                .map { it.inventoryItemId }.toSet()
            var spent = 0L
            var purchased = 0
            // Rotate priority daily so a limited budget does not always starve the same items.
            val candidates = CafePurchasingRepository.supplies.filter {
                enabled(it.itemId) && it.itemId in installed && it.itemId in required
            }
            val shift = if (candidates.isEmpty()) 0 else Math.floorMod(day, candidates.size)
            val rotated = candidates.drop(shift) + candidates.take(shift)
            val prioritized = rotated.sortedBy { if ((available[it.itemId] ?: 0.0) <= 1e-8) 0 else 1 }
            for (supply in prioritized) {
                if (!enabled(supply.itemId) || supply.itemId !in installed ||
                    supply.itemId !in required) continue
                // One supplier pack when stock falls below 20% of a pack.
                val current = available[supply.itemId] ?: 0.0
                // A zero-stock item has censored sales history. Allow a controlled
                // trial pack once a week even when observed demand is insufficient.
                val trial = current <= 1e-8 && Math.floorMod(day, 7) ==
                    Math.floorMod(supply.itemId.hashCode(), 7)
                if (supply.itemId in previouslyPurchased && !trial) {
                    if (!CafeReplenishmentRules.shouldAutoBuy(
                            current, supply.quantity, soldByItem[supply.itemId] ?: 0.0,
                            supply.shelfLifeDays)) continue
                } else if (current > supply.quantity * 0.2) continue
                if (spent + supply.costCents > config.dailyBudgetCents) continue
                try {
                    purchasing.purchase(supply.itemId, sessionId)
                    spent += supply.costCents
                    purchased++
                } catch (_: IllegalArgumentException) {
                    // Insufficient funds or unavailable supplier pack: leave item out of stock.
                } catch (_: IllegalStateException) {
                    // Insufficient funds or changed fulfillment: no purchase this day.
                }
            }
            prefs.edit().putInt("last_day", day).commit()
            purchased
        }
}
