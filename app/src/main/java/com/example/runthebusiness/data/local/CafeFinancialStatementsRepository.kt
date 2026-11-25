package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.data.local.dao.JournalWithLines
import com.example.runthebusiness.data.local.entity.LedgerAccountEntity

data class FinancialLine(val code: String, val name: String, val amountCents: Long)

data class DailyProfitLoss(val day: Int, val revenueCents: Long, val expenseCents: Long)

data class FinancialPeriod(
    val days: IntRange,
    val assets: List<FinancialLine>,
    val liabilities: List<FinancialLine>,
    val equity: List<FinancialLine>,
    val revenue: List<FinancialLine>,
    val expenses: List<FinancialLine>,
    val cashFlows: Map<CashFlowLine, Long>,
    val openingCashCents: Long,
    val closingCashCents: Long,
    val performance: List<CafeDayPerformance>,
    val dailyResults: List<DailyProfitLoss>
) {
    val revenueCents get() = revenue.sumOf { it.amountCents }
    val expenseCents get() = expenses.sumOf { it.amountCents }
    val profitCents get() = revenueCents - expenseCents
    val costOfSalesCents get() = expenseAmount("5000")
    val grossProfitCents get() = revenueCents - costOfSalesCents
    val interestCents get() = expenseAmount("5700")
    val incomeTaxCents get() = expenseAmount("5800")
    val operatingExpenses get() = expenses.filterNot { it.code in setOf("5000", "5700", "5800") }
    val operatingExpenseCents get() = expenseCents - costOfSalesCents - interestCents - incomeTaxCents
    val operatingProfitCents get() = grossProfitCents - operatingExpenseCents

    private fun expenseAmount(code: String): Long =
        expenses.firstOrNull { it.code == code }?.amountCents ?: 0L
}

data class FinancialComparison(val current: FinancialPeriod, val previous: FinancialPeriod?)

data class FinancialStatements(
    val time: GameTime,
    val monthToDate: FinancialComparison,
    val closedMonths: FinancialComparison?
)

internal const val REPORT_MONTH_DAYS = 30

internal fun financialMonthStart(day: Int): Int =
    (day - 1) / REPORT_MONTH_DAYS * REPORT_MONTH_DAYS + 1

internal fun matchedPriorMonthTime(time: GameTime): GameTime? =
    if (time.day > REPORT_MONTH_DAYS) time.copy(day = time.day - REPORT_MONTH_DAYS) else null

enum class CashFlowLine(val label: String, val section: String) {
    SALES("Customer receipts", "Operations"),
    STOCK("Supplier and stock payments", "Operations"),
    PAYROLL("Payroll", "Operations"),
    RENT("Rent", "Operations"),
    TAX("Taxes and social contributions", "Operations"),
    OTHER("Other operating movements", "Operations"),
    EQUIPMENT("Equipment", "Investing"),
    BORROWING("Loan proceeds", "Financing"),
    PRINCIPAL("Loan principal repaid", "Financing"),
    INTEREST("Loan interest", "Financing"),
    CAPITAL("Owner contributions and withdrawals", "Financing")
}

class CafeFinancialStatementsRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)
    private val performance = CafeDailyPerformanceRepository(context.applicationContext)

    suspend fun load(sessionId: String = "debug_session_001"): FinancialStatements? = db.withTransaction {
        val session = db.accountingDao().getSession(sessionId) ?: return@withTransaction null
        val now = GameTime(session.currentDay, session.currentHour, session.currentMinute)
        val start = financialMonthStart(now.day)
        val priorCutoff = matchedPriorMonthTime(now)
        val journal = db.journalViewDao().getJournal(sessionId)
        val accounts = db.gameSetupDao().getAccounts(sessionId)
        val activity = performance.recent(sessionId, now.day - start + 1 + REPORT_MONTH_DAYS * 2)
        fun period(days: IntRange) = financialPeriod(days, journal, accounts, activity)
        val priorMonthToDate = priorCutoff?.let { cutoff ->
            val range = (start - REPORT_MONTH_DAYS)..cutoff.day
            val priorActivity = activity.filter { it.day < cutoff.day } +
                performance.recent(sessionId, days = 1, through = cutoff)
            financialPeriod(range, journal, accounts, priorActivity, cutoff)
        }
        val closed = if (priorCutoff == null) null else FinancialComparison(
            current = period((start - REPORT_MONTH_DAYS) until start),
            previous = if (start > REPORT_MONTH_DAYS * 2)
                period((start - REPORT_MONTH_DAYS * 2) until (start - REPORT_MONTH_DAYS)) else null
        )
        FinancialStatements(now, FinancialComparison(period(start..now.day), priorMonthToDate), closed)
    }
}

private val liquidAccounts = setOf("1000", "1100")

internal fun financialPeriod(
    days: IntRange,
    journal: List<JournalWithLines>,
    accounts: List<LedgerAccountEntity>,
    activity: List<CafeDayPerformance>,
    cutoff: GameTime? = null
): FinancialPeriod {
    val through = journal.filter { row ->
        val entry = row.entry
        entry.gameDay <= days.last && (cutoff == null ||
            GameTime(entry.gameDay, entry.gameHour, entry.gameMinute) <= cutoff)
    }
    val entries = through.filter { it.entry.gameDay in days }
    val closing = balances(through)
    val opening = balances(journal.filter { it.entry.gameDay < days.first })
    val movement = balances(entries)
    fun lines(type: String, values: Map<String, Long>): List<FinancialLine> =
        accounts.filter { it.type.equals(type, ignoreCase = true) }.mapNotNull { account ->
            val debitBalance = values[account.code] ?: 0L
            val amount = if (type == "ASSET" || type == "EXPENSE") debitBalance else -debitBalance
            if (amount == 0L) null else FinancialLine(account.code, account.name, amount)
        }
    val earnings = lines("REVENUE", closing).sumOf { it.amountCents } -
        lines("EXPENSE", closing).sumOf { it.amountCents }
    val cashFlows = mutableMapOf<CashFlowLine, Long>()
    entries.forEach { entry ->
        cashMovement(entry).forEach { (line, amount) ->
            cashFlows[line] = cashFlows.getOrDefault(line, 0L) + amount
        }
    }
    val daily = entries.groupBy { it.entry.gameDay }.map { (day, rows) ->
        val dailyMovement = balances(rows)
        DailyProfitLoss(day, lines("REVENUE", dailyMovement).sumOf { it.amountCents },
            lines("EXPENSE", dailyMovement).sumOf { it.amountCents })
    }.sortedByDescending { it.day }
    return FinancialPeriod(days, lines("ASSET", closing), lines("LIABILITY", closing),
        lines("EQUITY", closing) + FinancialLine("RESULT", "Accumulated result", earnings),
        lines("REVENUE", movement), lines("EXPENSE", movement), cashFlows,
        liquidAccounts.sumOf { opening[it] ?: 0L }, liquidAccounts.sumOf { closing[it] ?: 0L },
        activity.filter { it.day in days }, daily)
}

private fun balances(entries: List<JournalWithLines>): Map<String, Long> {
    val totals = mutableMapOf<String, Long>()
    for (entry in entries) for (line in entry.lines) {
        totals[line.accountCode] = totals.getOrDefault(line.accountCode, 0L) + line.debitCents - line.creditCents
    }
    return totals
}

/** A transfer between cash and bank has zero net movement. Non-cash accruals are excluded. */
internal fun cashMovement(entry: JournalWithLines): Map<CashFlowLine, Long> {
    val net = entry.lines.filter { it.accountCode in liquidAccounts }.sumOf { it.debitCents - it.creditCents }
    if (net == 0L) return emptyMap()
    val counterparts = entry.lines.filter { it.accountCode !in liquidAccounts }
    val codes = counterparts.map { it.accountCode }.toSet()
    // Sales can include VAT and simultaneous non-cash cost-of-sales postings.
    if ("4000" in codes || "1200" in codes || "1300" in codes) return mapOf(CashFlowLine.SALES to net)
    val flows = counterparts.groupingBy { line ->
        when (line.accountCode) {
            "1400", "2010" -> CashFlowLine.STOCK
            "2300", "5200" -> CashFlowLine.PAYROLL
            "2000", "5400" -> CashFlowLine.RENT
            "2200", "2400", "2500", "2600", "5300", "5800" -> CashFlowLine.TAX
            "1500" -> CashFlowLine.EQUIPMENT
            "2100" -> if (line.creditCents > 0L) CashFlowLine.BORROWING else CashFlowLine.PRINCIPAL
            "5700", "2110" -> CashFlowLine.INTEREST
            "3000" -> CashFlowLine.CAPITAL
            else -> CashFlowLine.OTHER
        }
    }.fold(0L) { amount, line -> amount + line.creditCents - line.debitCents }
    if (CashFlowLine.STOCK in flows && codes.all { it in setOf("1400", "2010", "1600") }) {
        return mapOf(CashFlowLine.STOCK to net)
    }
    return flows.filterValues { it != 0L }
}
