package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FinancialLine(val code: String, val name: String, val amountCents: Long)
data class FinancialStatements(
    val assets: List<FinancialLine>,
    val liabilities: List<FinancialLine>,
    val equity: List<FinancialLine>,
    val revenue: List<FinancialLine>,
    val expenses: List<FinancialLine>,
    val cashMovements: List<FinancialLine>,
    val netIncomeCents: Long
)

data class DailyProfitLoss(val day: Int, val revenueCents: Long, val expenseCents: Long) {
    val profitCents: Long get() = revenueCents - expenseCents
}

/** Ledger-derived, cumulative statements for the current game session. */
class CafeFinancialStatementsRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun dailyProfitLoss(sessionId: String = "debug_session_001"): List<DailyProfitLoss> =
        withContext(Dispatchers.IO) {
            val types = db.gameSetupDao().getAccounts(sessionId).associate { it.code to it.type }
            db.journalViewDao().getJournal(sessionId)
                .groupBy { it.entry.gameDay }
                .map { (day, entries) ->
                    var revenue = 0L
                    var expense = 0L
                    entries.forEach { entry ->
                        entry.lines.forEach { line ->
                            when (types[line.accountCode]?.uppercase()) {
                                "REVENUE" -> revenue += line.creditCents - line.debitCents
                                "EXPENSE" -> expense += line.debitCents - line.creditCents
                            }
                        }
                    }
                    DailyProfitLoss(day, revenue, expense)
                }.sortedByDescending { it.day }
        }

    suspend fun cashAvailable(sessionId: String = "debug_session_001"): Long =
        withContext(Dispatchers.IO) {
            db.journalViewDao().getJournal(sessionId).sumOf { entry ->
                entry.lines.filter { it.accountCode == "1000" || it.accountCode == "1100" }
                    .sumOf { it.debitCents - it.creditCents }
            }
        }

    suspend fun load(sessionId: String = "debug_session_001"): FinancialStatements =
        withContext(Dispatchers.IO) {
            val accounts = db.gameSetupDao().getAccounts(sessionId)
            val journal = db.journalViewDao().getJournal(sessionId)
            val balances = mutableMapOf<String, Long>()
            for (entry in journal) for (line in entry.lines) {
                balances[line.accountCode] = (balances[line.accountCode] ?: 0L) +
                    line.debitCents - line.creditCents
            }
            fun lines(type: String): List<FinancialLine> =
                accounts.filter { it.type.equals(type, ignoreCase = true) }
                    .map { account ->
                        val signed = balances[account.code] ?: 0L
                        FinancialLine(account.code, account.name,
                            if (type == "ASSET" || type == "EXPENSE") signed else -signed)
                    }.filter { it.amountCents != 0L }
            val revenue = lines("REVENUE")
            val expenses = lines("EXPENSE")
            val profit = revenue.sumOf { it.amountCents } - expenses.sumOf { it.amountCents }
            val equity = lines("EQUITY") + FinancialLine("CURRENT_RESULT", "Current profit / loss", profit)
            // Movement in cash and bank accounts, not a classified operating/investing/financing CF.
            val cash = accounts.filter { it.code in setOf("1000", "1100") }.map { account ->
                FinancialLine(account.code, account.name, balances[account.code] ?: 0L)
            }
            FinancialStatements(lines("ASSET"), lines("LIABILITY"), equity, revenue, expenses, cash, profit)
        }
}
