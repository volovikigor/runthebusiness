package com.example.runthebusiness.data.local.entity
import androidx.room.*

@Entity(tableName = "equipment",
    foreignKeys = [ForeignKey(entity = BusinessLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("locationId")])
data class EquipmentEntity(
    @PrimaryKey val id: String, val locationId: String, val name: String,
    val purchaseCostCents: Long, val usefulLifeMonths: Int, val conditionPercent: Int,
    val capacityPerHour: Int, val accumulatedDepreciationCents: Long, val residualValueCents: Long,
    val placedInServiceDay: Int, val placedInServiceHour: Int, val placedInServiceMinute: Int,
    val isOperational: Boolean
)
