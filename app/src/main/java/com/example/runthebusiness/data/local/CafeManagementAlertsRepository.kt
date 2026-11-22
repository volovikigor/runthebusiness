package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ManagementAlert(
    val id: String,
    val priority: Int,
    val title: String,
    val detail: String,
    val destination: String,
    val section: Int = 0
)

/** Derived from durable business state: alerts disappear when the problem is resolved. */
class CafeManagementAlertsRepository(context: Context) {
    private val stock = CafePurchasingRepository(context.applicationContext)
    private val cash = CafeCashManagementRepository(context.applicationContext)
    private val game = GameRepository(context.applicationContext)
    private val db = GameDatabase.getInstance(context.applicationContext)
    private val consequences = CafePaymentConsequencesRepository(context.applicationContext)

    suspend fun load(sessionId: String = "debug_session_001"): List<ManagementAlert> =
        withContext(Dispatchers.IO) {
            val available = stock.stock(sessionId)
            val position = cash.position(sessionId)
            val day = game.loadGameTime(sessionId)?.day ?: return@withContext emptyList()
            val impact = consequences.load(sessionId)
            // Game cadence: rent on days 14, 28, ...; payroll on days 7, 14, 21, ...
            // Accruals remain visible in Finance every day; alerts are only for due/late bills.
            fun lastDueDay(period: Int): Int = (day / period) * period
            val settlements = db.accountingDao().getJournalEntries(sessionId)
                .filter { it.id.startsWith("settlement_") }
            suspend fun settledSince(account: String, cutoff: Int): Boolean = settlements.any { entry ->
                entry.gameDay >= cutoff && db.accountingDao().getJournalLines(entry.id)
                    .any { it.accountCode == account && it.debitCents > 0L }
            }
            suspend fun isDue(period: Int, account: String): Boolean =
                day >= period && !settledSince(account, lastDueDay(period))
            fun isLate(period: Int): Boolean = if (period == 7) impact.payrollDaysLate > 2
                else impact.rentDaysLate > 2
            val alerts = mutableListOf<ManagementAlert>()
            CafePurchasingRepository.supplies.forEach { supply ->
                val remaining = available[supply.itemId] ?: 0.0
                val threshold = supply.quantity * 0.2
                if (remaining <= threshold) {
                    alerts += ManagementAlert(
                        id = "stock_${supply.itemId}",
                        priority = if (remaining <= 0.0) 0 else 1,
                        title = if (remaining <= 0.0) "Out of ${supply.label}" else "Low stock: ${supply.label}",
                        detail = "%.0f %s left · reorder from Operations".format(
                            java.util.Locale.ROOT, remaining, supply.unit),
                        destination = "operations", section = 1
                    )
                }
            }
            val rentDue = position.rentPayableCents > 0L && isDue(14, "2000")
            val payrollDue = position.salariesPayableCents > 0L && isDue(7, "2300")
            if ((rentDue || payrollDue) && position.liquidityGapCents < 0L) {
                alerts += ManagementAlert(
                    id = "liquidity", priority = 0,
                    title = "Insufficient liquidity",
                    detail = "Accrued rent and payroll exceed cash and bank balances",
                    destination = "finance"
                )
            }
            if (rentDue) {
                alerts += ManagementAlert(
                    id = "rent", priority = if (isLate(14)) 0 else 2,
                    title = if (isLate(14)) "Overdue rent" else "Rent payment due",
                    detail = if (isLate(14)) "Rent is more than 2 game days late · settle in Finance > Cash"
                        else "Rent payment scheduled every 14 game days · Finance > Cash",
                    destination = "finance"
                )
            }
            if (payrollDue) {
                alerts += ManagementAlert(
                    id = "payroll", priority = if (isLate(7)) 0 else 2,
                    title = if (isLate(7)) "Overdue payroll" else "Payroll payment due",
                    detail = if (isLate(7)) "Payroll is more than 2 game days late · settle in Finance > Cash"
                        else "Payroll payment scheduled every 7 game days · Finance > Cash",
                    destination = "finance"
                )
            }
            if (impact.staffDisruption) alerts += ManagementAlert(
                id = "staff_disruption", priority = 0,
                title = "Staff disruption: customers lost",
                detail = "Unpaid payroll is reducing service capacity. Settle payroll in Finance > Cash.",
                destination = "finance"
            )
            if (impact.rentShutdown) alerts += ManagementAlert(
                id = "rent_shutdown", priority = 0,
                title = "Trading suspended: rent unpaid",
                detail = "Rent is more than 7 game days overdue. Settle rent in Finance > Cash to resume sales.",
                destination = "finance"
            )
            alerts.sortedWith(compareBy<ManagementAlert> { it.priority }.thenBy { it.id })
        }
}
