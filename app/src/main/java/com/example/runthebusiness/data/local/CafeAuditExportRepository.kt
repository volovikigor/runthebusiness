package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Complete, read-only support export. Includes ALL rows (no 100-row debug limit).
 * Sections are CSV tables with a table marker; intended for diagnosis, not reimport.
 */
class CafeAuditExportRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val sql = db.openHelper.readableDatabase
        val out = StringBuilder("\uFEFF")
        val tables = listOf(
            "game_sessions", "cafe_arrivals", "customer_orders", "customer_order_lines",
            "cafe_products", "cafe_recipe_items", "inventory_items", "inventory_batches",
            "stock_movements", "journal_entries", "journal_lines", "business_events"
        )
        fun cell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
        for (table in tables) {
            out.append(cell("TABLE")).append(',').append(cell(table)).append('\n')
            sql.query("SELECT * FROM \"$table\"").use { cursor ->
                out.append(cursor.columnNames.joinToString(",") { cell(it) }).append('\n')
                while (cursor.moveToNext()) {
                    out.append(cursor.columnNames.indices.joinToString(",") { index ->
                        cell(if (cursor.isNull(index)) "" else cursor.getString(index) ?: "")
                    }).append('\n')
                }
            }
            out.append('\n')
        }
        out.toString()
    }
}
