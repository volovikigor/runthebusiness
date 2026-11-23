package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.InventoryBatchEntity
import com.example.runthebusiness.data.local.entity.InventoryItemEntity
import com.example.runthebusiness.data.local.entity.CafeRecipeItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reclassifies part of the historical placeholder stock into actual ingredients/finished goods.
 * This is an inventory asset reclassification, not a new purchase or an expense.
 */
class CafeInventorySetupRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)
    private val setup = db.gameSetupDao()
    private val locations = db.businessLocationDao()
    private val operations = db.operationsDao()
    private val menu = db.cafeMenuDao()
    private val sales = db.cafeSalesDao()

    suspend fun hasRealIngredients(sessionId: String = "debug_session_001"): Boolean =
        withContext(Dispatchers.IO) {
            val business = setup.getBusinesses(sessionId).singleOrNull()
                ?: return@withContext false
            val ids = operations.getInventoryItems(business.id).map { it.id }.toSet()
            ids.containsAll(setOf("stock_beans_001", "stock_milk_001", "stock_croissants_001"))
        }

    suspend fun initializeRealStock(sessionId: String = "debug_session_001") =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val business = setup.getBusinesses(sessionId).single()
                val location = locations.getLocations(business.id).single()
                val products = menu.getProducts(location.id).associateBy { it.id }
                require(products.keys.containsAll(listOf("espresso_001", "cappuccino_001", "croissant_001"))) {
                    "Create starter menu first"
                }
                val items = operations.getInventoryItems(business.id)
                if (items.any { it.id == "stock_beans_001" }) return@withTransaction
                val placeholder = items.singleOrNull { it.id == "inventory_001" }
                    ?: error("Original opening stock not found")
                val oldBatch = operations.getInventoryBatches(business.id)
                    .singleOrNull { it.inventoryItemId == placeholder.id }
                    ?: error("Original opening batch not found")
                require(oldBatch.unitCostCents == 300_000L) {
                    "Unexpected opening stock valuation: cannot safely reclassify"
                }
                // Transfer 0.10 of the original €3,000 unit (€300).
                // New stock: 1,000g beans at €0.04/g = €40;
                // 10,000ml milk at €0.01/ml = €100;
                // 100 purchased croissants at €1.60 each = €160.
                require(oldBatch.quantityRemaining >= 0.10) {
                    "Not enough opening stock remains for a value-preserving conversion"
                }
                check(sales.updateBatchRemaining(
                    oldBatch.id, oldBatch.quantityRemaining - 0.10, oldBatch.quantityRemaining
                ) == 1)
                operations.insertInventoryItems(listOf(
                    InventoryItemEntity("stock_beans_001", location.id, "Coffee beans",
                        "GRAM", 200.0, true, 90),
                    InventoryItemEntity("stock_milk_001", location.id, "Milk",
                        "MILLILITER", 2000.0, true, 7),
                    InventoryItemEntity("stock_croissants_001", location.id, "Supplier croissants",
                        "PIECE", 20.0, true, 3)
                ))
                operations.insertInventoryBatches(listOf(
                    InventoryBatchEntity("batch_beans_001", "stock_beans_001", location.id,
                        1000.0, 1000.0, 4L, 1, 8, 0, null, null, null),
                    InventoryBatchEntity("batch_milk_001", "stock_milk_001", location.id,
                        10000.0, 10000.0, 1L, 1, 8, 0, null, null, null),
                    InventoryBatchEntity("batch_croissants_001", "stock_croissants_001", location.id,
                        100.0, 100.0, 160L, 1, 8, 0, null, null, null)
                ))
                listOf("espresso_001", "cappuccino_001", "croissant_001")
                    .forEach { menu.deleteRecipe(it) }
                menu.insertRecipes(listOf(
                    CafeRecipeItemEntity("espresso_001", "stock_beans_001", 9.0),
                    CafeRecipeItemEntity("cappuccino_001", "stock_beans_001", 9.0),
                    CafeRecipeItemEntity("cappuccino_001", "stock_milk_001", 150.0)
                ))
                check(menu.setFulfillment("espresso_001", "PREPARED", null) == 1)
                check(menu.setFulfillment("cappuccino_001", "PREPARED", null) == 1)
                check(menu.setFulfillment("croissant_001", "RESALE", "stock_croissants_001") == 1)
            }
        }
}
