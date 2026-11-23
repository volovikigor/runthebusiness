package com.example.runthebusiness.data.local

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Complete, read-only support export. Includes ALL rows (no 100-row debug limit).
 * Sections are CSV tables with a table marker; intended for diagnosis, not reimport.
 */
class CafeAuditExportRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)
    private val appContext = context.applicationContext

    suspend fun saveDesktopReport(): File = withContext(Dispatchers.IO) {
        val report = File(appContext.filesDir, "cafe_diagnostic_latest.csv")
        report.writeText(exportRecentCsv(), Charsets.UTF_8)
        report
    }

    /** Lightweight ADB report. Includes the last 14 completed days plus current stock
     * and catalog. The full-history Finance export remains unchanged.
     */
    suspend fun exportRecentCsv(): String = withContext(Dispatchers.IO) {
        val sql = db.openHelper.readableDatabase
        var currentDay = 1
        sql.query("SELECT MAX(currentDay) FROM game_sessions").use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) currentDay = cursor.getInt(0)
        }
        val fromDay = (currentDay - 14).coerceAtLeast(1)
        val throughDay = currentDay - 1
        val out = StringBuilder("\uFEFF")
        fun cell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
        fun writeSection(name: String, query: String) {
            out.append(cell("TABLE")).append(',').append(cell(name)).append('\n')
            sql.query(query).use { cursor ->
                out.append(cursor.columnNames.joinToString(",") { cell(it) }).append('\n')
                while (cursor.moveToNext()) {
                    out.append(cursor.columnNames.indices.joinToString(",") { index ->
                        cell(if (cursor.isNull(index)) "" else cursor.getString(index) ?: "")
                    }).append('\n')
                }
            }
            out.append('\n')
        }
        val days = "BETWEEN $fromDay AND $throughDay"
        writeSection("game_sessions", "SELECT * FROM game_sessions")
        writeSection("cafe_arrivals", "SELECT * FROM cafe_arrivals WHERE gameDay $days")
        writeSection("customer_orders", "SELECT * FROM customer_orders WHERE gameDay $days")
        writeSection("customer_order_lines", """SELECT l.* FROM customer_order_lines l
            JOIN customer_orders o ON o.id = l.orderId WHERE o.gameDay $days""")
        for (table in listOf("cafe_products", "cafe_recipe_items", "inventory_items", "inventory_batches")) {
            writeSection(table, "SELECT * FROM $table")
        }
        writeSection("stock_movements", "SELECT * FROM stock_movements WHERE gameDay $days")
        writeSection("journal_entries", "SELECT * FROM journal_entries WHERE gameDay $days")
        writeSection("journal_lines", """SELECT l.* FROM journal_lines l
            JOIN journal_entries e ON e.id = l.entryId WHERE e.gameDay $days""")
        writeSection("business_events", "SELECT * FROM business_events WHERE gameDay $days")
        writeSection("product_sales_14d", """SELECT l.productId, l.productName,
            p.type AS productType, p.isAvailable, p.fulfillment,
            COUNT(DISTINCT l.orderId) AS ordersWithProduct,
            COUNT(*) AS orderLines, SUM(l.quantity) AS units,
            SUM(l.quantity * l.unitPriceCents) AS grossSalesCents
            FROM customer_order_lines l
            JOIN customer_orders o ON o.id = l.orderId
            LEFT JOIN cafe_products p ON p.id = l.productId
            WHERE o.gameDay $days
            GROUP BY l.productId, l.productName, p.type, p.isAvailable, p.fulfillment
            ORDER BY units DESC""")
        writeSection("product_sales_daily", """SELECT o.gameDay, l.productId, l.productName,
            SUM(l.quantity) AS units, COUNT(DISTINCT l.orderId) AS ordersWithProduct,
            SUM(l.quantity * l.unitPriceCents) AS grossSalesCents
            FROM customer_order_lines l JOIN customer_orders o ON o.id = l.orderId
            WHERE o.gameDay $days
            GROUP BY o.gameDay, l.productId, l.productName
            ORDER BY o.gameDay, l.productName""")
        writeSection("group_size_conversion", """SELECT guests, status,
            COUNT(*) AS groups FROM cafe_arrivals WHERE gameDay $days
            GROUP BY guests, status ORDER BY guests, status""")
        writeSection("current_inventory", """SELECT i.id AS inventoryItemId, i.name,
            i.unit, i.shelfLifeDays, COALESCE(SUM(b.quantityRemaining), 0) AS onHand
            FROM inventory_items i LEFT JOIN inventory_batches b ON b.inventoryItemId = i.id
            GROUP BY i.id, i.name, i.unit, i.shelfLifeDays ORDER BY i.name""")
        appendSummary(out, ::cell)
        out.toString()
    }

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
        appendSummary(out, ::cell)
        out.toString()
    }

    private suspend fun appendSummary(out: StringBuilder, cell: (String) -> String) {
        val flow = CafeCustomerFlowRepository(appContext).lastCompletedDays()
        val economics = CafeBreakEvenRepository(appContext).snapshot()
        fun section(name: String, values: List<Pair<String, Any>>) {
            out.append(cell("TABLE")).append(",").append(cell(name)).append("\n")
            out.append("metric,value\n")
            values.forEach { (key, value) ->
                out.append(cell(key)).append(",").append(cell(value.toString())).append("\n")
            }
            out.append("\n")
        }
        if (flow != null) section("customer_flow_summary_14d", listOf(
            "completed_days" to flow.days, "arriving_groups" to flow.groups,
            "arriving_guests" to flow.guests, "served_groups" to flow.servedGroups,
            "served_guests" to flow.servedGuests, "orders" to flow.orders,
            "order_lines" to flow.orderLines, "net_sales_cents" to flow.netSalesCents
        ) + flow.byStatus.map { (key, value) -> "status_$key" to value })
        if (economics != null) section("break_even_summary_14d", listOf(
            "completed_days" to economics.observedDays, "orders" to economics.orders,
            "net_sales_cents" to economics.netSalesCents, "cogs_cents" to economics.cogsCents,
            "expired_inventory_cents" to economics.wasteCents,
            "waste_daily_cents" to economics.wasteDailyCents,
            "fixed_daily_cents" to economics.fixedDailyCents,
            "avg_contribution_cents" to economics.avgContributionCents,
            "actual_orders_per_day" to economics.actualOrdersPerDay,
            "break_even_orders_per_day" to (economics.ordersNeededPerDay ?: "N/A")
        ))
    }
}
