package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import com.example.runthebusiness.engine.InitialGameFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DebugTable(val name: String, val columns: List<String>, val rows: List<List<String>>)

class DebugDatabaseRepository(context: Context) {
    private val database = GameDatabase.getInstance(context)

    // This is a development-only seed of the accounting journal, NOT full game persistence.
    suspend fun seedOpeningJournal(): Unit = withContext(Dispatchers.IO) {
        val initial = InitialGameFactory.create()
        val business = initial.businesses.single()
        val sessionId = "debug_session_001"
        val dao = database.accountingDao()
        database.withTransaction {
            if (dao.getSession(sessionId) != null) return@withTransaction
            val now = System.currentTimeMillis()
            dao.insertSession(
                GameSessionEntity(
                    id = sessionId, name = business.name,
                    currentDay = initial.currentTime.day,
                    currentHour = initial.currentTime.hour,
                    currentMinute = initial.currentTime.minute,
                    createdAtMillis = now, updatedAtMillis = now
                )
            )
            initial.journalEntries.forEach { entry ->
                val event = BusinessEventEntity(
                    id = entry.sourceEventId, sessionId = sessionId,
                    businessId = entry.businessId,
                    eventType = "OPENING_TRANSACTION",
                    sourceObjectType = entry.sourceObjectType,
                    sourceObjectId = entry.sourceObjectId,
                    gameDay = entry.timestamp.day,
                    gameHour = entry.timestamp.hour,
                    gameMinute = entry.timestamp.minute,
                    description = entry.description
                )
                val header = JournalEntryEntity(
                    id = entry.id, sessionId = sessionId,
                    businessId = entry.businessId,
                    sourceEventId = event.id,
                    description = entry.description,
                    gameDay = entry.timestamp.day,
                    gameHour = entry.timestamp.hour,
                    gameMinute = entry.timestamp.minute
                )
                val lines = entry.lines.mapIndexed { index, line ->
                    JournalLineEntity(
                        id = "${entry.id}_${index + 1}",
                        entryId = entry.id, lineNumber = index + 1,
                        accountCode = line.accountCode,
                        locationId = line.locationId,
                        debitCents = line.debitCents,
                        creditCents = line.creditCents
                    )
                }
                dao.saveAccountingOperation(event, header, lines)
            }
        }
    }

    suspend fun inspect(): List<DebugTable> = withContext(Dispatchers.IO) {
        // Fixed allowlist: never interpolate user-supplied table names into SQL.
        val tables = listOf("game_sessions", "business_events", "journal_entries", "journal_lines")
        val sqlDb = database.openHelper.readableDatabase
        tables.map { table ->
            sqlDb.query("SELECT * FROM \"$table\" LIMIT 100").use { cursor ->
                val columns = cursor.columnNames.toList()
                val rows = mutableListOf<List<String>>()
                while (cursor.moveToNext()) {
                    rows += columns.indices.map { index ->
                        if (cursor.isNull(index)) "NULL" else cursor.getString(index) ?: ""
                    }
                }
                DebugTable(table, columns, rows)
            }
        }
    }
}
