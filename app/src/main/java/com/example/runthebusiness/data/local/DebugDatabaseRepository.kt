package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import com.example.runthebusiness.engine.InitialGameFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DebugTable(val name: String, val columns: List<String>, val rows: List<List<String>>)

class DebugDatabaseRepository(private val context: Context) {
    private val database = GameDatabase.getInstance(context)

    suspend fun seedOpeningJournal() {
        GameRepository(context).initializeOpeningGame()
    }

    suspend fun inspect(): List<DebugTable> = withContext(Dispatchers.IO) {
        // Fixed allowlist: never interpolate user-supplied table names into SQL.
        val tables = listOf("game_sessions", "businesses", "ledger_accounts", "loans", "business_events", "journal_entries", "journal_lines")
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
