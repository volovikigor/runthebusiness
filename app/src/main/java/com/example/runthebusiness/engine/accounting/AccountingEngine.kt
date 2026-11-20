
package com.example.runthebusiness.engine.accounting

import com.example.runthebusiness.engine.model.*

class AccountingEngine {

    fun post(
        state: SimulationState,
        entry: JournalEntry
    ): SimulationState {
        require(entry.id.isNotBlank()) {
            "Journal entry ID is required"
        }

        require(entry.lines.size >= 2) {
            "Journal entry must contain at least two lines"
        }

        require(state.journalEntries.none { it.id == entry.id }) {
            "Duplicate journal entry: ${entry.id}"
        }

        val accounts = state.ledgerAccounts
            .associateBy { it.code }

        var totalDebit = 0L
        var totalCredit = 0L

        entry.lines.forEach { line ->
            require(accounts.containsKey(line.accountCode)) {
                "Unknown account: ${line.accountCode}"
            }

            require(
                line.debitCents >= 0 &&
                        line.creditCents >= 0
            ) {
                "Negative debit or credit is not allowed"
            }

            require(
                (line.debitCents > 0L) !=
                        (line.creditCents > 0L)
            ) {
                "Each line must contain either debit or credit"
            }

            totalDebit = Math.addExact(
                totalDebit,
                line.debitCents
            )

            totalCredit = Math.addExact(
                totalCredit,
                line.creditCents
            )
        }

        require(totalDebit == totalCredit) {
            "Unbalanced journal entry: " +
                    "debit=$totalDebit, credit=$totalCredit"
        }

        require(totalDebit > 0L) {
            "Journal entry amount must be positive"
        }

        val savedEntry = entry.copy(
            lines = entry.lines.map { it.copy() }
        )

        return state.copy(
            journalEntries =
                state.journalEntries + savedEntry
        )
    }

    fun accountBalanceCents(
        state: SimulationState,
        accountCode: String
    ): Long {
        val account = state.ledgerAccounts
            .firstOrNull { it.code == accountCode }
            ?: error("Unknown account: $accountCode")

        var debits = 0L
        var credits = 0L

        state.journalEntries.forEach { entry ->
            entry.lines
                .filter { it.accountCode == accountCode }
                .forEach { line ->
                    debits = Math.addExact(
                        debits,
                        line.debitCents
                    )
                    credits = Math.addExact(
                        credits,
                        line.creditCents
                    )
                }
        }

        return when (account.type) {
            AccountType.ASSET,
            AccountType.EXPENSE -> debits - credits

            AccountType.LIABILITY,
            AccountType.EQUITY,
            AccountType.REVENUE -> credits - debits
        }
    }
}
