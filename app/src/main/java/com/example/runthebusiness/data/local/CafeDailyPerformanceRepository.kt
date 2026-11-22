package com.example.runthebusiness.data.local

import android.content.Context
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
    val revenueCents: Long,
    val cogsCents: Long
) {
    val lostGroups: Int get() = arrivingGroups - servedGroups
    val grossMarginCents: Long get() = revenueCents - cogsCents
}

/** Daily business outcomes from existing durable arrivals and sales; no schema changes. */
class CafeDailyPerformanceRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun recent(sessionId: String = "debug_session_001", days: Int = 14): List<CafeDayPerformance> =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext emptyList()
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext emptyList()
            val session = db.accountingDao().getSession(sessionId)
                ?: return@withContext emptyList()
            val fromDay = (session.currentDay - days + 1).coerceAtLeast(1)
            val arrivals = db.cafeArrivalDao().history(location.id)
                .filter { it.gameDay >= fromDay }
                .groupBy { it.gameDay }
            val orders = db.cafeSalesDao().getOrders(location.id)
                .filter { it.gameDay >= fromDay }
                .groupBy { it.gameDay }
            (fromDay..session.currentDay).map { day ->
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
                    revenueCents = sales.sumOf { it.grossCents },
                    cogsCents = sales.sumOf { it.cogsCents }
                )
            }.reversed()
        }
}
