package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.core.finance.CafeTaxPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScheduledBill(val account: String, val label: String, val accrued: Long,
    val due: Long, val nextDueDay: Int, val automatic: Boolean, val periodDays: Int)

data class SupplierBill(val id: String, val label: String, val dueDay: Int, val amountCents: Long)

class CafePaymentRegisterRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)
    private val cash = CafeCashManagementRepository(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("cafe_bill_policy", Context.MODE_PRIVATE)
    private val accounts = listOf("2300" to "Payroll", "2000" to "Rent", "2200" to "VAT",
        "2400" to "Social contributions", "2600" to "Corporate income tax")

    suspend fun supplierBills(sessionId: String = "debug_session_001"): List<SupplierBill> =
        db.accountingDao().supplierBills(sessionId)
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
        accounts.map { (account, label) ->
            val regular = account == "2300" || account == "2000"
            val period = if (regular) 14 else CafeTaxPolicy.taxPeriodDays
            val cycle = if (regular) day / period * period else (day - 1) / period * period + 1
            ScheduledBill(account, label, (-dao.accountBalance(sessionId, account)).coerceAtLeast(0L),
                if (cycle <= 1) 0L else dao.payableDue(sessionId, account, cycle).coerceAtLeast(0L),
                cycle + period, prefs.getBoolean(account, false), period)
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
