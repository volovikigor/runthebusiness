package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import com.example.runthebusiness.engine.InitialGameFactory
import com.example.runthebusiness.engine.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists the opening company, chart of accounts, loan and journal atomically.
 *  Other operational SimulationState objects are not persisted yet.
 */
class GameRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)
    private val accounting = db.accountingDao()
    private val setup = db.gameSetupDao()

    suspend fun initializeOpeningGame(sessionId: String = "debug_session_001") = withContext(Dispatchers.IO) {
        val state = InitialGameFactory.create()
        val business = state.businesses.single()
        db.withTransaction {
            val existing = accounting.getSession(sessionId)
            if (existing == null) {
                val now = System.currentTimeMillis()
                accounting.insertSession(GameSessionEntity(
                    id = sessionId, name = business.name,
                    currentDay = state.currentTime.day,
                    currentHour = state.currentTime.hour,
                    currentMinute = state.currentTime.minute,
                    createdAtMillis = now, updatedAtMillis = now
                ))
            }
            if (setup.getBusinesses(sessionId).isEmpty()) {
                setup.insertBusiness(BusinessEntity(
                    id = business.id, sessionId = sessionId, name = business.name,
                    ownerId = business.ownerId, industry = business.industry.name,
                    currencyCode = business.currencyCode, isActive = business.isActive
                ))
                setup.insertAccounts(state.ledgerAccounts.map {
                    LedgerAccountEntity(sessionId, business.id, it.code, it.name, it.type.name)
                })
                setup.insertLoans(state.loans.map {
                    LoanEntity(
                        id = it.id, sessionId = sessionId, businessId = it.businessId,
                        principalCents = it.principalCents,
                        outstandingPrincipalCents = it.outstandingPrincipalCents,
                        annualInterestRate = it.annualInterestRate,
                        termMonths = it.termMonths,
                        monthlyPaymentCents = it.monthlyPaymentCents,
                        nextPaymentDay = it.nextPaymentAt.day,
                        nextPaymentHour = it.nextPaymentAt.hour,
                        nextPaymentMinute = it.nextPaymentAt.minute,
                        isActive = it.isActive
                    )
                })
            }
            val existingEntryIds = accounting.getJournalEntries(sessionId).map { it.id }.toSet()
            state.journalEntries.filterNot { it.id in existingEntryIds }.forEach { entry ->
                val event = BusinessEventEntity(
                    id = entry.sourceEventId, sessionId = sessionId,
                    businessId = entry.businessId,
                    eventType = "OPENING_TRANSACTION",
                    sourceObjectType = entry.sourceObjectType,
                    sourceObjectId = entry.sourceObjectId,
                    gameDay = entry.timestamp.day,
                    gameHour = entry.timestamp.hour,
                    gameMinute = entry.timestamp.minute,
                    description = entry.description
                )
                val header = JournalEntryEntity(
                    id = entry.id, sessionId = sessionId, businessId = entry.businessId,
                    sourceEventId = event.id, description = entry.description,
                    gameDay = entry.timestamp.day, gameHour = entry.timestamp.hour,
                    gameMinute = entry.timestamp.minute
                )
                val lines = entry.lines.mapIndexed { index, line ->
                    JournalLineEntity(
                        id = "${entry.id}_${index + 1}", entryId = entry.id,
                        lineNumber = index + 1, accountCode = line.accountCode,
                        locationId = line.locationId, debitCents = line.debitCents,
                        creditCents = line.creditCents
                    )
                }
                accounting.saveAccountingOperation(event, header, lines)
            }
        }
    }

    suspend fun loadOpeningCompany(sessionId: String = "debug_session_001"): OpeningCompany? =
        withContext(Dispatchers.IO) {
            val session = accounting.getSession(sessionId) ?: return@withContext null
            val businesses = setup.getBusinesses(sessionId)
            if (businesses.isEmpty()) return@withContext null
            OpeningCompany(
                sessionName = session.name,
                businesses = businesses.map {
                    Business(it.id, it.name, it.ownerId, Industry.valueOf(it.industry), it.currencyCode, it.isActive)
                },
                accounts = setup.getAccounts(sessionId).map {
                    LedgerAccount(it.code, it.name, AccountType.valueOf(it.type))
                },
                loans = setup.getLoans(sessionId).map {
                    Loan(
                        id = it.id, businessId = it.businessId,
                        principalCents = it.principalCents,
                        outstandingPrincipalCents = it.outstandingPrincipalCents,
                        annualInterestRate = it.annualInterestRate,
                        termMonths = it.termMonths,
                        monthlyPaymentCents = it.monthlyPaymentCents,
                        nextPaymentAt = GameTime(it.nextPaymentDay, it.nextPaymentHour, it.nextPaymentMinute),
                        isActive = it.isActive
                    )
                }
            )
        }
}

data class OpeningCompany(
    val sessionName: String,
    val businesses: List<Business>,
    val accounts: List<LedgerAccount>,
    val loans: List<Loan>
)
