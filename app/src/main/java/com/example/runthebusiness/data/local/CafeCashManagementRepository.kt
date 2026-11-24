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

    suspend fun position(sessionId: String = "debug_session_001"): CashPosition =
        withContext(Dispatchers.IO) {
            val dao = db.accountingDao()
            CashPosition(
                cashCents = dao.accountBalance(sessionId, "1000"),
                bankCents = dao.accountBalance(sessionId, "1100"),
                rentPayableCents = (-dao.accountBalance(sessionId, "2000")).coerceAtLeast(0L),
                salariesPayableCents = (-dao.accountBalance(sessionId, "2300")).coerceAtLeast(0L)
            )
        }

    suspend fun transferCashToBank(amountCents: Long, sessionId: String = "debug_session_001") =
        withContext(Dispatchers.IO) {
            require(amountCents > 0L)
            db.withTransaction {
                val session = checkNotNull(db.accountingDao().getSession(sessionId))
                val business = db.gameSetupDao().getBusinesses(sessionId).single()
                require(db.accountingDao().accountBalance(sessionId, "1000") >= amountCents) {
                    "Insufficient cash for bank deposit"
                }
                val id = UUID.randomUUID().toString()
                val entryId = "bank_deposit_$id"
                val eventId = "event_$entryId"
                db.accountingDao().saveAccountingOperation(
                    BusinessEventEntity(
                        id = eventId, sessionId = sessionId, businessId = business.id,
                        eventType = "BANK_DEPOSIT", sourceObjectType = "BANK_TRANSFER",
                        sourceObjectId = id, gameDay = session.currentDay,
                        gameHour = session.currentHour, gameMinute = session.currentMinute,
                        description = "Deposit cash into bank"
                    ),
                    JournalEntryEntity(
                        id = entryId, sessionId = sessionId, businessId = business.id,
                        sourceEventId = eventId, description = "Deposit cash into bank",
                        gameDay = session.currentDay, gameHour = session.currentHour,
                        gameMinute = session.currentMinute
                    ),
                    listOf(
                        JournalLineEntity(
                            id = "${entryId}_1", entryId = entryId, lineNumber = 1,
                            accountCode = "1100", locationId = "",
                            debitCents = amountCents, creditCents = 0L
                        ),
                        JournalLineEntity(
                            id = "${entryId}_2", entryId = entryId, lineNumber = 2,
                            accountCode = "1000", locationId = "",
                            debitCents = 0L, creditCents = amountCents
                        )
                    )
                )
            }
        }

    suspend fun pay(accountCode: String, sessionId: String = "debug_session_001", amountCents: Long? = null): Long =
        withContext(Dispatchers.IO) {
            require(accountCode == "2000" || accountCode == "2300") {
                "Only rent and salary settlements are supported"
            }
            db.withTransaction {
                val session = checkNotNull(db.accountingDao().getSession(sessionId))
                val business = db.gameSetupDao().getBusinesses(sessionId).single()
                val outstanding = (-db.accountingDao().accountBalance(sessionId, accountCode)).coerceAtLeast(0L)
                val due = amountCents ?: outstanding
                require(due <= outstanding)
                require(due > 0L) { "No outstanding payable on account $accountCode" }
                val bank = db.accountingDao().accountBalance(sessionId, "1100")
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
                            accountCode = accountCode, locationId = "",
                            debitCents = due, creditCents = 0L
                        ),
                        JournalLineEntity(
                            id = "${entryId}_2", entryId = entryId, lineNumber = 2,
                            accountCode = "1100", locationId = "",
                            debitCents = 0L, creditCents = due
                        )
                    )
                )
                due
            }.also { CafePaymentConsequencesRepository.invalidate() }
        }
}
