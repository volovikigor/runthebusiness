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

    suspend fun journal(sessionId: String = "debug_session_001"): List<DebugJournal> =
        withContext(Dispatchers.IO) {
            val accounts = database.gameSetupDao().getAccounts(sessionId).associateBy { it.code }
            val productsByOrder = database.cafeSalesDao().getAllOrderLines()
                .groupBy { it.orderId }
            database.journalViewDao().getJournal(sessionId).map { group ->
                val orderId = when {
                    group.entry.id.startsWith("sale_") -> group.entry.id.removePrefix("sale_")
                    group.entry.id.startsWith("cogs_") -> group.entry.id.removePrefix("cogs_")
                    else -> null
                }
                val products = orderId?.let { productsByOrder[it] }.orEmpty()
                val title = if (products.isNotEmpty()) {
                    val items = products.joinToString(", ") { "${it.quantity} × ${it.productName}" }
                    if (group.entry.id.startsWith("cogs_")) "Inventory consumed · $items"
                    else "Customer sale · $items"
                } else group.entry.description
                DebugJournal(
                    id = group.entry.id,
                    description = title,
                    day = group.entry.gameDay,
                    lines = group.lines.sortedBy { it.lineNumber }.map { line ->
                        DebugJournalLine(
                            account = "${line.accountCode} ${accounts[line.accountCode]?.name ?: ""}".trim(),
                            debitCents = line.debitCents,
                            creditCents = line.creditCents
                        )
                    }
                )
            }
        }

    suspend fun inspect(): List<DebugTable> = withContext(Dispatchers.IO) {
        // Fixed allowlist: never interpolate user-supplied table names into SQL.
        val tables = listOf("game_sessions", "businesses", "locations", "ledger_accounts", "loans", "cafe_locations", "employees", "equipment", "inventory_items", "inventory_batches", "business_events", "customer_orders", "cafe_products", "cafe_recipe_items", "cafe_arrivals")
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

data class DebugJournal(val id: String, val description: String, val day: Int, val lines: List<DebugJournalLine>)
data class DebugJournalLine(val account: String, val debitCents: Long, val creditCents: Long)
