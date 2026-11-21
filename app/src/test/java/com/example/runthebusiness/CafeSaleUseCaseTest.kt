package com.example.runthebusiness

import com.example.runthebusiness.engine.InitialGameFactory
import com.example.runthebusiness.engine.model.*
import com.example.runthebusiness.industries.cafe.usecase.CompleteCafeSaleUseCase
import org.junit.Assert.*
import org.junit.Test

class CafeSaleUseCaseTest {
    private fun startingState(): SimulationState {
        val state = InitialGameFactory.create()
        val locationId = state.locations.single().id
        val item = state.inventory.single()
        return state.copy(
            products = listOf(Product(
                id = "espresso", locationId = locationId, name = "Espresso",
                sellingPriceCents = 330L,
                recipe = listOf(RecipeItem(item.id, 1.0))
            )),
            inventoryBatches = listOf(state.inventoryBatches.single().copy(
                quantityReceived = 10.0, quantityRemaining = 10.0, unitCostCents = 80L
            ))
        )
    }

    private fun order(id: String = "order_1", quantity: Int = 2): CustomerOrder =
        CustomerOrder(
            id = id, locationId = "cafe_001", paymentMethod = PaymentMethod.CASH,
            items = listOf(CustomerOrderLine("espresso", quantity, 330L))
        )

    @Test fun salePostsVatAndCogsAndReducesStock() {
        val initial = startingState()
        val result = CompleteCafeSaleUseCase().execute(initial, order())
        assertEquals(8.0, result.inventoryBatches.single().quantityRemaining, 0.000001)
        assertEquals(10.0, initial.inventoryBatches.single().quantityRemaining, 0.000001)
        assertEquals(OrderStatus.COMPLETED, result.customerOrders.single().status)
        val revenue = result.journalEntries.first { it.id == "sale_order_1" }
        assertEquals(660L, revenue.lines.sumOf { it.debitCents })
        assertEquals(600L, revenue.lines.first { it.accountCode == "4000" }.creditCents)
        assertEquals(60L, revenue.lines.first { it.accountCode == "2200" }.creditCents)
        val cogs = result.journalEntries.first { it.id == "cogs_order_1" }
        assertEquals(160L, cogs.lines.first { it.accountCode == "5000" }.debitCents)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversellingWithoutChangingInput() {
        CompleteCafeSaleUseCase().execute(startingState(), order(quantity = 11))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateOrder() {
        val service = CompleteCafeSaleUseCase()
        val once = service.execute(startingState(), order())
        service.execute(once, order())
    }
}
