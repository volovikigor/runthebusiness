package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import com.example.runthebusiness.engine.model.GameTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/** Expired inventory is removed once, with a durable movement and balanced journal. */
class CafeSpoilageRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun expire(now: GameTime, sessionId: String = "debug_session_001"): Int =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext 0
            val items = db.operationsDao().getInventoryItems(business.id).associateBy { it.id }
            val batches = db.operationsDao().getInventoryBatches(business.id)
                .filter { batch ->
                    batch.quantityRemaining > 0.000001 && batch.expiresDay != null &&
                        GameTime(batch.expiresDay, batch.expiresHour ?: 0,
                            batch.expiresMinute ?: 0).totalMinutes <= now.totalMinutes
                }
            var expired = 0
            for (batch in batches) {
                db.withTransaction {
                    val quantity = batch.quantityRemaining
                    if (db.cafeSalesDao().updateBatchRemaining(batch.id, 0.0, quantity) != 1)
                        return@withTransaction
                    val item = checkNotNull(items[batch.inventoryItemId])
                    val cost = (quantity * batch.unitCostCents).roundToLong()
                    val id = "expiry_${batch.id}"
                    db.cafeSalesDao().insertStockMovements(listOf(StockMovementEntity(
                        id = "movement_$id", inventoryItemId = item.id,
                        inventoryItemName = item.name, batchId = batch.id,
                        sourceType = "WRITE_OFF", sourceId = id,
                        quantityDelta = -quantity, unitCostCents = batch.unitCostCents,
                        gameDay = now.day, gameHour = now.hour, gameMinute = now.minute
                    )))
                    if (cost > 0L) {
                        val entryId = "journal_$id"
                        val description = "Expired stock · ${item.name} · ${quantity} ${item.unit}"
                        db.accountingDao().saveAccountingOperation(
                            BusinessEventEntity(
                                id = "event_$id", sessionId = sessionId, businessId = business.id,
                                eventType = "INVENTORY_WRITE_OFF",
                                sourceObjectType = "INVENTORY_BATCH", sourceObjectId = batch.id,
                                gameDay = now.day, gameHour = now.hour, gameMinute = now.minute,
                                description = description
                            ),
                            JournalEntryEntity(
                                id = entryId, sessionId = sessionId, businessId = business.id,
                                sourceEventId = "event_$id", description = description,
                                gameDay = now.day, gameHour = now.hour, gameMinute = now.minute
                            ),
                            listOf(
                                JournalLineEntity(
                                    id = "${entryId}_1", entryId = entryId, lineNumber = 1,
                                    accountCode = "5100", locationId = batch.locationId,
                                    debitCents = cost, creditCents = 0L
                                ),
                                JournalLineEntity(
                                    id = "${entryId}_2", entryId = entryId, lineNumber = 2,
                                    accountCode = "1400", locationId = batch.locationId,
                                    debitCents = 0L, creditCents = cost
                                )
                            )
                        )
                    }
                    expired++
                }
            }
            expired
        }
}
