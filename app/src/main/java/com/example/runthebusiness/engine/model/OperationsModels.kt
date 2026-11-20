
package com.example.runthebusiness.engine.model

enum class ProductType {
    DRINK, FOOD, PASTRY
}

enum class StockUnit {
    PIECE, GRAM, MILLILITER
}

enum class PurchaseStatus {
    CREATED, CONFIRMED, DELIVERED, CANCELLED
}

enum class OrderStatus {
    PLACED, PREPARING, COMPLETED, CANCELLED
}

enum class StockWriteOffReason {
    EXPIRED, DAMAGED, SPOILED, LOST, OTHER
}

data class Product(
    var id: String = "",
    var locationId: String = "",
    var name: String = "",
    var type: ProductType = ProductType.DRINK,
    var sellingPriceCents: Long = 0L,
    var taxCategoryCode: String = "ES_HOSPITALITY",
    var preparationMinutes: Int = 2,
    var recipe: List<RecipeItem> = emptyList(),
    var isAvailable: Boolean = true
)

data class RecipeItem(
    var ingredientId: String = "",
    var quantity: Double = 0.0
)

data class InventoryItem(
    var id: String = "",
    var locationId: String = "",
    var name: String = "",
    var unit: StockUnit = StockUnit.PIECE,
    var reorderLevel: Double = 0.0,
    var isPerishable: Boolean = false,
    var shelfLifeDays: Int = 0
)

data class InventoryBatch(
    var id: String = "",
    var inventoryItemId: String = "",
    var locationId: String = "",
    var quantityReceived: Double = 0.0,
    var quantityRemaining: Double = 0.0,
    var unitCostCents: Long = 0L,
    var receivedAt: GameTime = GameTime(),
    var expiresAt: GameTime? = null
)

data class Supplier(
    var id: String = "",
    var name: String = "",
    var paymentTermsDays: Int = 7,
    var reliability: Double = 1.0,
    var isActive: Boolean = true
)

data class PurchaseOrderLine(
    var inventoryItemId: String = "",
    var quantity: Double = 0.0,
    var unitPriceCents: Long = 0L
)

data class PurchaseOrder(
    var id: String = "",
    var locationId: String = "",
    var supplierId: String = "",
    var items: List<PurchaseOrderLine> = emptyList(),
    var createdAt: GameTime = GameTime(),
    var expectedDeliveryAt: GameTime = GameTime(),
    var status: PurchaseStatus = PurchaseStatus.CREATED
)

data class CustomerOrderLine(
    var productId: String = "",
    var quantity: Int = 0,
    var unitPriceCents: Long = 0L
)

data class CustomerOrder(
    var id: String = "",
    var locationId: String = "",
    var items: List<CustomerOrderLine> = emptyList(),
    var customerCount: Int = 1,
    var serviceType: ServiceType = ServiceType.DINE_IN,
    var paymentMethod: PaymentMethod = PaymentMethod.BANK_CARD,
    var createdAt: GameTime = GameTime(),
    var status: OrderStatus = OrderStatus.PLACED
)
enum class ServiceType {
    DINE_IN,
    TAKEAWAY
}
data class StockWriteOff(
    var id: String = "",
    var locationId: String = "",
    var batchId: String = "",
    var quantity: Double = 0.0,
    var costCents: Long = 0L,
    var reason: StockWriteOffReason = StockWriteOffReason.EXPIRED,
    var occurredAt: GameTime = GameTime(),
    var journalEntryId: String = ""
)
