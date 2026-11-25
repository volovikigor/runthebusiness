package com.example.runthebusiness.data.local

import com.example.runthebusiness.data.local.dao.JournalWithLines
import com.example.runthebusiness.data.local.entity.JournalEntryEntity
import com.example.runthebusiness.data.local.entity.JournalLineEntity
import com.example.runthebusiness.data.local.entity.LedgerAccountEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CafeFinancialStatementsTest {
    @Test
    fun comparesPeriodActivityAndCumulativeBalancesWithoutCountingAnInternalDeposit() {
        val journal = listOf(
            entry(1, "1100" to 1_000L, "2100" to -1_000L),
            entry(30, "1000" to 120L, "4000" to -100L, "2200" to -20L),
            entry(31, "1100" to 100L, "1000" to -100L),
            entry(31, "1400" to 80L, "2010" to -80L),
            entry(32, "2010" to 80L, "1100" to -80L),
            entry(32, "5200" to 40L, "2300" to -40L)
        )
        val accounts = listOf(
            "1000" to "ASSET", "1100" to "ASSET", "1400" to "ASSET",
            "2100" to "LIABILITY", "2200" to "LIABILITY", "2010" to "LIABILITY",
            "2300" to "LIABILITY", "4000" to "REVENUE", "5200" to "EXPENSE"
        ).map { (code, type) -> LedgerAccountEntity("session", "cafe", code, code, type) }

        val previous = financialPeriod(1..30, journal, accounts, emptyList())
        val current = financialPeriod(31..32, journal, accounts, emptyList())

        assertEquals(100L, previous.profitCents)
        assertEquals(-40L, current.profitCents)
        assertEquals(60L, current.equity.single().amountCents)
        assertEquals(1_120L, current.openingCashCents)
        assertEquals(previous.closingCashCents, current.openingCashCents)
        assertEquals(1_040L, current.closingCashCents)
        assertEquals(mapOf(CashFlowLine.BORROWING to 1_000L, CashFlowLine.SALES to 120L), previous.cashFlows)
        assertEquals(mapOf(CashFlowLine.STOCK to -80L), current.cashFlows)
        assertEquals(current.closingCashCents - current.openingCashCents, current.cashFlows.values.sum())
        assertEquals(current.assets.sumOf { it.amountCents },
            current.liabilities.sumOf { it.amountCents } + current.equity.sumOf { it.amountCents })
    }

    @Test
    fun separatesLoanPrincipalFromInterestWhenBothArePaidTogether() {
        val payment = entry(31, "2100" to 90L, "5700" to 10L, "1100" to -100L)

        assertEquals(mapOf(CashFlowLine.PRINCIPAL to -90L, CashFlowLine.INTEREST to -10L),
            cashMovement(payment))
    }

    private fun entry(day: Int, vararg postings: Pair<String, Long>): JournalWithLines {
        val id = "entry-$day"
        return JournalWithLines(
            JournalEntryEntity(id, "session", "cafe", null, "", day, 8, 0),
            postings.mapIndexed { index, (account, amount) ->
                JournalLineEntity("$id-$index", id, index, account,
                    debitCents = amount.coerceAtLeast(0L), creditCents = (-amount).coerceAtLeast(0L))
            }
        )
    }
}
