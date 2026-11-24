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
            suspend fun daysLate(period: Int, account: String): Int {
                for (due in period..day step period) {
                    if (db.accountingDao().payableDue(sessionId, account, due) > 0L) return day - due
                }
                return 0
            }
            PaymentConsequences(
                payrollDaysLate = daysLate(14, "2300"),
                rentDaysLate = daysLate(14, "2000")
            )
        }
}
