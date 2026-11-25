package com.example.runthebusiness.data.local

import android.content.Context
import com.example.runthebusiness.engine.model.GameTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CafeDayPerformance(
    val day: Int,
    val arrivingGroups: Int,
    val arrivingGuests: Int,
    val servedGroups: Int,
    val noSeats: Int,
    val noStock: Int,
    val declinedPrice: Int,
    val staffDisruption: Int,
    val rentShutdown: Int,
    val staffCapacity: Int,
    val pendingGroups: Int,
    val revenueCents: Long,
    val netSalesCents: Long,
    val cogsCents: Long
) {
    val lostGroups: Int get() = arrivingGroups - servedGroups - pendingGroups
    val grossMarginCents: Long get() = netSalesCents - cogsCents
}

/** Daily business outcomes from existing durable arrivals and sales; no schema changes. */
class CafeDailyPerformanceRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun recent(
        sessionId: String = "debug_session_001",
        days: Int = 14,
        through: GameTime? = null
    ): List<CafeDayPerformance> =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext emptyList()
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext emptyList()
            val session = db.accountingDao().getSession(sessionId)
                ?: return@withContext emptyList()
            val end = through ?: GameTime(session.currentDay, session.currentHour, session.currentMinute)
            val fromDay = (end.day - days + 1).coerceAtLeast(1)
            fun included(day: Int, hour: Int, minute: Int): Boolean =
                day >= fromDay && GameTime(day, hour, minute) <= end
            val arrivals = db.cafeArrivalDao().history(location.id)
                .filter { included(it.gameDay, it.gameHour, it.gameMinute) }
                .groupBy { it.gameDay }
            val orders = db.cafeSalesDao().getOrders(location.id)
                .filter { included(it.gameDay, it.gameHour, it.gameMinute) }
                .groupBy { it.gameDay }
            (fromDay..end.day).map { day ->
                val groups = arrivals[day].orEmpty()
                val sales = orders[day].orEmpty()
                CafeDayPerformance(
                    day = day,
                    arrivingGroups = groups.size,
                    arrivingGuests = groups.sumOf { it.guests },
                    servedGroups = groups.count { it.status == "SERVED" },
                    noSeats = groups.count { it.status == "NO_SEATS" },
                    noStock = groups.count { it.status == "NO_STOCK" },
                    declinedPrice = groups.count { it.status == "NO_PURCHASE" },
                    staffDisruption = groups.count { it.status == "STAFF_DISRUPTION" },
                    rentShutdown = groups.count { it.status == "RENT_SHUTDOWN" },
                    staffCapacity = groups.count { it.status == "STAFF_CAPACITY" },
                    pendingGroups = groups.count { it.status == "ARRIVED" },
                    netSalesCents = sales.sumOf { it.netCents },
                    revenueCents = sales.sumOf { it.grossCents },
                    cogsCents = sales.sumOf { it.cogsCents }
                )
            }.reversed()
        }
}
