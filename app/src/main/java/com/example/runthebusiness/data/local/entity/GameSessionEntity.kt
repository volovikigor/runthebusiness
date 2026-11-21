package com.example.runthebusiness.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val currentDay: Int = 1,
    val currentHour: Int = 8,
    val currentMinute: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val isActive: Boolean = true
)