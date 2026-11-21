package com.example.runthebusiness.data.local.entity

import androidx.room.*

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(entity = GameSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("sessionId"), Index("businessId")]
)
data class LoanEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val businessId: String,
    val principalCents: Long,
    val outstandingPrincipalCents: Long,
    val annualInterestRate: Double,
    val termMonths: Int,
    val monthlyPaymentCents: Long,
    val nextPaymentDay: Int,
    val nextPaymentHour: Int,
    val nextPaymentMinute: Int,
    val isActive: Boolean
)
