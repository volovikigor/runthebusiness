package com.example.runthebusiness.data.local.entity

import androidx.room.*

@Entity(
    tableName = "ledger_accounts",
    primaryKeys = ["sessionId", "businessId", "code"],
    foreignKeys = [
        ForeignKey(entity = GameSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("businessId")]
)
data class LedgerAccountEntity(
    val sessionId: String,
    val businessId: String,
    val code: String,
    val name: String,
    val type: String
)
