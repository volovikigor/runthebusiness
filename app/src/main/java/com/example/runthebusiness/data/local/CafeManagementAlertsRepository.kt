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

    suspend fun load(sessionId: String = "debug_session_001"): List<ManagementAlert> =
        withContext(Dispatchers.IO) {
            val available = stock.stock(sessionId)
            val position = cash.position(sessionId)
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
            if (position.totalDueCents > 0L && position.liquidityGapCents < 0L) {
                alerts += ManagementAlert(
                    id = "liquidity", priority = 0,
                    title = "Insufficient liquidity",
                    detail = "Accrued rent and payroll exceed cash and bank balances",
                    destination = "finance"
                )
            }
            if (position.rentPayableCents > 0L) {
                alerts += ManagementAlert(
                    id = "rent", priority = 2, title = "Rent payable",
                    detail = "Accrued rent remains unpaid",
                    destination = "finance"
                )
            }
            if (position.salariesPayableCents > 0L) {
                alerts += ManagementAlert(
                    id = "payroll", priority = 2, title = "Salaries payable",
                    detail = "Accrued gross salaries remain unpaid",
                    destination = "finance"
                )
            }
            alerts.sortedWith(compareBy<ManagementAlert> { it.priority }.thenBy { it.id })
        }
}
