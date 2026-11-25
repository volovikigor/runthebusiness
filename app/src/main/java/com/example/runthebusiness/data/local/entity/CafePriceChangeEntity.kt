package com.example.runthebusiness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "cafe_price_changes",
    primaryKeys = ["productId", "gameDay"],
    foreignKeys = [ForeignKey(
        entity = CafeProductEntity::class,
        parentColumns = ["id"],
        childColumns = ["productId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CafePriceChangeEntity(
    val productId: String,
    val gameDay: Int,
    val previousCents: Long,
    val currentCents: Long
)
