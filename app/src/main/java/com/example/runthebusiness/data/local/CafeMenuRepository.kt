package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.CafeProductEntity
import com.example.runthebusiness.data.local.entity.CafeRecipeItemEntity
import com.example.runthebusiness.data.local.entity.CafePriceChangeEntity
import com.example.runthebusiness.data.local.dao.ProductSalesTotals
import com.example.runthebusiness.data.local.dao.PriceWindowTraffic
import com.example.runthebusiness.engine.model.ProductType
import com.example.runthebusiness.industries.cafe.simulation.CafePriceDemandRules.openingPriceCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CafeMenuItem(
    val id: String,
    val name: String,
    val type: String,
    val priceCents: Long,
    val preparationMinutes: Int,
    val enabled: Boolean,
    val fulfillment: String,
    val unitsSoldRecently: Long,
    val finishedStockItemId: String?,
    val ingredients: List<Pair<String, Double>>
)

data class PriceObservation(val days: IntRange, val sales: ProductSalesTotals, val traffic: PriceWindowTraffic)
data class MenuPriceComparison(
    val change: CafePriceChangeEntity,
    val hasBaseline: Boolean,
    val before: PriceObservation?,
    val after: PriceObservation?
)

internal fun comparablePriceWindows(today: Int, changeDay: Int, previousChangeDay: Int?): Pair<IntRange, IntRange>? {
    val firstStableDay = (previousChangeDay ?: 0) + 1
    val days = minOf(7, today - changeDay - 1, changeDay - firstStableDay)
    if (days <= 0) return null
    return ((changeDay - days) until changeDay) to ((changeDay + 1)..(changeDay + days))
}

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
                    ProductType.DRINK.name, openingPriceCents("espresso_001"), "ES_HOSPITALITY", 2, true),
                CafeProductEntity("cappuccino_001", location.id, "Cappuccino",
                    ProductType.DRINK.name, openingPriceCents("cappuccino_001"), "ES_HOSPITALITY", 4, true),
                CafeProductEntity("croissant_001", location.id, "Croissant",
                    ProductType.PASTRY.name, openingPriceCents("croissant_001"), "ES_HOSPITALITY", 2, true)
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
                val product = checkNotNull(menu.getProduct(location.id, productId)) { "Menu product not found" }
                val day = checkNotNull(db.accountingDao().getSession(sessionId)).currentDay
                recordPrice(product, priceCents, day)
                Unit
            }
        }

    suspend fun resetPrices(sessionId: String = "debug_session_001"): Int = db.withTransaction {
        val business = setup.getBusinesses(sessionId).single()
        val location = locations.getLocations(business.id).single()
        val day = checkNotNull(db.accountingDao().getSession(sessionId)).currentDay
        menu.getProducts(location.id).count { product ->
            recordPrice(product, openingPriceCents(product.id), day)
        }
    }

    private suspend fun recordPrice(product: CafeProductEntity, priceCents: Long, day: Int): Boolean {
        if (product.sellingPriceCents == priceCents) return false
        val sameDay = menu.latestPriceChanges(product.id).firstOrNull()?.takeIf { it.gameDay == day }
        menu.savePriceChange(CafePriceChangeEntity(
            product.id, day, sameDay?.previousCents ?: product.sellingPriceCents, priceCents
        ))
        check(menu.updatePrice(product.id, product.locationId, priceCents) == 1) { "Menu product not found" }
        return true
    }

    suspend fun priceComparisons(sessionId: String = "debug_session_001"): List<MenuPriceComparison> =
        db.withTransaction {
            val business = setup.getBusinesses(sessionId).singleOrNull() ?: return@withTransaction emptyList()
            val location = locations.getLocations(business.id).single()
            val today = checkNotNull(db.accountingDao().getSession(sessionId)).currentDay
            menu.latestChangedProducts(location.id).map { change ->
                val previous = menu.latestPriceChanges(change.productId).getOrNull(1)
                val windows = comparablePriceWindows(today, change.gameDay, previous?.gameDay)
                suspend fun observe(days: IntRange): PriceObservation = PriceObservation(
                    days,
                    db.cafeSalesDao().productSales(location.id, change.productId, days.first, days.last),
                    db.cafeArrivalDao().priceWindowTraffic(location.id, days.first, days.last)
                )
                MenuPriceComparison(change, change.gameDay > (previous?.gameDay ?: 0) + 1, windows?.let { observe(it.first) }, windows?.let { observe(it.second) })
            }
        }

    suspend fun loadMenu(sessionId: String = "debug_session_001"): List<CafeMenuItem> =
        withContext(Dispatchers.IO) {
            val business = setup.getBusinesses(sessionId).singleOrNull()
                ?: return@withContext emptyList()
            val location = locations.getLocations(business.id).singleOrNull()
                ?: return@withContext emptyList()
            val today = db.accountingDao().getSession(sessionId)?.currentDay ?: 1
            val recentSales = db.cafeSalesDao().unitsSold(location.id, (today - 7).coerceAtLeast(1), today)
                .associate { it.productId to it.units }
            menu.getProducts(location.id).map { product ->
                CafeMenuItem(
                    id = product.id, name = product.name, type = product.type,
                    priceCents = product.sellingPriceCents,
                    preparationMinutes = product.preparationMinutes,
                    enabled = product.isAvailable,
                    fulfillment = product.fulfillment,
                    unitsSoldRecently = recentSales[product.id] ?: 0L,
                    finishedStockItemId = product.finishedStockItemId,
                    ingredients = menu.getRecipe(product.id).map {
                        it.ingredientId to it.quantity
                    }
                )
            }
        }
}
