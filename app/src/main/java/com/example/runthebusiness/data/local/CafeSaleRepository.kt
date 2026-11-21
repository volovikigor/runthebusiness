package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import com.example.runthebusiness.engine.model.*
import com.example.runthebusiness.industries.cafe.usecase.CompleteCafeSaleUseCase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Temporary diagnostic sale using 1% of the original placeholder inventory batch.
 * Real café products/recipes and their persistent catalog are a separate feature.
 */
class CafeSaleRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)
    private val accounting = db.accountingDao()
    private val setup = db.gameSetupDao()
    private val operations = db.operationsDao()
    private val locations = db.businessLocationDao()
    private val sales = db.cafeSalesDao()
    private val useCase = CompleteCafeSaleUseCase()

    suspend fun sellDemoItem(sessionId: String = "debug_session_001"): CustomerOrderEntity =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val session = checkNotNull(accounting.getSession(sessionId)) {
                    "Initialize opening game before making a sale"
                }
                val business = setup.getBusinesses(sessionId).single()
                val location = locations.getLocations(business.id).single()
                val cafe = operations.getLocations(business.id).single()
                val item = operations.getInventoryItems(business.id).single()
                val storedBatch = operations.getInventoryBatches(business.id).single()
                val currentTime = GameTime(session.currentDay, session.currentHour, session.currentMinute)
                val product = Product(
                    id = "demo_product", locationId = location.id, name = "Diagnostic sale item",
                    sellingPriceCents = 5_000L,
                    recipe = listOf(RecipeItem(item.id, 0.01))
                )
                val state = SimulationState(
                    currentTime = currentTime,
                    businesses = listOf(Business(business.id, business.name, business.ownerId,
                        Industry.valueOf(business.industry), business.currencyCode, business.isActive)),
                    locations = listOf(CafeLocation(location.id, location.businessId, location.name,
                        InteriorStyle.valueOf(cafe.interiorStyle), cafe.tableCount, cafe.seatsPerTable,
                        cafe.averageVisitMinutes, cafe.targetSeatOccupancy, cafe.takeawayShare,
                        location.openingHour, location.closingHour, location.monthlyRentCents, location.isOpen)),
                    inventory = listOf(InventoryItem(item.id, item.locationId, item.name,
                        StockUnit.valueOf(item.unit), item.reorderLevel, item.isPerishable, item.shelfLifeDays)),
                    inventoryBatches = listOf(InventoryBatch(storedBatch.id, storedBatch.inventoryItemId,
                        storedBatch.locationId, storedBatch.quantityReceived, storedBatch.quantityRemaining,
                        storedBatch.unitCostCents, GameTime(storedBatch.receivedDay, storedBatch.receivedHour,
                            storedBatch.receivedMinute), null)),
                    products = listOf(product),
                    ledgerAccounts = setup.getAccounts(sessionId).map {
                        LedgerAccount(it.code, it.name, AccountType.valueOf(it.type))
                    }
                )
                val id = UUID.randomUUID().toString()
                val order = CustomerOrder(
                    id = id, locationId = location.id, paymentMethod = PaymentMethod.CASH,
                    items = listOf(CustomerOrderLine(product.id, 1, product.sellingPriceCents)),
                    createdAt = currentTime
                )
                val result = useCase.execute(state, order)
                val updatedBatch = result.inventoryBatches.single()
                check(sales.updateBatchRemaining(
                    storedBatch.id, updatedBatch.quantityRemaining, storedBatch.quantityRemaining
                ) == 1) { "Stock changed during sale" }

                val entries = result.journalEntries
                entries.forEach { entry ->
                    val event = BusinessEventEntity(
                        id = entry.sourceEventId, sessionId = sessionId, businessId = business.id,
                        eventType = "CUSTOMER_SALE", sourceObjectType = "CUSTOMER_ORDER",
                        sourceObjectId = id, gameDay = currentTime.day, gameHour = currentTime.hour,
                        gameMinute = currentTime.minute, description = entry.description
                    )
                    val header = JournalEntryEntity(
                        id = entry.id, sessionId = sessionId, businessId = business.id,
                        sourceEventId = event.id, description = entry.description,
                        gameDay = currentTime.day, gameHour = currentTime.hour,
                        gameMinute = currentTime.minute
                    )
                    val lines = entry.lines.mapIndexed { index, line ->
                        JournalLineEntity(
                            id = "${entry.id}_${index + 1}", entryId = entry.id,
                            lineNumber = index + 1, accountCode = line.accountCode,
                            locationId = line.locationId, debitCents = line.debitCents,
                            creditCents = line.creditCents
                        )
                    }
                    accounting.saveAccountingOperation(event, header, lines)
                }
                val revenue = entries.single { it.id == "sale_$id" }
                val cogs = entries.firstOrNull { it.id == "cogs_$id" }
                val row = CustomerOrderEntity(
                    id = id, locationId = location.id, gameDay = currentTime.day,
                    gameHour = currentTime.hour, gameMinute = currentTime.minute,
                    grossCents = revenue.lines.sumOf { it.debitCents },
                    netCents = revenue.lines.first { it.accountCode == "4000" }.creditCents,
                    vatCents = revenue.lines.firstOrNull { it.accountCode == "2200" }?.creditCents ?: 0L,
                    cogsCents = cogs?.lines?.first { it.accountCode == "5000" }?.debitCents ?: 0L,
                    paymentMethod = order.paymentMethod.name, status = OrderStatus.COMPLETED.name
                )
                sales.insertOrder(row)
                row
            }
        }

    suspend fun loadDemoSales(sessionId: String = "debug_session_001"): List<CustomerOrderEntity> =
        withContext(Dispatchers.IO) {
            val business = setup.getBusinesses(sessionId).singleOrNull() ?: return@withContext emptyList()
            val location = locations.getLocations(business.id).singleOrNull() ?: return@withContext emptyList()
            sales.getOrders(location.id)
        }
}
