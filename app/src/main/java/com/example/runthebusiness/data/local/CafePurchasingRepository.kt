package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MVP cash purchases: one fixed supplier pack, immediately received into stock.
 * Stock purchase capitalizes inventory (1400) and pays cash (1000); COGS
 * is recognized only when a subsequent customer sale consumes the batch.
 */
class CafePurchasingRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)

    data class Supply(val itemId: String, val label: String, val quantity: Double, val unitCostCents: Long, val unit: String, val shelfLifeDays: Int = 0) {
        val costCents: Long get() = (quantity * unitCostCents).toLong()
    }

    companion object {
        val supplies = listOf(
            Supply("stock_beans_001", "Coffee beans", 1000.0, 4L, "g"),
            Supply("stock_milk_001", "Milk", 10000.0, 1L, "ml"),
            Supply("stock_croissants_001", "Croissants", 100.0, 160L, "pcs")
        ) + CafeExpandedMenuRepository.offerings.map {
            Supply(it.stockId, it.name, it.packSize, it.costCents, "pcs", it.shelfLifeDays)
        }
    }

    suspend fun stock(sessionId: String = "debug_session_001"): Map<String, Double> =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext emptyMap()
            val batches = db.operationsDao().getInventoryBatches(business.id)
            batches.groupBy { it.inventoryItemId }.mapValues { (_, rows) ->
                rows.sumOf { it.quantityRemaining }
            }
        }

    suspend fun purchase(itemId: String, sessionId: String = "debug_session_001"): Long =
        withContext(Dispatchers.IO) {
            val supply = supplies.singleOrNull { it.itemId == itemId }
                ?: error("Unknown supplier pack")
            db.withTransaction {
                val session = checkNotNull(db.accountingDao().getSession(sessionId)) { "Start the café first" }
                val business = db.gameSetupDao().getBusinesses(sessionId).single()
                val location = db.businessLocationDao().getLocations(business.id).single()
                require(db.operationsDao().getInventoryItems(business.id).any { it.id == itemId }) {
                    "Install real ingredients before purchasing supplies"
                }
                val cash = db.accountingDao().accountBalance(sessionId, "1000")
                val bank = db.accountingDao().accountBalance(sessionId, "1100")
                require(cash >= supply.costCents || bank >= supply.costCents) {
                    "Insufficient funds: supplier pack costs €%.2f".format(
                        java.util.Locale.ROOT, supply.costCents / 100.0)
                }
                val paymentAccount = if (cash >= supply.costCents) "1000" else "1100"
                val id = UUID.randomUUID().toString()
                db.operationsDao().insertInventoryBatches(listOf(
                    InventoryBatchEntity(
                        id = "purchase_batch_$id",
                        inventoryItemId = itemId,
                        locationId = location.id,
                        quantityReceived = supply.quantity,
                        quantityRemaining = supply.quantity,
                        unitCostCents = supply.unitCostCents,
                        receivedDay = session.currentDay,
                        receivedHour = session.currentHour,
                        receivedMinute = session.currentMinute,
                        expiresDay = if (supply.shelfLifeDays > 0)
                            session.currentDay + supply.shelfLifeDays else null,
                        expiresHour = if (supply.shelfLifeDays > 0) 0 else null,
                        expiresMinute = if (supply.shelfLifeDays > 0) 0 else null
                    )
                ))
                db.cafeSalesDao().insertStockMovements(listOf(StockMovementEntity(
                    id = "purchase_movement_$id",
                    inventoryItemId = itemId, inventoryItemName = supply.label,
                    batchId = "purchase_batch_$id",
                    sourceType = "INVENTORY_PURCHASE", sourceId = "purchase_batch_$id",
                    quantityDelta = supply.quantity, unitCostCents = supply.unitCostCents,
                    gameDay = session.currentDay, gameHour = session.currentHour,
                    gameMinute = session.currentMinute
                )))
                val eventId = "purchase_event_$id"
                val entryId = "purchase_$id"
                val description = "Cash purchase: ${supply.label}, ${supply.quantity} ${supply.unit}"
                db.accountingDao().saveAccountingOperation(
                    BusinessEventEntity(
                        id = eventId, sessionId = sessionId, businessId = business.id,
                        eventType = "INVENTORY_PURCHASE", sourceObjectType = "INVENTORY_BATCH",
                        sourceObjectId = "purchase_batch_$id",
                        gameDay = session.currentDay, gameHour = session.currentHour,
                        gameMinute = session.currentMinute, description = description
                    ),
                    JournalEntryEntity(
                        id = entryId, sessionId = sessionId, businessId = business.id,
                        sourceEventId = eventId, description = description,
                        gameDay = session.currentDay, gameHour = session.currentHour,
                        gameMinute = session.currentMinute
                    ),
                    listOf(
                        JournalLineEntity(
                            id = "${entryId}_1", entryId = entryId, lineNumber = 1,
                            accountCode = "1400", locationId = location.id,
                            debitCents = supply.costCents, creditCents = 0L
                        ),
                        JournalLineEntity(
                            id = "${entryId}_2", entryId = entryId, lineNumber = 2,
                            accountCode = paymentAccount, locationId = location.id,
                            debitCents = 0L, creditCents = supply.costCents
                        )
                    )
                )
                supply.costCents
            }
        }
}
