package com.example.runthebusiness.data.local.entity

import androidx.room.*

@Entity(tableName = "cafe_arrivals",
    foreignKeys = [ForeignKey(entity = BusinessLocationEntity::class,
        parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("locationId")])
data class CafeArrivalEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val gameDay: Int,
    val gameHour: Int,
    val gameMinute: Int,
    val guests: Int,
    val status: String,
    val preferredProductId: String? = null
)
