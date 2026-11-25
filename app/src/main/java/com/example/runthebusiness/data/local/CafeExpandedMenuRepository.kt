package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.CafeProductEntity
import com.example.runthebusiness.data.local.entity.InventoryItemEntity
import com.example.runthebusiness.industries.cafe.simulation.CafePriceDemandRules.referencePriceCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase-one expanded menu: supplier-prepared food is stocked as finished goods.
 * Purchasing capitalizes each unit and FIFO sale recognizes its cost.
 * Future kitchen milestone can replace resale with ingredient-level recipes.
 */
class CafeExpandedMenuRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    data class Offering(
        val id: String, val name: String, val type: String,
        val priceCents: Long, val costCents: Long, val packSize: Double,
        val shelfLifeDays: Int, val preparationMinutes: Int
    ) {
        val stockId: String get() = "stock_$id"
    }

    companion object {
        val offerings = listOf(
            Offering("americano_001", "Americano", "DRINK", referencePriceCents("americano_001"), 80, 30.0, 7, 3),
            Offering("latte_001", "Latte", "DRINK", referencePriceCents("latte_001"), 140, 30.0, 7, 4),
            Offering("pain_chocolat_001", "Pain au chocolat", "PASTRY", referencePriceCents("pain_chocolat_001"), 150, 30.0, 3, 2),
            Offering("muffin_001", "Blueberry muffin", "PASTRY", referencePriceCents("muffin_001"), 160, 30.0, 3, 2),
            Offering("cheesecake_001", "Cheesecake slice", "DESSERT", referencePriceCents("cheesecake_001"), 220, 20.0, 4, 2),
            Offering("carrot_cake_001", "Carrot cake slice", "DESSERT", referencePriceCents("carrot_cake_001"), 190, 20.0, 4, 2),
            Offering("brownie_001", "Brownie", "DESSERT", referencePriceCents("brownie_001"), 150, 24.0, 5, 2),
            Offering("ham_cheese_001", "Ham & cheese sandwich", "FOOD", referencePriceCents("ham_cheese_001"), 280, 20.0, 2, 5),
            Offering("chicken_sandwich_001", "Chicken sandwich", "FOOD", referencePriceCents("chicken_sandwich_001"), 360, 20.0, 2, 6),
            Offering("caesar_salad_001", "Caesar salad", "FOOD", referencePriceCents("caesar_salad_001"), 440, 15.0, 2, 8),
            Offering("med_salad_001", "Mediterranean salad", "FOOD", referencePriceCents("med_salad_001"), 380, 15.0, 2, 7)
        )
    }

    suspend fun install(sessionId: String = "debug_session_001", enableNewProducts: Boolean = false) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val business = db.gameSetupDao().getBusinesses(sessionId).single()
                val location = db.businessLocationDao().getLocations(business.id).single()
                val menu = db.cafeMenuDao()
                val existing = menu.getProducts(location.id).map { it.id }.toSet()
                val existingStock = db.operationsDao().getInventoryItems(business.id)
                    .map { it.id }.toSet()
                val newItems = offerings.filter { it.stockId !in existingStock }.map {
                    InventoryItemEntity(it.stockId, location.id, it.name + " (ready-made)",
                        "PIECE", it.packSize * 0.2, true, it.shelfLifeDays)
                }
                if (newItems.isNotEmpty()) db.operationsDao().insertInventoryItems(newItems)
                val newProducts = offerings.filter { it.id !in existing }.map {
                    CafeProductEntity(it.id, location.id, it.name, it.type,
                        it.priceCents, "ES_HOSPITALITY", it.preparationMinutes,
                        enableNewProducts, "RESALE", it.stockId)
                }
                if (newProducts.isNotEmpty()) menu.insertProducts(newProducts)
            }
        }

    suspend fun setEnabled(productId: String, enabled: Boolean,
        sessionId: String = "debug_session_001") = withContext(Dispatchers.IO) {
        val business = db.gameSetupDao().getBusinesses(sessionId).single()
        val location = db.businessLocationDao().getLocations(business.id).single()
        check(db.cafeMenuDao().setAvailability(productId, location.id, enabled) == 1)
    }
}
