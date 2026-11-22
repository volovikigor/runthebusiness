package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** One-unit manual spoilage/write-off; FIFO by receipt timestamp. */
class CafeWriteOffRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun writeOffOne(itemId: String, sessionId: String = "debug_session_001"): Long =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val session = checkNotNull(db.accountingDao().getSession(sessionId))
                val business = db.gameSetupDao().getBusinesses(sessionId).single()
                val location = db.businessLocationDao().getLocations(business.id).single()
                val item = db.operationsDao().getInventoryItems(business.id)
                    .singleOrNull { it.id == itemId } ?: error("Unknown inventory item")
                val batch = db.operationsDao().getInventoryBatches(business.id)
                    .filter { it.inventoryItemId == itemId && it.quantityRemaining >= 1.0 }
                    .sortedWith(compareBy({ it.receivedDay }, { it.receivedHour },
                        { it.receivedMinute }, { it.id })).firstOrNull()
                    ?: error("No full unit available to write off")
                val cost = batch.unitCostCents
                require(cost > 0L) { "Zero-value write-offs are not supported" }
                check(db.cafeSalesDao().updateBatchRemaining(
                    batch.id, batch.quantityRemaining - 1.0, batch.quantityRemaining
                ) == 1) { "Inventory changed; retry" }
                val id = UUID.randomUUID().toString()
                db.cafeSalesDao().insertStockMovements(listOf(StockMovementEntity(
                    id = "writeoff_movement_$id",
                    inventoryItemId = itemId, inventoryItemName = item.name,
                    batchId = batch.id, sourceType = "WRITE_OFF", sourceId = id,
                    quantityDelta = -1.0, unitCostCents = cost,
                    gameDay = session.currentDay, gameHour = session.currentHour,
                    gameMinute = session.currentMinute
                )))
                val entryId = "writeoff_$id"
                val eventId = "writeoff_event_$id"
                val description = "Spoilage · 1 ${item.unit} of ${item.name}"
                db.accountingDao().saveAccountingOperation(
                    BusinessEventEntity(
                        id = eventId, sessionId = sessionId, businessId = business.id,
                        eventType = "INVENTORY_WRITE_OFF", sourceObjectType = "INVENTORY_ITEM",
                        sourceObjectId = itemId, gameDay = session.currentDay,
                        gameHour = session.currentHour, gameMinute = session.currentMinute,
                        description = description
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
                            accountCode = "5100", locationId = location.id,
                            debitCents = cost, creditCents = 0L
                        ),
                        JournalLineEntity(
                            id = "${entryId}_2", entryId = entryId, lineNumber = 2,
                            accountCode = "1400", locationId = location.id,
                            debitCents = 0L, creditCents = cost
                        )
                    )
                )
                cost
            }
        }
}
