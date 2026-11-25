package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.CafeProductEntity
import com.example.runthebusiness.data.local.entity.CafeRecipeItemEntity
import com.example.runthebusiness.engine.model.ProductType
import com.example.runthebusiness.industries.cafe.simulation.CafePriceDemandRules.referencePriceCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CafeMenuItem(
    val id: String,
    val name: String,
    val type: String,
    val priceCents: Long,
    val preparationMinutes: Int,
    val enabled: Boolean,
    val ingredients: List<Pair<String, Double>>
)

/** First persistent menu. Recipes reference the existing opening-stock placeholder until
 * ingredient-specific opening stock and purchasing are introduced.
 */
class CafeMenuRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)
    private val setup = db.gameSetupDao()
    private val locations = db.businessLocationDao()
    private val operations = db.operationsDao()
    private val menu = db.cafeMenuDao()

    suspend fun seedMenu(sessionId: String = "debug_session_001") = withContext(Dispatchers.IO) {
        db.withTransaction {
            val business = setup.getBusinesses(sessionId).singleOrNull()
                ?: error("Start café before creating the menu")
            val location = locations.getLocations(business.id).single()
            if (menu.countProducts(location.id) > 0) return@withTransaction
            val ingredient = operations.getInventoryItems(business.id).singleOrNull()
                ?: error("Opening inventory must exist")
            val products = listOf(
                CafeProductEntity("espresso_001", location.id, "Espresso",
                    ProductType.DRINK.name, referencePriceCents("espresso_001"), "ES_HOSPITALITY", 2, true),
                CafeProductEntity("cappuccino_001", location.id, "Cappuccino",
                    ProductType.DRINK.name, referencePriceCents("cappuccino_001"), "ES_HOSPITALITY", 4, true),
                CafeProductEntity("croissant_001", location.id, "Croissant",
                    ProductType.PASTRY.name, referencePriceCents("croissant_001"), "ES_HOSPITALITY", 2, true)
            )
            menu.insertProducts(products)
            menu.insertRecipes(listOf(
                CafeRecipeItemEntity("espresso_001", ingredient.id, 0.00015),
                CafeRecipeItemEntity("cappuccino_001", ingredient.id, 0.00025),
                CafeRecipeItemEntity("croissant_001", ingredient.id, 0.00020)
            ))
        }
    }

    suspend fun updatePrice(productId: String, priceCents: Long, sessionId: String = "debug_session_001") =
        withContext(Dispatchers.IO) {
            require(priceCents in 50L..5000L) { "Price must be between €0.50 and €50.00" }
            db.withTransaction {
                val business = setup.getBusinesses(sessionId).singleOrNull()
                    ?: error("Start café first")
                val location = locations.getLocations(business.id).single()
                check(menu.updatePrice(productId, location.id, priceCents) == 1) {
                    "Menu product not found"
                }
            }
        }

    suspend fun loadMenu(sessionId: String = "debug_session_001"): List<CafeMenuItem> =
        withContext(Dispatchers.IO) {
            val business = setup.getBusinesses(sessionId).singleOrNull()
                ?: return@withContext emptyList()
            val location = locations.getLocations(business.id).singleOrNull()
                ?: return@withContext emptyList()
            menu.getProducts(location.id).map { product ->
                CafeMenuItem(
                    id = product.id, name = product.name, type = product.type,
                    priceCents = product.sellingPriceCents,
                    preparationMinutes = product.preparationMinutes,
                    enabled = product.isAvailable,
                    ingredients = menu.getRecipe(product.id).map {
                        it.ingredientId to it.quantity
                    }
                )
            }
        }
}
