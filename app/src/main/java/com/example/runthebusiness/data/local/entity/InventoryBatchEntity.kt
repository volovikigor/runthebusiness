package com.example.runthebusiness.data.local.entity
import androidx.room.*

@Entity(tableName = "inventory_batches",
    foreignKeys = [
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["inventoryItemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = BusinessLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("inventoryItemId"), Index("locationId")])
data class InventoryBatchEntity(
    @PrimaryKey val id: String, val inventoryItemId: String, val locationId: String,
    val quantityReceived: Double, val quantityRemaining: Double, val unitCostCents: Long,
    val receivedDay: Int, val receivedHour: Int, val receivedMinute: Int,
    val expiresDay: Int?, val expiresHour: Int?, val expiresMinute: Int?
)
