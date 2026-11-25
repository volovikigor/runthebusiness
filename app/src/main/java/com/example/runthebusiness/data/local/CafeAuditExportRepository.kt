package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.core.finance.CashForecast
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

    suspend fun saveDesktopReport(completedDays: Int = 14): File = withContext(Dispatchers.IO) {
        val report = File(appContext.filesDir, "cafe_diagnostic_latest.csv")
        val temporary = File(appContext.filesDir, "cafe_diagnostic_latest.csv.tmp")
        temporary.writeText(exportRecentCsv(completedDays), Charsets.UTF_8)
        check(temporary.renameTo(report)) { "Could not replace the café diagnostic CSV" }
        report
    }

    /** A rolling completed-day report. Balances include earlier ledger activity. */
    suspend fun exportRecentCsv(completedDays: Int = 14): String = db.withTransaction {
        require(completedDays in 1..30)
        val sql = db.openHelper.readableDatabase
        var currentDay = 1
        sql.query("SELECT MAX(currentDay) FROM game_sessions").use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) currentDay = cursor.getInt(0)
        }
        val fromDay = (currentDay - completedDays).coerceAtLeast(1)
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
        fun writeRows(name: String, columns: List<String>, rows: Iterable<List<Any>>) {
            out.append(cell("TABLE")).append(',').append(cell(name)).append('\n')
            out.append(columns.joinToString(",") { cell(it) }).append('\n')
            rows.forEach { row -> out.append(row.joinToString(",") { cell(it.toString()) }).append('\n') }
            out.append('\n')
        }
        val days = "BETWEEN $fromDay AND $throughDay"
        val replenish = CafeAutoPurchasingRepository(appContext)
        val policy = replenish.policy()
        val paymentRegister = CafePaymentRegisterRepository(appContext)
        writeRows("diagnostic_window", listOf("fromDay", "throughDay", "completedDays",
            "autoPurchasing", "dailyBudgetCents", "automaticCashDeposits"), listOf(listOf(
            fromDay, throughDay, (throughDay - fromDay + 1).coerceAtLeast(0), policy.enabled,
            policy.dailyBudgetCents, paymentRegister.automaticDepositsEnabled()
        )))
        writeRows("supplier_pack_settings", listOf("itemId", "packQuantity", "packCents", "shelfLifeDays", "autoOrder"),
            CafePurchasingRepository.supplies.map {
                listOf(it.itemId, it.quantity, it.costCents, it.shelfLifeDays, replenish.enabled(it.itemId))
            })
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
        writeSection("product_sales_${completedDays}d", """SELECT l.productId, l.productName,
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
        writeSection("promotion_outcomes", """
            SELECT preferredProductId AS productId, status, COUNT(*) AS groups, SUM(guests) AS guests
            FROM cafe_arrivals WHERE gameDay $days AND preferredProductId IS NOT NULL
            GROUP BY preferredProductId, status ORDER BY preferredProductId, status
        """)
        writeSection("group_size_conversion", """SELECT guests, status,
            COUNT(*) AS groups FROM cafe_arrivals WHERE gameDay $days
            GROUP BY guests, status ORDER BY guests, status""")
        writeSection("current_inventory", """SELECT i.id AS inventoryItemId, i.name,
            i.unit, i.shelfLifeDays, COALESCE(SUM(b.quantityRemaining), 0) AS onHand
            FROM inventory_items i LEFT JOIN inventory_batches b ON b.inventoryItemId = i.id
            GROUP BY i.id, i.name, i.unit, i.shelfLifeDays ORDER BY i.name""")
        writeSection("starting_finance", """
            SELECT j.sessionId, j.gameDay, j.id, j.description, l.accountCode, l.debitCents, l.creditCents
            FROM journal_entries j JOIN journal_lines l ON l.entryId = j.id
            JOIN business_events b ON b.id = j.sourceEventId
            WHERE b.eventType = 'OPENING_TRANSACTION' ORDER BY j.sessionId, j.id, l.lineNumber
        """)
        writeSection("loan_contracts", "SELECT * FROM loans")
        writeSection("balances_at_export", """
            SELECT j.sessionId, l.accountCode, SUM(l.debitCents - l.creditCents) AS debitBalanceCents
            FROM journal_entries j JOIN journal_lines l ON l.entryId = j.id
            GROUP BY j.sessionId, l.accountCode ORDER BY j.sessionId, l.accountCode
        """)
        val sessions = sql.query("SELECT id FROM game_sessions").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        writeRows("loan_schedule_projected", listOf("sessionId", "loanId", "day", "interestCents", "principalCents",
            "paymentCents"), sessions.flatMap { id ->
                db.gameSetupDao().getLoans(id).filter { it.isActive }.flatMap { loan ->
                    CashForecast.loanSchedule(loan.outstandingPrincipalCents, loan.annualInterestRate,
                        loan.monthlyPaymentCents, loan.nextPaymentDay, loan.termMonths).map {
                        listOf(id, loan.id, it.dueDay, it.interestCents, it.principalCents, it.totalCents)
                    }
                }
            })
        writeSection("daily_economics", dailyEconomicsSql(fromDay, throughDay))
        writeSection("stock_waste", """
            SELECT inventoryItemId, inventoryItemName,
                CASE WHEN sourceId LIKE 'expiry_%' THEN 'EXPIRY' ELSE 'OTHER_WRITE_OFF' END AS reason,
                COUNT(*) AS movements, SUM(-quantityDelta) AS quantity,
                SUM(ROUND(-quantityDelta * unitCostCents)) AS valueCents
            FROM stock_movements WHERE gameDay $days AND sourceType = 'WRITE_OFF'
            GROUP BY inventoryItemId, inventoryItemName, reason ORDER BY valueCents DESC
        """)
        val session = sessions.singleOrNull()
        if (session != null) {
            writeRows("unpaid_supplier_invoices", listOf("id", "label", "dueDay", "cents"),
                paymentRegister.supplierBills(session).map { listOf(it.id, it.label, it.dueDay, it.amountCents) })
            writeRows("scheduled_bills_snapshot", listOf("account", "label", "accruedCents", "dueCents",
                "nextDueDay", "automatic"), paymentRegister.snapshot(session).map {
                listOf(it.account, it.label, it.accrued, it.due, it.nextDueDay, it.automatic)
            })
        }
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
            "order_lines" to flow.orderLines, "net_sales_cents" to flow.netSalesCents,
            "card_orders" to flow.cardOrders, "card_gross_cents" to flow.cardGrossCents,
            "cash_gross_cents" to flow.cashGrossCents
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

/** Include pre-window postings when calculating balances, including opening finance. */
internal fun dailyEconomicsSql(fromDay: Int, throughDay: Int): String = """
    WITH RECURSIVE dates(day) AS (
        SELECT $fromDay WHERE $throughDay >= $fromDay
        UNION ALL SELECT day + 1 FROM dates WHERE day < $throughDay
    ), ledger AS (
        SELECT e.sessionId, e.gameDay,
            SUM(CASE WHEN a.type = 'REVENUE' THEN l.creditCents - l.debitCents ELSE 0 END) AS revenue,
            SUM(CASE WHEN a.type = 'EXPENSE' THEN l.debitCents - l.creditCents ELSE 0 END) AS expenses,
            SUM(CASE WHEN l.accountCode = '5000' THEN l.debitCents - l.creditCents ELSE 0 END) AS cogs,
            SUM(CASE WHEN l.accountCode = '5100' THEN l.debitCents - l.creditCents ELSE 0 END) AS waste,
            SUM(CASE WHEN l.accountCode = '5800' THEN l.debitCents - l.creditCents ELSE 0 END) AS tax,
            SUM(CASE WHEN l.accountCode = '5700' THEN l.debitCents - l.creditCents ELSE 0 END) AS interest,
            SUM(CASE WHEN l.accountCode = '2100' THEN l.debitCents ELSE 0 END) AS principalRepaid,
            SUM(CASE WHEN l.accountCode = '1000' THEN l.debitCents - l.creditCents ELSE 0 END) AS cashDelta,
            SUM(CASE WHEN l.accountCode = '1100' THEN l.debitCents - l.creditCents ELSE 0 END) AS bankDelta,
            SUM(CASE WHEN l.accountCode = '1400' THEN l.debitCents - l.creditCents ELSE 0 END) AS inventoryDelta,
            SUM(CASE WHEN l.accountCode = '2010' THEN l.creditCents - l.debitCents ELSE 0 END) AS supplierDelta,
            SUM(CASE WHEN a.type = 'LIABILITY' AND l.accountCode NOT IN ('2100', '2110')
                THEN l.creditCents - l.debitCents ELSE 0 END) AS payableDelta
        FROM journal_entries e JOIN journal_lines l ON l.entryId = e.id
        LEFT JOIN ledger_accounts a ON a.sessionId = e.sessionId AND a.businessId = e.businessId
            AND a.code = l.accountCode
        WHERE e.gameDay <= $throughDay GROUP BY e.sessionId, e.gameDay
    ), daily AS (
        SELECT s.id AS sessionId, d.day,
            COALESCE(revenue, 0) AS netSalesCents, COALESCE(expenses, 0) AS expenseCents,
            COALESCE(cogs, 0) AS costOfSalesCents, COALESCE(waste, 0) AS wasteExpenseCents,
            COALESCE(tax, 0) AS incomeTaxExpenseCents, COALESCE(interest, 0) AS interestExpenseCents,
            COALESCE(principalRepaid, 0) AS principalRepaidCents,
            COALESCE(revenue, 0) - COALESCE(expenses, 0) AS netProfitCents,
            COALESCE(cashDelta, 0) AS cashChangeCents, COALESCE(bankDelta, 0) AS bankChangeCents,
            COALESCE((SELECT SUM(h.cashDelta) FROM ledger h
                WHERE h.sessionId = s.id AND h.gameDay <= d.day), 0) AS closingCashCents,
            COALESCE((SELECT SUM(h.bankDelta) FROM ledger h
                WHERE h.sessionId = s.id AND h.gameDay <= d.day), 0) AS closingBankCents,
            COALESCE((SELECT SUM(h.inventoryDelta) FROM ledger h
                WHERE h.sessionId = s.id AND h.gameDay <= d.day), 0) AS closingInventoryCents,
            COALESCE((SELECT SUM(h.supplierDelta) FROM ledger h
                WHERE h.sessionId = s.id AND h.gameDay <= d.day), 0) AS closingSupplierPayableCents,
            COALESCE((SELECT SUM(h.payableDelta) FROM ledger h
                WHERE h.sessionId = s.id AND h.gameDay <= d.day), 0) AS closingNonLoanLiabilitiesCents
        FROM game_sessions s CROSS JOIN dates d
        LEFT JOIN ledger l ON l.sessionId = s.id AND l.gameDay = d.day
    )
    SELECT * FROM daily ORDER BY sessionId, day
""".trimIndent()
