package com.example.runthebusiness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = BusinessEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceEventId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("sessionId"),
        Index("sourceEventId"),
        Index("businessId")
    ]
)
data class JournalEntryEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val businessId: String,
    val sourceEventId: String?,
    val description: String,
    val gameDay: Int,
    val gameHour: Int,
    val gameMinute: Int
)