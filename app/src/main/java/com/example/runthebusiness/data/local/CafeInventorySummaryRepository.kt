package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.StockMovementEntity
import kotlin.math.roundToLong

data class InventoryPeriod(
    val day: Int,
    val opening: Double,
    val purchased: Double,
    val consumed: Double,
    val writtenOff: Double,
    val other: Double,
    val closing: Double,
    val wasteCents: Long
)

data class IngredientInventory(
    val id: String,
    val name: String,
    val unit: String,
    val total: InventoryPeriod,
    val days: List<InventoryPeriod>
)

data class CafeInventorySummary(val fromDay: Int, val throughDay: Int, val items: List<IngredientInventory>)

class CafeInventorySummaryRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun load(sessionId: String = "debug_session_001"): CafeInventorySummary? = db.withTransaction {
        val today = db.accountingDao().getSession(sessionId)?.currentDay ?: return@withTransaction null
        val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull() ?: return@withTransaction null
        val fromDay = (today - 6).coerceAtLeast(1)
        val operations = db.operationsDao()
        val balances = operations.getInventoryBatches(business.id).groupBy { it.inventoryItemId }
            .mapValues { (_, batches) -> batches.sumOf { it.quantityRemaining } }
        val movements = db.cafeSalesDao().movementsSince(fromDay).filter { it.gameDay <= today }
            .groupBy { it.inventoryItemId }
        val items = operations.getInventoryItems(business.id).sortedBy { it.name }.mapNotNull { item ->
            val closing = balances[item.id] ?: 0.0
            val rows = movements[item.id].orEmpty()
            if (rows.isEmpty() && closing == 0.0) return@mapNotNull null
            val byDay = rows.groupBy { it.gameDay }
            var end = closing
            val days = (today downTo fromDay).map { day ->
                period(day, end, byDay[day].orEmpty()).also { end = it.opening }
            }
            IngredientInventory(item.id, item.name,
                when (item.unit) {
                    "GRAM" -> "g"
                    "MILLILITER" -> "ml"
                    "PIECE" -> "pcs"
                    else -> item.unit
                },
                period(today, closing, rows), days)
        }
        CafeInventorySummary(fromDay, today, items)
    }

    private fun period(day: Int, closing: Double, rows: List<StockMovementEntity>): InventoryPeriod {
        val purchased = rows.filter { it.sourceType == "INVENTORY_PURCHASE" }.sumOf { it.quantityDelta }
        val consumed = -rows.filter { it.sourceType == "CUSTOMER_ORDER" }.sumOf { it.quantityDelta }
        val writeOffs = rows.filter { it.sourceType == "WRITE_OFF" }
        val writtenOff = -writeOffs.sumOf { it.quantityDelta }
        val movement = rows.sumOf { it.quantityDelta }
        return InventoryPeriod(day, closing - movement, purchased, consumed, writtenOff,
            movement - purchased + consumed + writtenOff, closing,
            writeOffs.sumOf { (-it.quantityDelta * it.unitCostCents).roundToLong() })
    }
}
