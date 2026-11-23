package com.example.runthebusiness.industries.cafe.usecase

import com.example.runthebusiness.engine.accounting.AccountingEngine
import com.example.runthebusiness.engine.model.*
import java.math.BigDecimal
import java.math.RoundingMode

/** One atomic *domain* sale calculation. Persistence is a separate repository responsibility. */
class CompleteCafeSaleUseCase(private val accounting: AccountingEngine = AccountingEngine()) {
    fun execute(
        state: SimulationState,
        order: CustomerOrder,
        vatRatePercent: Int = 10
    ): SimulationState {
        require(vatRatePercent in 0..100)
        require(order.id.isNotBlank() && order.locationId.isNotBlank())
        require(order.status == OrderStatus.PLACED)
        require(order.items.isNotEmpty() && order.items.all { it.quantity > 0 })
        require(state.locations.any { it.id == order.locationId })
        require(state.customerOrders.none { it.id == order.id })
        require(state.journalEntries.none { it.sourceObjectType == "CUSTOMER_ORDER" && it.sourceObjectId == order.id })
        val products = state.products.associateBy { it.id }
        val required = mutableMapOf<String, BigDecimal>()
        var grossCents = 0L
        order.items.forEach { line ->
            val product = requireNotNull(products[line.productId]) { "Unknown product: ${line.productId}" }
            require(product.locationId == order.locationId && product.isAvailable)
            require(product.sellingPriceCents >= 0L)
            require(line.unitPriceCents == product.sellingPriceCents) { "Order price must match product price" }
            grossCents = Math.addExact(grossCents, Math.multiplyExact(line.unitPriceCents, line.quantity.toLong()))
            product.recipe.forEach { recipe ->
                require(recipe.quantity > 0 && recipe.quantity.isFinite())
                val amount = BigDecimal.valueOf(recipe.quantity).multiply(BigDecimal.valueOf(line.quantity.toLong()))
                required[recipe.ingredientId] = (required[recipe.ingredientId] ?: BigDecimal.ZERO).add(amount)
            }
        }
        require(grossCents > 0L)
        val batches = state.inventoryBatches.map { it.copy() }.toMutableList()
        var costCents = 0L
        required.forEach { (ingredientId, quantity) ->
            require(state.inventory.any { it.id == ingredientId && it.locationId == order.locationId })
            var remaining = quantity
            val candidates = batches.withIndex()
                .filter { it.value.inventoryItemId == ingredientId && it.value.locationId == order.locationId }
                .sortedWith(compareBy({ it.value.receivedAt.totalMinutes }, { it.value.id }))
            for ((index, batch) in candidates) {
                if (remaining.signum() == 0) break
                val available = BigDecimal.valueOf(batch.quantityRemaining)
                if (available.signum() <= 0) continue
                val used = remaining.min(available)
                // Cost rounding is per consumed batch; accounting amounts remain integral cents.
                val cost = used.multiply(BigDecimal.valueOf(batch.unitCostCents))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact()
                costCents = Math.addExact(costCents, cost)
                batches[index] = batch.copy(quantityRemaining = available.subtract(used).toDouble())
                remaining = remaining.subtract(used)
            }
            require(remaining.signum() == 0) { "Insufficient stock: $ingredientId" }
        }
        val netCents = BigDecimal.valueOf(grossCents)
            .multiply(BigDecimal.valueOf(100L))
            .divide(BigDecimal.valueOf(100L + vatRatePercent), 0, RoundingMode.HALF_UP)
            .longValueExact()
        val vatCents = Math.subtractExact(grossCents, netCents)
        val receivableAccount = when (order.paymentMethod) {
            PaymentMethod.CASH -> "1000"
            PaymentMethod.BANK_CARD -> "1100"
            else -> error("Unsupported payment method: ${order.paymentMethod}")
        }
        val revenueEntry = JournalEntry(
            id = "sale_${order.id}", businessId = state.businesses.single().id,
            timestamp = state.currentTime, description = "Sale ${order.id}",
            sourceEventId = "sale_event_${order.id}",
            sourceObjectType = "CUSTOMER_ORDER", sourceObjectId = order.id,
            lines = listOf(
                JournalLine(accountCode = receivableAccount, debitCents = grossCents),
                JournalLine(accountCode = "4000", creditCents = netCents)
            ) + if (vatCents > 0L) listOf(JournalLine(accountCode = "2200", creditCents = vatCents)) else emptyList()
        )
        var next = accounting.post(state, revenueEntry)
        if (costCents > 0L) {
            next = accounting.post(next, JournalEntry(
                id = "cogs_${order.id}", businessId = state.businesses.single().id,
                timestamp = state.currentTime, description = "Cost of sale ${order.id}",
                sourceEventId = "cogs_event_${order.id}",
                sourceObjectType = "CUSTOMER_ORDER", sourceObjectId = order.id,
                lines = listOf(
                    JournalLine(accountCode = "5000", debitCents = costCents),
                    JournalLine(accountCode = "1400", creditCents = costCents)
                )
            ))
        }
        return next.copy(
            inventoryBatches = batches,
            customerOrders = next.customerOrders + order.copy(status = OrderStatus.COMPLETED)
        )
    }
}
