package com.example.runthebusiness.data.local

import android.content.Context
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.data.local.entity.BusinessEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TimedCafeEvent(val id: String, val time: GameTime)
data class CafeOrderScene(val event: TimedCafeEvent, val drinks: Boolean, val food: Boolean)

data class CafeDashboardSnapshot(
    val time: GameTime,
    val cafeName: String,
    val tables: Int,
    val seats: Int,
    val isOpen: Boolean,
    val openingHour: Int,
    val closingHour: Int,
    val salesCount: Int,
    val grossSalesCents: Long,
    val netSalesCents: Long,
    val outputVatCents: Long,
    val cogsCents: Long,
    val stockRemaining: Double,
    val stockOriginal: Double,
    val latestOrder: CafeOrderScene? = null,
    val latestDelivery: TimedCafeEvent? = null,
    val latestPayment: TimedCafeEvent? = null
) {
    val isTrading: Boolean get() = isOpen && time.hour in openingHour until closingHour
    val grossProfitCents: Long get() = netSalesCents - cogsCents
}

class CafeDashboardRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)
    private val setup = db.gameSetupDao()
    private val accounting = db.accountingDao()
    private val locations = db.businessLocationDao()
    private val operations = db.operationsDao()
    private val sales = db.cafeSalesDao()

    suspend fun snapshot(sessionId: String = "debug_session_001"): CafeDashboardSnapshot? =
        withContext(Dispatchers.IO) {
            val session = accounting.getSession(sessionId) ?: return@withContext null
            val business = setup.getBusinesses(sessionId).singleOrNull() ?: return@withContext null
            val shared = locations.getLocations(business.id).singleOrNull() ?: return@withContext null
            val cafe = operations.getLocations(business.id).singleOrNull() ?: return@withContext null
            val orders = sales.getOrders(shared.id)
            val batches = operations.getInventoryBatches(business.id)
            val time = GameTime(session.currentDay, session.currentHour, session.currentMinute)
            val latestOrder = orders.firstOrNull()?.let { order ->
                val orderTime = GameTime(order.gameDay, order.gameHour, order.gameMinute)
                if (time.totalMinutes - orderTime.totalMinutes !in 0L..29L) null else {
                    val orderIds = orders.takeWhile {
                        it.gameDay == order.gameDay && it.gameHour == order.gameHour && it.gameMinute == order.gameMinute
                    }.map { it.id }
                    val products = db.cafeMenuDao().getProducts(shared.id).associateBy { it.id }
                    val ordered = sales.getOrderLinesForOrders(orderIds).mapNotNull { products[it.productId] }
                    CafeOrderScene(TimedCafeEvent(order.id, orderTime),
                        drinks = ordered.any { it.type == "DRINK" },
                        food = ordered.any { it.type == "FOOD" && it.fulfillment == "PREPARED" })
                }
            }
            CafeDashboardSnapshot(
                time = time,
                cafeName = shared.name, tables = cafe.tableCount,
                seats = cafe.tableCount * cafe.seatsPerTable,
                isOpen = shared.isOpen,
                openingHour = shared.openingHour,
                closingHour = shared.closingHour,
                salesCount = orders.size,
                grossSalesCents = orders.sumOf { it.grossCents },
                netSalesCents = orders.sumOf { it.netCents },
                outputVatCents = orders.sumOf { it.vatCents },
                cogsCents = orders.sumOf { it.cogsCents },
                stockRemaining = batches.sumOf { it.quantityRemaining },
                stockOriginal = batches.sumOf { it.quantityReceived },
                latestOrder = latestOrder,
                latestDelivery = accounting.latestSupplierDelivery(sessionId)?.timed(),
                latestPayment = accounting.latestAutomaticPayment(sessionId)?.timed()
            )
        }
}

private fun BusinessEventEntity.timed(): TimedCafeEvent =
    TimedCafeEvent(id, GameTime(gameDay, gameHour, gameMinute))
