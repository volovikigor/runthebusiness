package com.example.runthebusiness.data.local.entity
import androidx.room.*

/** Café-only extension. Shared name/hours/rent live exclusively in locations. */
@Entity(tableName = "cafe_locations",
    foreignKeys = [ForeignKey(entity = BusinessLocationEntity::class, parentColumns = ["id"], childColumns = ["id"], onDelete = ForeignKey.RESTRICT)])
data class CafeLocationEntity(
    @PrimaryKey val id: String,
    val interiorStyle: String, val tableCount: Int, val seatsPerTable: Int,
    val averageVisitMinutes: Int, val targetSeatOccupancy: Double, val takeawayShare: Double
)
