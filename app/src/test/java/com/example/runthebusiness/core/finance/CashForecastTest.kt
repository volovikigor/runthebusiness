package com.example.runthebusiness.core.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class CashForecastTest {
    @Test
    fun overdueAndCurrentPaymentsUseBankWithoutUsingCashOrAddingUnrealizedSales() {
        val forecast = CashForecast.project(
            today = 31, cashCents = 20_000L, bankCents = 8_000L,
            dailyCashChangeCents = 200L, dailyBankChangeCents = 1_000L,
            payments = listOf(
                ForecastPayment("Rent", 28, 3_000L),
                ForecastPayment("Loan", 31, 2_000L),
                ForecastPayment("Payroll", 32, 3_500L),
                ForecastPayment("Outside horizon", 39, 8_000L)
            )
        )
        assertEquals(8, forecast.size)
        assertEquals(listOf(28, 31), forecast.first().payments.map { it.dueDay })
        assertEquals(3_000L, forecast.first().bankCents)
        assertEquals(20_000L, forecast.first().cashCents)
        assertEquals(500L, forecast[1].bankCents)
        assertEquals(500L, forecast[1].bankShortfallCents)
        assertEquals(20_200L, forecast[1].cashCents)
        assertEquals(38, forecast.last().day)
    }

    @Test
    fun loanScheduleSplitsInterestAndPrincipalAndSettlesRoundingAtMaturity() {
        val schedule = CashForecast.loanSchedule(5_000_000L, 0.06, 96_664L, 31, 60)
        assertEquals(60, schedule.size)
        assertEquals(LoanInstallment(31, 71_664L, 25_000L), schedule.first())
        assertEquals(61, schedule[1].dueDay)
        assertEquals(5_000_000L, schedule.sumOf { it.principalCents })
    }
}
