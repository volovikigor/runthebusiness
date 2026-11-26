package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.BusinessEventEntity
import com.example.runthebusiness.data.local.entity.JournalEntryEntity
import com.example.runthebusiness.data.local.entity.JournalLineEntity
import com.example.runthebusiness.data.local.entity.LedgerAccountEntity
import java.util.UUID

internal const val CREDIT_LINE_PRINCIPAL = "2120"
internal const val CREDIT_LINE_INTEREST = "2121"

internal val cafeCreditAccountNames = mapOf(
    CREDIT_LINE_PRINCIPAL to "Credit line principal",
    CREDIT_LINE_INTEREST to "Credit line interest payable"
)

data class CafeCreditLine(val principalCents: Long, val interestCents: Long) {
    val availableCents: Long get() = (LIMIT_CENTS - principalCents).coerceAtLeast(0L)
    val owedCents: Long get() = principalCents + interestCents

    companion object {
        const val LIMIT_CENTS = 500_000L
        const val ANNUAL_RATE_PERCENT = 12
        // The café calendar has twelve 30-day months.
        internal fun dailyInterestCents(principalCents: Long): Long =
            (principalCents * ANNUAL_RATE_PERCENT + 18_000L) / 36_000L
    }
}

class CafeCreditLineRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun position(sessionId: String = "debug_session_001"): CafeCreditLine = db.withTransaction {
        val dao = db.accountingDao()
        CafeCreditLine((-dao.accountBalance(sessionId, CREDIT_LINE_PRINCIPAL)).coerceAtLeast(0L),
            (-dao.accountBalance(sessionId, CREDIT_LINE_INTEREST)).coerceAtLeast(0L))
    }

    suspend fun draw(cents: Long, sessionId: String = "debug_session_001") = db.withTransaction {
        require(cents in 1..position(sessionId).availableCents) { "Amount exceeds the available credit line" }
        val setup = db.gameSetupDao()
        val businessId = setup.getBusinesses(sessionId).single().id
        val accounts = setup.getAccounts(sessionId).map { it.code }.toSet()
        val missingAccounts = cafeCreditAccountNames.filterKeys { it !in accounts }
        if (missingAccounts.isNotEmpty()) {
            setup.insertAccounts(missingAccounts.map { (code, name) ->
                LedgerAccountEntity(sessionId, businessId, code, name, "LIABILITY")
            })
        }
        post(sessionId, businessId, "CREDIT_LINE_DRAW", "Credit line draw",
            listOf("1100" to cents, CREDIT_LINE_PRINCIPAL to -cents))
    }

    suspend fun repay(cents: Long, sessionId: String = "debug_session_001") = db.withTransaction {
        val credit = position(sessionId)
        val bank = db.accountingDao().accountBalance(sessionId, "1100")
        require(cents in 1..minOf(credit.owedCents, bank)) { "Amount exceeds credit debt or bank funds" }
        val interest = minOf(cents, credit.interestCents)
        val principal = cents - interest
        post(sessionId, db.gameSetupDao().getBusinesses(sessionId).single().id,
            "CREDIT_LINE_REPAYMENT", "Credit line repayment",
            listOf(CREDIT_LINE_INTEREST to interest, CREDIT_LINE_PRINCIPAL to principal, "1100" to -cents))
    }

    private suspend fun post(
        sessionId: String,
        businessId: String,
        type: String,
        description: String,
        postings: List<Pair<String, Long>>
    ) {
        val dao = db.accountingDao()
        val session = checkNotNull(dao.getSession(sessionId))
        val id = "credit_line_${UUID.randomUUID()}"
        dao.saveAccountingOperation(
            BusinessEventEntity(id, sessionId, businessId, type, "CREDIT_LINE", businessId,
                session.currentDay, session.currentHour, session.currentMinute, description),
            JournalEntryEntity(id, sessionId, businessId, id, description,
                session.currentDay, session.currentHour, session.currentMinute),
            postings.filter { it.second != 0L }.mapIndexed { index, (account, signed) ->
                JournalLineEntity("${id}_$index", id, index + 1, account,
                    debitCents = signed.coerceAtLeast(0L), creditCents = (-signed).coerceAtLeast(0L))
            }
        )
    }
}
