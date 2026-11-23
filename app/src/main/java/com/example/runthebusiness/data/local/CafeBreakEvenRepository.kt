package com.example.runthebusiness.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

data class CafeBreakEven(
    val observedDays: Int,
    val orders: Int,
    val netSalesCents: Long,
    val cogsCents: Long,
    val fixedDailyCents: Long,
    val avgContributionCents: Double,
    val ordersNeededPerDay: Int?,
    val actualOrdersPerDay: Double,
    val targetNetSalesDailyCents: Long?,
    val rentDailyCents: Long,
    val payrollDailyCents: Long,
    val depreciationDailyCents: Long
)

/**
 * Diagnostic unit economics, based on the last 14 COMPLETED game days.
 * Not a forecast: excludes unmodelled expenses and employer social contributions.
 */
class CafeBreakEvenRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun snapshot(sessionId: String = "debug_session_001"): CafeBreakEven? =
        withContext(Dispatchers.IO) {
            val session = db.accountingDao().getSession(sessionId) ?: return@withContext null
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext null
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext null
            val lastComplete = session.currentDay - 1
            val first = (lastComplete - 13).coerceAtLeast(1)
            val days = (lastComplete - first + 1).coerceAtLeast(0)
            val orders = if (days > 0) db.cafeSalesDao().getOrders(location.id)
                .filter { it.gameDay in first..lastComplete } else emptyList()
            val rent = location.monthlyRentCents / 30L
            val wages = db.operationsDao().getEmployees(business.id)
                .filter { it.isActive }.sumOf { it.monthlyGrossSalaryCents } / 30L
            val depreciation = db.operationsDao().getEquipment(business.id)
                .filter { it.isOperational && it.usefulLifeMonths > 0 }
                .sumOf { (it.purchaseCostCents - it.residualValueCents).coerceAtLeast(0L) /
                    (it.usefulLifeMonths.toLong() * 30L) }
            val fixed = rent + wages + depreciation
            val net = orders.sumOf { it.netCents }
            val cogs = orders.sumOf { it.cogsCents }
            val contribution = if (orders.isNotEmpty()) (net - cogs).toDouble() / orders.size else 0.0
            val required = if (contribution > 0.0) ceil(fixed / contribution).toInt() else null
            val netPerOrder = if (orders.isNotEmpty()) net.toDouble() / orders.size else 0.0
            CafeBreakEven(
                observedDays = days, orders = orders.size, netSalesCents = net,
                cogsCents = cogs, fixedDailyCents = fixed,
                avgContributionCents = contribution, ordersNeededPerDay = required,
                actualOrdersPerDay = if (days > 0) orders.size.toDouble() / days else 0.0,
                targetNetSalesDailyCents = required?.let { ceil(it * netPerOrder).toLong() },
                rentDailyCents = rent, payrollDailyCents = wages,
                depreciationDailyCents = depreciation
            )
        }
}
