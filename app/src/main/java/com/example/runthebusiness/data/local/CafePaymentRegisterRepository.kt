package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScheduledBill(val account: String, val label: String, val accrued: Long,
    val due: Long, val nextDueDay: Int, val automatic: Boolean)

class CafePaymentRegisterRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)
    private val cash = CafeCashManagementRepository(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("cafe_bill_policy", Context.MODE_PRIVATE)
    private val accounts = listOf("2300" to "Payroll", "2000" to "Rent")
    fun setAutomatic(account: String, enabled: Boolean) {
        require(accounts.any { it.first == account })
        prefs.edit().putBoolean(account, enabled).apply()
    }
    fun automaticDepositsEnabled(): Boolean = prefs.getBoolean("automatic_deposits", false)

    fun setAutomaticDeposits(enabled: Boolean) {
        prefs.edit().putBoolean("automatic_deposits", enabled).apply()
    }

    fun reset() { prefs.edit().clear().commit() }
    suspend fun snapshot(sessionId: String = "debug_session_001"): List<ScheduledBill> = withContext(Dispatchers.IO) {
        val dao = db.accountingDao()
        val day = dao.getSession(sessionId)?.currentDay ?: return@withContext emptyList()
        val cycle = day / 14 * 14
        accounts.map { (account, label) ->
            ScheduledBill(account, label, (-dao.accountBalance(sessionId, account)).coerceAtLeast(0L),
                if (cycle == 0) 0L else dao.payableDue(sessionId, account, cycle).coerceAtLeast(0L),
                cycle + 14, prefs.getBoolean(account, false))
        }
    }
    suspend fun settleAutomatic(sessionId: String = "debug_session_001") {
        if (accounts.none { prefs.getBoolean(it.first, false) }) return
        db.withTransaction {
            val autoDeposit = automaticDepositsEnabled()
            for (bill in snapshot(sessionId)) {
                if (!bill.automatic || bill.due <= 0L) continue
                val position = cash.position(sessionId)
                val shortfall = (bill.due - position.bankCents).coerceAtLeast(0L)
                if (shortfall > 0L) {
                    if (!autoDeposit || position.cashCents < shortfall) continue
                    cash.transferCashToBank(shortfall, sessionId)
                }
                cash.pay(bill.account, sessionId, bill.due)
            }
        }
    }
}
