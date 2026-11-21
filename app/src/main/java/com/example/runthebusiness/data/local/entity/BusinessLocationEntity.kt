package com.example.runthebusiness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cross-industry location identity. Café-specific fields remain in cafe_locations. */
@Entity(
    tableName = "locations",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId")]
)
data class BusinessLocationEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val name: String,
    val openingHour: Int,
    val closingHour: Int,
    val monthlyRentCents: Long,
    val isOpen: Boolean
)
