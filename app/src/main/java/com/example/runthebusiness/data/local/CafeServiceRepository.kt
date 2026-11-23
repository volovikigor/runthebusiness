package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.industries.cafe.simulation.CafeSeatingRules
import com.example.runthebusiness.industries.cafe.simulation.CafePriceDemandRules
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
    private val consequences = CafePaymentConsequencesRepository(context)

    suspend fun processPending(sessionId: String = "debug_session_001"): Int =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext 0
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext 0
            val cafe = db.operationsDao().getLocations(business.id).singleOrNull()
                ?: return@withContext 0
            val arrivalDao = db.cafeArrivalDao()
            val pending = arrivalDao.pending(location.id)
            if (pending.isEmpty()) return@withContext 0
            val impact = consequences.load(sessionId)
            var served = 0
            // Build the seating timeline once per batch rather than querying the
            // entire growing arrival history for every group (quadratic work).
            val earliestDay = pending.first().gameDay
            val lookbackDays = (cafe.averageVisitMinutes / 1440) + 2
            val visits = arrivalDao.recentServed(location.id,
                (earliestDay - lookbackDays).coerceAtLeast(1))
                .map { CafeSeatingRules.Visit(
                    GameTime(it.gameDay, it.gameHour, it.gameMinute).totalMinutes,
                    it.guests, true
                ) }.toMutableList()
            for (arrival in pending) {
                db.withTransaction {
                    val now = GameTime(arrival.gameDay, arrival.gameHour, arrival.gameMinute)
                    val seats = CafeSeatingRules.availableSeats(
                        now.totalMinutes, cafe.tableCount * cafe.seatsPerTable,
                        cafe.averageVisitMinutes, visits
                    )
                    // Overdue rent blocks trading until settled; overdue payroll loses
                    // roughly one in three groups through predictable service disruption.
                    val disrupted = impact.staffDisruption &&
                        Math.floorMod(arrival.id.hashCode(), 3) == 0
                    if (impact.rentShutdown) {
                        check(arrivalDao.markProcessed(arrival.id, "RENT_SHUTDOWN") == 1)
                    } else if (disrupted) {
                        check(arrivalDao.markProcessed(arrival.id, "STAFF_DISRUPTION") == 1)
                    } else if (seats < arrival.guests) {
                        check(arrivalDao.markProcessed(arrival.id, "NO_SEATS") == 1)
                    } else {
                        val products = db.cafeMenuDao().getProducts(location.id)
                            .filter { it.isAvailable }
                            .sortedBy { it.id }
                        var sold = false
                        var offered = false
                        val offset = ((now.totalMinutes + arrival.guests).rem(
                            products.size.coerceAtLeast(1).toLong())).toInt()
                        val batches = db.operationsDao().getInventoryBatches(business.id)
                        val stock = batches.groupBy { it.inventoryItemId }
                            .mapValues { (_, rows) -> rows.sumOf { it.quantityRemaining } }
                            .toMutableMap()
                        val basket = mutableListOf<String>()
                        val drinks = products.filter { it.type == "DRINK" }
                        val pastries = products.filter { it.type == "PASTRY" }
                        suspend fun tryAdd(product: com.example.runthebusiness.data.local.entity.CafeProductEntity): Boolean {
                            if (product.fulfillment == "BATCH_PRODUCED") return false
                            val required = if (product.fulfillment == "RESALE") {
                                listOf(requireNotNull(product.finishedStockItemId) to 1.0)
                            } else {
                                db.cafeMenuDao().getRecipe(product.id).map { it.ingredientId to it.quantity }
                            }
                            if (required.isEmpty()) return false
                            if (!required.all { (ingredient, amount) ->
                                (stock[ingredient] ?: 0.0) + 1e-8 >= amount
                            }) return false
                            offered = true
                            if (!CafePriceDemandRules.accepts(
                                    arrival.id + "_" + basket.size, product.id, product.sellingPriceCents
                                )) return false
                            required.forEach { (ingredient, amount) ->
                                stock[ingredient] = (stock[ingredient] ?: 0.0) - amount
                            }
                            basket.add(product.id)
                            return true
                        }
                        repeat(arrival.guests) { guest ->
                            if (drinks.isNotEmpty()) {
                                val shift = (offset + guest) % drinks.size
                                val rotated = drinks.drop(shift) + drinks.take(shift)
                                for (drink in rotated) if (tryAdd(drink)) break
                            }
                        }
                        if (pastries.isNotEmpty() && (arrival.guests > 1 ||
                                Math.floorMod(arrival.id.hashCode(), 2) == 0)) {
                            val shift = offset % pastries.size
                            val rotated = pastries.drop(shift) + pastries.take(shift)
                            for (pastry in rotated) if (tryAdd(pastry)) break
                        }
                        // Lunch groups may add a meal; afternoon groups prefer dessert.
                        // These are separate optional choices, not guaranteed purchases.
                        val category = if (now.hour in 11..15) "FOOD" else "DESSERT"
                        val extras = products.filter { it.type == category }
                        if (extras.isNotEmpty()) {
                            val shift = offset % extras.size
                            val rotated = extras.drop(shift) + extras.take(shift)
                            for (extra in rotated) if (tryAdd(extra)) break
                        }
                        if (basket.isNotEmpty()) {
                            sale.sellBasket(basket, sessionId, now)
                            sold = true
                        }
                        check(arrivalDao.markProcessed(arrival.id,
                            if (sold) "SERVED" else if (offered) "NO_PURCHASE" else "NO_STOCK") == 1)
                        if (sold) {
                            served++
                            visits.add(CafeSeatingRules.Visit(now.totalMinutes, arrival.guests, true))
                        }
                    }
                }
            }
            served
        }
}
