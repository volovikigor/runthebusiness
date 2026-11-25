package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.core.finance.CashForecast
import com.example.runthebusiness.core.finance.LoanInstallment
import com.example.runthebusiness.data.local.entity.BusinessEventEntity
import com.example.runthebusiness.data.local.entity.JournalEntryEntity
import com.example.runthebusiness.data.local.entity.JournalLineEntity

/** One outstanding starter loan per café. Ledger and schedule advance in the same transaction. */
class CafeLoanPaymentRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)
    private val cash = CafeCashManagementRepository(context)
    private val bills = CafePaymentRegisterRepository(context)

    suspend fun schedule(sessionId: String = "debug_session_001"): List<LoanInstallment> = db.withTransaction {
        val first = db.gameSetupDao().getLoans(sessionId).singleOrNull { it.isActive }
            ?: return@withTransaction emptyList()
        val dao = db.accountingDao()
        val today = dao.getSession(sessionId)?.currentDay ?: return@withTransaction emptyList()
        val projected = CashForecast.loanSchedule(first.outstandingPrincipalCents, first.annualInterestRate,
            first.monthlyPaymentCents, first.nextPaymentDay, first.termMonths)
        val owed = (-dao.accountBalance(sessionId, "2110")).coerceAtLeast(0L)
        if (projected.isEmpty() || today < first.nextPaymentDay) return@withTransaction projected
        val nextAfterPayment = nextCycleDay(first.nextPaymentDay, today)
        listOf(projected.first().copy(interestCents = owed)) +
            projected.drop(1).mapIndexed { i, installment ->
                installment.copy(dueDay = nextAfterPayment + i * 30)
            }
    }

    suspend fun settleDue(automatic: Boolean, sessionId: String = "debug_session_001"): Long = db.withTransaction {
        val dao = db.accountingDao()
        val session = dao.getSession(sessionId) ?: return@withTransaction 0L
        val setup = db.gameSetupDao()
        val loan = setup.getLoans(sessionId).singleOrNull { it.isActive } ?: return@withTransaction 0L
        if (session.currentDay < loan.nextPaymentDay) return@withTransaction 0L
        val payment = schedule(sessionId).firstOrNull() ?: return@withTransaction 0L
        if (payment.totalCents <= 0L) return@withTransaction 0L
        val position = cash.position(sessionId)
        val gap = (payment.totalCents - position.bankCents).coerceAtLeast(0L)
        if (gap > 0L) {
            if (!automatic || !bills.automaticDepositsEnabled() || position.cashCents < gap)
                return@withTransaction 0L
            cash.transferCashToBank(gap, sessionId)
        }
        val id = "loan_payment_${loan.id}_${loan.nextPaymentDay}"
        val description = "Starter loan installment"
        val postings = listOf("2100" to payment.principalCents, "2110" to payment.interestCents)
            .filter { it.second > 0L }
        dao.saveAccountingOperation(
            BusinessEventEntity(id, sessionId, loan.businessId, "LOAN_PAYMENT",
                "LOAN", loan.id, session.currentDay, session.currentHour, session.currentMinute, description),
            JournalEntryEntity(id, sessionId, loan.businessId, id, description,
                session.currentDay, session.currentHour, session.currentMinute),
            postings.mapIndexed { index, (code, cents) ->
                JournalLineEntity("${id}_${index + 1}", id, index + 1, code,
                    debitCents = cents, creditCents = 0L)
            } + JournalLineEntity("${id}_bank", id, postings.size + 1, "1100",
                debitCents = 0L, creditCents = payment.totalCents)
        )
        val balance = loan.outstandingPrincipalCents - payment.principalCents
        check(setup.updateLoan(loan.copy(
            outstandingPrincipalCents = balance,
            termMonths = (loan.termMonths - 1).coerceAtLeast(0),
            nextPaymentDay = nextCycleDay(loan.nextPaymentDay, session.currentDay),
            nextPaymentHour = 0,
            nextPaymentMinute = 0,
            isActive = balance > 0L
        )) == 1)
        payment.totalCents
    }
}

private fun nextCycleDay(dueDay: Int, today: Int): Int = dueDay + ((today - dueDay) / 30 + 1) * 30
