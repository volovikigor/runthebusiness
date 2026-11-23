package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CafeCustomerFlow(
    val days: Int, val groups: Int, val guests: Int,
    val servedGroups: Int, val servedGuests: Int,
    val byStatus: Map<String, Int>,
    val orders: Int,
    val netSalesCents: Long,
    val orderLines: Int,
    val cardOrders: Int,
    val cardGrossCents: Long,
    val cashGrossCents: Long
)

class CafeCustomerFlowRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun lastCompletedDays(days: Int = 14, sessionId: String = "debug_session_001"):
        CafeCustomerFlow? = withContext(Dispatchers.IO) {
        val session = db.accountingDao().getSession(sessionId) ?: return@withContext null
        val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
            ?: return@withContext null
        val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
            ?: return@withContext null
        val fromDay = (session.currentDay - days).coerceAtLeast(1)
        val rows = db.cafeArrivalDao().history(location.id).filter {
            it.gameDay in fromDay until session.currentDay
        }
        val served = rows.filter { it.status == "SERVED" }
        val orders = db.cafeSalesDao().getOrders(location.id).filter {
            it.gameDay in fromDay until session.currentDay
        }
        val orderIds = orders.map { it.id }.toSet()
        val orderLines = db.cafeSalesDao().getAllOrderLines().count { it.orderId in orderIds }
        CafeCustomerFlow(
            days = (session.currentDay - fromDay).coerceAtLeast(0),
            groups = rows.size, guests = rows.sumOf { it.guests },
            servedGroups = served.size, servedGuests = served.sumOf { it.guests },
            byStatus = rows.groupingBy { it.status }.eachCount().toSortedMap(),
            orders = orders.size,
            netSalesCents = orders.sumOf { it.netCents },
            orderLines = orderLines,
            cardOrders = orders.count { it.paymentMethod == "BANK_CARD" },
            cardGrossCents = orders.filter { it.paymentMethod == "BANK_CARD" }.sumOf { it.grossCents },
            cashGrossCents = orders.filter { it.paymentMethod == "CASH" }.sumOf { it.grossCents }
        )
    }
}
