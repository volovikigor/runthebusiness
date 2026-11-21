package com.example.runthebusiness.data.local.entity
import androidx.room.*

@Entity(tableName = "cafe_locations",
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("businessId")])
data class CafeLocationEntity(
    @PrimaryKey val id: String, val businessId: String, val name: String,
    val interiorStyle: String, val tableCount: Int, val seatsPerTable: Int,
    val averageVisitMinutes: Int, val targetSeatOccupancy: Double, val takeawayShare: Double,
    val openingHour: Int, val closingHour: Int, val monthlyRentCents: Long, val isOpen: Boolean
)
