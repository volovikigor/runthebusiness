package com.example.runthebusiness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customer_orders",
    foreignKeys = [ForeignKey(entity = BusinessLocationEntity::class,
        parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("locationId")]
)
data class CustomerOrderEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val gameDay: Int,
    val gameHour: Int,
    val gameMinute: Int,
    val grossCents: Long,
    val netCents: Long,
    val vatCents: Long,
    val cogsCents: Long,
    val paymentMethod: String,
    val status: String
)
