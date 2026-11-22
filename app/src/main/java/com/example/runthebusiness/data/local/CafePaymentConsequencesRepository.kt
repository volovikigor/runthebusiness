package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic, reversible MVP consequences based on unpaid due cycles.
 * No extra journal charges: consequences affect operations, not accounting balances.
 */
data class PaymentConsequences(
    val payrollDaysLate: Int,
    val rentDaysLate: Int
) {
    val staffDisruption: Boolean get() = payrollDaysLate > 2
    val rentShutdown: Boolean get() = rentDaysLate > 7
}

class CafePaymentConsequencesRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun load(sessionId: String = "debug_session_001"): PaymentConsequences =
        withContext(Dispatchers.IO) {
            val session = db.accountingDao().getSession(sessionId)
                ?: return@withContext PaymentConsequences(0, 0)
            val day = session.currentDay
            val journal = db.journalViewDao().getJournal(sessionId)
            fun daysLate(period: Int, account: String): Int {
                val due = (day / period) * period
                if (due == 0) return 0
                // Settlement at or after a due date resolves that due cycle.
                val settled = journal.any { record ->
                    record.entry.id.startsWith("settlement_") &&
                        record.entry.gameDay >= due &&
                        record.lines.any { line ->
                            line.accountCode == account && line.debitCents > 0L
                        }
                }
                return if (settled) 0 else day - due
            }
            PaymentConsequences(
                payrollDaysLate = daysLate(7, "2300"),
                rentDaysLate = daysLate(14, "2000")
            )
        }
}
