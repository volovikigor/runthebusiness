package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.core.finance.CafeTaxPolicy
import com.example.runthebusiness.core.finance.CashForecast
import com.example.runthebusiness.core.finance.ForecastDay
import com.example.runthebusiness.core.finance.ForecastPayment
import com.example.runthebusiness.core.finance.LoanInstallment
import com.example.runthebusiness.core.finance.dailyMonthlyAccrual

data class CafeCashForecast(
    val days: List<ForecastDay>,
    val historyDays: Int,
    val installments: List<LoanInstallment>,
    val staffCostTodayCents: Long,
    val staffCostTomorrowCents: Long
)

class CafeCashForecastRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)
    private val bills = CafePaymentRegisterRepository(context)

    suspend fun load(sessionId: String = "debug_session_001"): CafeCashForecast? = db.withTransaction {
        val dao = db.accountingDao()
        val today = dao.getSession(sessionId)?.currentDay ?: return@withTransaction null
        val setup = db.gameSetupDao()
        val business = setup.getBusinesses(sessionId).singleOrNull() ?: return@withTransaction null
        val locations = db.businessLocationDao().getLocations(business.id)
        val staff = db.operationsDao().getEmployees(business.id)
        val currentStaff = staff.filter { it.isActive }
        val nextDayStaff = staff.filter { it.plannedActive }
        val historyDays = minOf(7, today - 1)
        suspend fun average(account: String): Long {
            if (historyDays == 0) return 0L
            return dao.tradingCashMovement(
                sessionId, account, today - historyDays, today, locations.any { it.isOpen }
            ) / historyDays
        }

        val installments = setup.getLoans(sessionId).filter { it.isActive }.flatMap {
            CashForecast.loanSchedule(
                it.outstandingPrincipalCents, it.annualInterestRate, it.monthlyPaymentCents,
                it.nextPaymentDay, it.termMonths
            )
        }.filter { it.dueDay >= today }.sortedBy { it.dueDay }
        val payments = installments.map { ForecastPayment("Loan installment", it.dueDay, it.totalCents) }
            .toMutableList()
        payments += bills.supplierBills(sessionId).map { ForecastPayment(it.label, it.dueDay, it.amountCents) }
        for (bill in bills.snapshot(sessionId)) {
            if (bill.due > 0L) {
                payments += ForecastPayment(bill.label, bill.nextDueDay - bill.periodDays, bill.due)
            }
            if (bill.nextDueDay > today + 7) continue
            val futureAccrual = when (bill.account) {
                "2000" -> (today until bill.nextDueDay).sumOf { day ->
                    locations.sumOf { dailyMonthlyAccrual(it.monthlyRentCents, day) }
                }
                "2300", "2400" -> (today until bill.nextDueDay).sumOf { day ->
                    val workingStaff = if (day == today) currentStaff else nextDayStaff
                    workingStaff.sumOf { employee ->
                        val gross = dailyMonthlyAccrual(employee.monthlyGrossSalaryCents, day)
                        val withheld = CafeTaxPolicy.employeeSocialCents(gross)
                        if (bill.account == "2300") gross - withheld
                        else withheld + CafeTaxPolicy.employerSocialCents(gross)
                    }
                }
                "2200" -> if (historyDays > 0 && locations.any { it.isOpen }) {
                    val futureDays = (bill.nextDueDay - today - 1).coerceAtLeast(0)
                    dao.vatFromSales(sessionId, today - historyDays, today) * futureDays / historyDays
                } else 0L
                "2600" -> CafeTaxPolicy.corporateTaxCents(dao.periodProfitBeforeTax(
                    sessionId, bill.nextDueDay - bill.periodDays, today
                ))
                else -> 0L
            }
            val nextBill = (bill.accrued - bill.due).coerceAtLeast(0L) + futureAccrual
            if (nextBill > 0L) payments += ForecastPayment(bill.label, bill.nextDueDay, nextBill)
        }

        CafeCashForecast(
            days = CashForecast.project(
                today, dao.accountBalance(sessionId, "1000"), dao.accountBalance(sessionId, "1100"),
                average("1000"), average("1100"), payments
            ),
            historyDays = historyDays,
            installments = installments,
            staffCostTodayCents = currentStaff.staffCostCents(today),
            staffCostTomorrowCents = nextDayStaff.staffCostCents(today + 1)
        )
    }
}
