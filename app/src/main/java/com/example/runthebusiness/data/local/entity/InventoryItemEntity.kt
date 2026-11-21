package com.example.runthebusiness.data.local.entity
import androidx.room.*

@Entity(tableName = "inventory_items",
    foreignKeys = [ForeignKey(entity = BusinessLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("locationId")])
data class InventoryItemEntity(
    @PrimaryKey val id: String, val locationId: String, val name: String,
    val unit: String, val reorderLevel: Double, val isPerishable: Boolean, val shelfLifeDays: Int
)
