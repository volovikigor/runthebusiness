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
        report.writeText(exportCsv(), Charsets.UTF_8)
        report
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
