package com.example.runthebusiness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("entryId"),
        Index(value = ["entryId", "lineNumber"], unique = true)
    ]
)
data class JournalLineEntity(
    @PrimaryKey
    val id: String,
    val entryId: String,
    val lineNumber: Int,
    val accountCode: String,
    val locationId: String = "",
    val debitCents: Long = 0L,
    val creditCents: Long = 0L
)