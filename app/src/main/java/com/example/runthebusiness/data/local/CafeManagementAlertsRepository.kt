package com.example.runthebusiness.data.local

import android.content.Context
import com.example.runthebusiness.R
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
class CafeManagementAlertsRepository(private val context: Context) {
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
            // Game cadence: rent and payroll on days 14, 28, 42, ...
            // Accruals remain visible in Finance every day; alerts are only for due/late bills.
            fun lastDueDay(period: Int): Int = (day / period) * period
            suspend fun isDue(period: Int, account: String): Boolean =
                day >= period && (db.accountingDao().lastSettlementDay(sessionId, account) ?: 0) <
                    lastDueDay(period)
            fun payrollLate(): Boolean = impact.payrollDaysLate > 2
            fun rentLate(): Boolean = impact.rentDaysLate > 2
            val alerts = mutableListOf<ManagementAlert>()
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
            val installedIds = if (business == null) emptySet() else
                db.operationsDao().getInventoryItems(business.id).map { it.id }.toSet()
            val location = business?.let { db.businessLocationDao().getLocations(it.id).singleOrNull() }
            val products = location?.let { db.cafeMenuDao().getProducts(it.id) }.orEmpty()
            val requiredStockIds = mutableSetOf<String>()
            for (product in products.filter { it.isAvailable }) {
                if (product.fulfillment == "RESALE") {
                    product.finishedStockItemId?.let { requiredStockIds += it }
                } else if (product.fulfillment == "PREPARED") {
                    requiredStockIds += db.cafeMenuDao().getRecipe(product.id).map { it.ingredientId }
                }
            }
            CafePurchasingRepository.supplies.filter {
                it.itemId in installedIds && it.itemId in requiredStockIds
            }.forEach { supply ->
                val remaining = available[supply.itemId] ?: 0.0
                val threshold = supply.quantity * 0.2
                if (remaining <= threshold) {
                    alerts += ManagementAlert(
                        id = "stock_${supply.itemId}",
                        priority = if (remaining <= 0.0) 0 else 1,
                        title = context.getString(if (remaining <= 0.0) R.string.alert_no_stock else R.string.alert_low_stock, supply.label),
                        detail = context.getString(R.string.alert_stock_detail,
                            "%.0f".format(java.util.Locale.ROOT, remaining), supply.unit),
                        destination = "operations", section = 1
                    )
                }
            }
            val rentDue = position.rentPayableCents > 0L && isDue(14, "2000")
            val payrollDue = position.salariesPayableCents > 0L && isDue(14, "2300")
            if ((rentDue || payrollDue) && position.liquidityGapCents < 0L) {
                alerts += ManagementAlert(
                    id = "liquidity", priority = 0,
                    title = context.getString(R.string.alert_liquidity),
                    detail = context.getString(R.string.alert_liquidity_detail),
                    destination = "finance"
                )
            }
            if (rentDue) {
                alerts += ManagementAlert(
                    id = "rent", priority = if (rentLate()) 0 else 2,
                    title = if (rentLate()) context.getString(R.string.alert_rent_late) else context.getString(R.string.alert_rent_due),
                    detail = if (rentLate()) context.getString(R.string.alert_rent_late_detail)
                        else context.getString(R.string.alert_rent_due_detail),
                    destination = "finance"
                )
            }
            if (payrollDue) {
                alerts += ManagementAlert(
                    id = "payroll", priority = if (payrollLate()) 0 else 2,
                    title = if (payrollLate()) context.getString(R.string.alert_payroll_late) else context.getString(R.string.alert_payroll_due),
                    detail = if (payrollLate()) context.getString(R.string.alert_payroll_late_detail)
                        else context.getString(R.string.alert_payroll_due_detail),
                    destination = "finance"
                )
            }
            if (impact.staffDisruption) alerts += ManagementAlert(
                id = "staff_disruption", priority = 0,
                title = context.getString(R.string.alert_staff_disruption),
                detail = context.getString(R.string.alert_staff_detail),
                destination = "finance"
            )
            if (impact.rentShutdown) alerts += ManagementAlert(
                id = "rent_shutdown", priority = 0,
                title = context.getString(R.string.alert_rent_shutdown),
                detail = context.getString(R.string.alert_shutdown_detail),
                destination = "finance"
            )
            alerts.sortedWith(compareBy<ManagementAlert> { it.priority }.thenBy { it.id })
        }
}
