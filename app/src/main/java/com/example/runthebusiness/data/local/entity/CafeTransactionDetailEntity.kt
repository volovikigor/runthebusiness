package com.example.runthebusiness.data.local.entity

import androidx.room.*

/** Immutable snapshot of the product and price at sale time. */
@Entity(tableName = "customer_order_lines",
    foreignKeys = [ForeignKey(entity = CustomerOrderEntity::class, parentColumns = ["id"],
        childColumns = ["orderId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("orderId")])
data class CustomerOrderLineEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceCents: Long
)

/** Durable stock ledger: positive quantities are receipts; negative quantities are consumption. */
@Entity(tableName = "stock_movements",
    indices = [Index("inventoryItemId"), Index("batchId"), Index("sourceId")])
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val inventoryItemId: String,
    val inventoryItemName: String,
    val batchId: String,
    val sourceType: String,
    val sourceId: String,
    val quantityDelta: Double,
    val unitCostCents: Long,
    val gameDay: Int,
    val gameHour: Int,
    val gameMinute: Int
)
