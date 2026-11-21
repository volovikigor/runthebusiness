package com.example.runthebusiness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "business_events",
    foreignKeys = [
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "id"], unique = true)
    ]
)
data class BusinessEventEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val businessId: String,
    val eventType: String,
    val sourceObjectType: String,
    val sourceObjectId: String,
    val gameDay: Int,
    val gameHour: Int,
    val gameMinute: Int,
    val description: String
)