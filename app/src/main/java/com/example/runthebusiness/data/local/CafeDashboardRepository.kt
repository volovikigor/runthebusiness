package com.example.runthebusiness.data.local

import android.content.Context
import com.example.runthebusiness.engine.model.GameTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val stockOriginal: Double
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
            CafeDashboardSnapshot(
                time = GameTime(session.currentDay, session.currentHour, session.currentMinute),
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
                stockOriginal = batches.sumOf { it.quantityReceived }
            )
        }
}
