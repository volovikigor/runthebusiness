package com.example.runthebusiness.core.finance

import kotlin.math.roundToLong

data class ForecastPayment(val label: String, val dueDay: Int, val amountCents: Long)

data class LoanInstallment(val dueDay: Int, val principalCents: Long, val interestCents: Long) {
    val totalCents: Long get() = principalCents + interestCents
}

data class ForecastDay(
    val day: Int,
    val cashCents: Long,
    val bankCents: Long,
    val bankShortfallCents: Long,
    val payments: List<ForecastPayment>
)

object CashForecast {
    fun loanSchedule(
        outstandingCents: Long,
        annualInterestRate: Double,
        monthlyPaymentCents: Long,
        firstDueDay: Int,
        termMonths: Int
    ): List<LoanInstallment> {
        if (outstandingCents <= 0L || monthlyPaymentCents <= 0L || termMonths <= 0) return emptyList()
        var balance = outstandingCents
        return buildList {
            repeat(termMonths) { month ->
                if (balance == 0L) return@buildList
                val interest = (balance * annualInterestRate / 12.0).roundToLong()
                val principal = if (month == termMonths - 1) balance
                else (monthlyPaymentCents - interest).coerceIn(0L, balance)
                add(LoanInstallment(firstDueDay + month * 30, principal, interest))
                balance -= principal
            }
        }
    }

    fun project(
        today: Int,
        cashCents: Long,
        bankCents: Long,
        dailyCashChangeCents: Long,
        dailyBankChangeCents: Long,
        payments: List<ForecastPayment>
    ): List<ForecastDay> {
        var cash = cashCents
        var bank = bankCents
        val byDay = payments.groupBy { it.dueDay.coerceAtLeast(today) }
        return (today..today + 7).map { day ->
            val due = byDay[day].orEmpty()
            bank -= due.sumOf { it.amountCents }
            val bankBeforeTrading = bank
            if (day > today) {
                cash += dailyCashChangeCents
                bank += dailyBankChangeCents
            }
            ForecastDay(day, cash, bank, (-minOf(bankBeforeTrading, bank)).coerceAtLeast(0L), due)
        }
    }
}

internal fun dailyMonthlyAccrual(monthlyCents: Long, day: Int): Long =
    monthlyCents / 30L + if ((day - 1) % 30 + 1 <= monthlyCents % 30) 1L else 0L
