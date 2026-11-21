package com.example.runthebusiness.data.local.entity

import androidx.room.*

@Entity(
    tableName = "businesses",
    foreignKeys = [ForeignKey(entity = GameSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("sessionId")]
)
data class BusinessEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val name: String,
    val ownerId: String,
    val industry: String,
    val currencyCode: String,
    val isActive: Boolean
)
