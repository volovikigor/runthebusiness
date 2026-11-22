package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class CashPosition(
    val cashCents: Long,
    val bankCents: Long,
    val rentPayableCents: Long,
    val salariesPayableCents: Long
) {
    val totalCashCents: Long get() = cashCents + bankCents
    val totalDueCents: Long get() = rentPayableCents + salariesPayableCents
    val liquidityGapCents: Long get() = totalCashCents - totalDueCents
}

/** Settles accrued rent or gross salary from the bank, without creating a second expense. */
class CafeCashManagementRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    private suspend fun balances(sessionId: String): Map<String, Long> {
        val sums = mutableMapOf<String, Long>()
        db.journalViewDao().getJournal(sessionId).forEach { entry ->
            entry.lines.forEach { line ->
                sums[line.accountCode] = (sums[line.accountCode] ?: 0L) +
                    line.debitCents - line.creditCents
            }
        }
        return sums
    }

    suspend fun position(sessionId: String = "debug_session_001"): CashPosition =
        withContext(Dispatchers.IO) {
            val b = balances(sessionId)
            CashPosition(
                cashCents = b["1000"] ?: 0L,
                bankCents = b["1100"] ?: 0L,
                rentPayableCents = (-(b["2000"] ?: 0L)).coerceAtLeast(0L),
                salariesPayableCents = (-(b["2300"] ?: 0L)).coerceAtLeast(0L)
            )
        }

    suspend fun pay(accountCode: String, sessionId: String = "debug_session_001"): Long =
        withContext(Dispatchers.IO) {
            require(accountCode == "2000" || accountCode == "2300") {
                "Only rent and salary settlements are supported"
            }
            db.withTransaction {
                val session = checkNotNull(db.accountingDao().getSession(sessionId))
                val business = db.gameSetupDao().getBusinesses(sessionId).single()
                val b = balances(sessionId)
                val due = (-(b[accountCode] ?: 0L)).coerceAtLeast(0L)
                require(due > 0L) { "No outstanding payable on account $accountCode" }
                val bank = b["1100"] ?: 0L
                require(bank >= due) {
                    "Insufficient bank balance: due €%.2f, available €%.2f".format(
                        java.util.Locale.ROOT, due / 100.0, bank / 100.0)
                }
                val id = UUID.randomUUID().toString()
                val entryId = "settlement_$id"
                val eventId = "event_$entryId"
                val description = if (accountCode == "2000") "Rent payable settled by bank"
                    else "Gross salaries payable settled by bank"
                db.accountingDao().saveAccountingOperation(
                    BusinessEventEntity(
                        id = eventId, sessionId = sessionId, businessId = business.id,
                        eventType = "PAYABLE_SETTLEMENT", sourceObjectType = "LEDGER_ACCOUNT",
                        sourceObjectId = accountCode, gameDay = session.currentDay,
                        gameHour = session.currentHour, gameMinute = session.currentMinute,
                        description = description
                    ),
                    JournalEntryEntity(
                        id = entryId, sessionId = sessionId, businessId = business.id,
                        sourceEventId = eventId, description = description,
                        gameDay = session.currentDay, gameHour = session.currentHour,
                        gameMinute = session.currentMinute
                    ),
                    listOf(
                        JournalLineEntity(
                            id = "${entryId}_1", entryId = entryId, lineNumber = 1,
                            accountCode = accountCode, locationId = null,
                            debitCents = due, creditCents = 0L
                        ),
                        JournalLineEntity(
                            id = "${entryId}_2", entryId = entryId, lineNumber = 2,
                            accountCode = "1100", locationId = null,
                            debitCents = 0L, creditCents = due
                        )
                    )
                )
                due
            }
        }
}
