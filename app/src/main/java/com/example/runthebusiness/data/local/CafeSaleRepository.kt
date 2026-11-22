package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import com.example.runthebusiness.engine.model.*
import com.example.runthebusiness.industries.cafe.usecase.CompleteCafeSaleUseCase
import com.example.runthebusiness.industries.cafe.model.CafeFulfillment
import com.example.runthebusiness.industries.cafe.model.CafeFulfillmentRules
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
    private val menu = db.cafeMenuDao()
    private val useCase = CompleteCafeSaleUseCase()

    suspend fun sellDemoItem(sessionId: String = "debug_session_001"): CustomerOrderEntity =
        sellMenuItem(null, sessionId)

    suspend fun sellMenuItem(productId: String?, sessionId: String = "debug_session_001", saleTime: GameTime? = null): CustomerOrderEntity =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val session = checkNotNull(accounting.getSession(sessionId)) {
                    "Initialize opening game before making a sale"
                }
                val business = setup.getBusinesses(sessionId).single()
                val location = locations.getLocations(business.id).single()
                val cafe = operations.getLocations(business.id).single()
                val items = operations.getInventoryItems(business.id)
                val storedBatches = operations.getInventoryBatches(business.id)
                val currentTime = saleTime ?: GameTime(session.currentDay, session.currentHour, session.currentMinute)
                val product = if (productId == null) {
                    Product(
                        id = "demo_product", locationId = location.id, name = "Diagnostic sale item",
                        sellingPriceCents = 5_000L,
                        recipe = listOf(RecipeItem("inventory_001", 0.01))
                    )
                } else {
                    val saved = menu.getProducts(location.id).firstOrNull { it.id == productId }
                        ?: error("Unknown menu item: $productId")
                    val recipes = menu.getRecipe(saved.id).map { RecipeItem(it.ingredientId, it.quantity) }
                    val fulfillment = CafeFulfillment.valueOf(saved.fulfillment)
                    CafeFulfillmentRules.validate(fulfillment, recipes.size, saved.finishedStockItemId)
                    require(fulfillment != CafeFulfillment.BATCH_PRODUCED) {
                        "Batch production is not implemented"
                    }
                    Product(
                        id = saved.id, locationId = saved.locationId, name = saved.name,
                        type = ProductType.valueOf(saved.type),
                        sellingPriceCents = saved.sellingPriceCents,
                        taxCategoryCode = saved.taxCategoryCode,
                        preparationMinutes = saved.preparationMinutes,
                        recipe = if (fulfillment == CafeFulfillment.RESALE) {
                            listOf(RecipeItem(requireNotNull(saved.finishedStockItemId), 1.0))
                        } else recipes,
                        isAvailable = saved.isAvailable
                    )
                }
                val state = SimulationState(
                    currentTime = currentTime,
                    businesses = listOf(Business(business.id, business.name, business.ownerId,
                        Industry.valueOf(business.industry), business.currencyCode, business.isActive)),
                    locations = listOf(CafeLocation(location.id, location.businessId, location.name,
                        InteriorStyle.valueOf(cafe.interiorStyle), cafe.tableCount, cafe.seatsPerTable,
                        cafe.averageVisitMinutes, cafe.targetSeatOccupancy, cafe.takeawayShare,
                        location.openingHour, location.closingHour, location.monthlyRentCents, location.isOpen)),
                    inventory = items.map { item ->
                        InventoryItem(item.id, item.locationId, item.name,
                            StockUnit.valueOf(item.unit), item.reorderLevel, item.isPerishable, item.shelfLifeDays)
                    },
                    inventoryBatches = storedBatches.map { batch ->
                        InventoryBatch(batch.id, batch.inventoryItemId, batch.locationId,
                            batch.quantityReceived, batch.quantityRemaining, batch.unitCostCents,
                            GameTime(batch.receivedDay, batch.receivedHour, batch.receivedMinute),
                            if (batch.expiresDay == null) null else GameTime(
                                batch.expiresDay, requireNotNull(batch.expiresHour),
                                requireNotNull(batch.expiresMinute)))
                    },
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
                val previousById = storedBatches.associateBy { it.id }
                val movementRows = mutableListOf<StockMovementEntity>()
                result.inventoryBatches.filter { updated ->
                    updated.quantityRemaining != previousById.getValue(updated.id).quantityRemaining
                }.forEach { updated ->
                    val previous = previousById.getValue(updated.id)
                    val ingredient = items.firstOrNull { it.id == updated.inventoryItemId }
                    movementRows += StockMovementEntity(
                        id = "sale_movement_${id}_${updated.id}",
                        inventoryItemId = updated.inventoryItemId,
                        inventoryItemName = ingredient?.name ?: updated.inventoryItemId,
                        batchId = updated.id,
                        sourceType = "CUSTOMER_ORDER", sourceId = id,
                        quantityDelta = updated.quantityRemaining - previous.quantityRemaining,
                        unitCostCents = updated.unitCostCents,
                        gameDay = currentTime.day, gameHour = currentTime.hour,
                        gameMinute = currentTime.minute
                    )
                    check(sales.updateBatchRemaining(
                        updated.id, updated.quantityRemaining, previous.quantityRemaining
                    ) == 1) { "Stock changed during sale: ${updated.id}" }
                }

                val entries = result.journalEntries
                entries.forEach { entry ->
                    val readableDescription = when {
                        entry.id.startsWith("sale_") -> "Sold 1 × ${product.name}"
                        entry.id.startsWith("cogs_") -> "Inventory consumed · 1 × ${product.name}"
                        else -> entry.description
                    }
                    val event = BusinessEventEntity(
                        id = entry.sourceEventId, sessionId = sessionId, businessId = business.id,
                        eventType = "CUSTOMER_SALE", sourceObjectType = "CUSTOMER_ORDER",
                        sourceObjectId = id, gameDay = currentTime.day, gameHour = currentTime.hour,
                        gameMinute = currentTime.minute, description = readableDescription
                    )
                    val header = JournalEntryEntity(
                        id = entry.id, sessionId = sessionId, businessId = business.id,
                        sourceEventId = event.id, description = readableDescription,
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
                sales.insertOrderLine(CustomerOrderLineEntity(
                    id = "order_line_$id", orderId = id,
                    productId = product.id, productName = product.name,
                    quantity = 1, unitPriceCents = product.sellingPriceCents
                ))
                if (movementRows.isNotEmpty()) sales.insertStockMovements(movementRows)
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
