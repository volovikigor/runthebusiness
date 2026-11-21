package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.industries.cafe.simulation.CafeSeatingRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Processes pending arrivals oldest-first. Each group gets one order if there is room
 * and at least one product can be prepared. Sales and arrival status share a transaction.
 * Pending events survive process death and can be retried without double-selling.
 */
class CafeServiceRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)
    private val sale = CafeSaleRepository(context)

    suspend fun processPending(sessionId: String = "debug_session_001"): Int =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext 0
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext 0
            val cafe = db.operationsDao().getLocations(business.id).singleOrNull()
                ?: return@withContext 0
            val arrivalDao = db.cafeArrivalDao()
            var served = 0
            for (arrival in arrivalDao.pending(location.id)) {
                db.withTransaction {
                    val now = GameTime(arrival.gameDay, arrival.gameHour, arrival.gameMinute)
                    val visits = arrivalDao.history(location.id).map {
                        CafeSeatingRules.Visit(
                            GameTime(it.gameDay, it.gameHour, it.gameMinute).totalMinutes,
                            it.guests, it.status == "SERVED"
                        )
                    }
                    val seats = CafeSeatingRules.availableSeats(
                        now.totalMinutes, cafe.tableCount * cafe.seatsPerTable,
                        cafe.averageVisitMinutes, visits
                    )
                    if (seats < arrival.guests) {
                        check(arrivalDao.markProcessed(arrival.id, "NO_SEATS") == 1)
                    } else {
                        val products = db.cafeMenuDao().getProducts(location.id)
                            .filter { it.isAvailable }
                            .sortedBy { it.id }
                        var sold = false
                        // Stable rotation prevents every group from choosing the same product.
                        val offset = ((now.totalMinutes + arrival.guests).rem(
                            products.size.coerceAtLeast(1).toLong())).toInt()
                        for (product in products.drop(offset) + products.take(offset)) {
                            try {
                                sale.sellMenuItem(product.id, sessionId, now)
                                sold = true
                                break
                            } catch (e: IllegalArgumentException) {
                                // Only inventory shortage is an expected fallback condition.
                                if (e.message?.startsWith("Insufficient stock:") != true) throw e
                            }
                        }
                        check(arrivalDao.markProcessed(arrival.id,
                            if (sold) "SERVED" else "NO_STOCK") == 1)
                        if (sold) served++
                    }
                }
            }
            served
        }
}
