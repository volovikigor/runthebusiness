package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.core.finance.dailyMonthlyAccrual
import com.example.runthebusiness.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simplified 30-day game month. Accruals are non-cash and idempotent per day.
 * Rent: Dr 5400 / Cr 2000; wages: Dr 5200 / Cr 2300;
 * depreciation: Dr 5600 / Cr 1590.
 * Actual settlement and employer payroll taxes are future milestones.
 */
class CafeDailyCloseRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)

    suspend fun lastClosedDay(sessionId: String = "debug_session_001"): Int =
        withContext(Dispatchers.IO) {
            db.accountingDao().entryIdsWithPrefix(sessionId, "daily_close_%")
                .mapNotNull { it.removePrefix("daily_close_").substringBefore("_").toIntOrNull() }
                .maxOrNull() ?: 0
        }

    suspend fun closeDay(day: Int, sessionId: String = "debug_session_001") =
        withContext(Dispatchers.IO) {
            require(day >= 1)
            db.withTransaction {
                val session = checkNotNull(db.accountingDao().getSession(sessionId))
                require(day < session.currentDay) { "Cannot close a day before midnight" }
                val business = db.gameSetupDao().getBusinesses(sessionId).single()
                val locations = db.businessLocationDao().getLocations(business.id)
                val employees = db.operationsDao().getEmployees(business.id)
                val equipment = db.operationsDao().getEquipment(business.id)
                val existing = db.accountingDao().entryIdsWithPrefix(sessionId, "daily_close_${day}_%").toSet()
                data class Accrual(val kind: String, val locationId: String,
                    val description: String, val debit: String, val credit: String, val amount: Long)
                val accruals = mutableListOf<Accrual>()
                locations.forEach { loc ->
                    val rent = dailyMonthlyAccrual(loc.monthlyRentCents, day)
                    accruals += Accrual("rent", loc.id, "Daily rent accrual · ${loc.name}",
                        "5400", "2000", rent)
                }
                employees.filter { it.isActive }.forEach { employee ->
                    val wage = dailyMonthlyAccrual(employee.monthlyGrossSalaryCents, day)
                    accruals += Accrual("salary_${employee.id}", employee.locationId,
                        "Daily gross salary · ${employee.name}", "5200", "2300", wage)
                }
                equipment.filter { it.isOperational && day >= it.placedInServiceDay &&
                    it.usefulLifeMonths > 0 }.forEach { asset ->
                    val depreciable = (asset.purchaseCostCents - asset.residualValueCents)
                        .coerceAtLeast(0L)
                    val days = asset.usefulLifeMonths.toLong() * 30L
                    val elapsed = (day - asset.placedInServiceDay).toLong()
                    if (elapsed < days) {
                        val amount = depreciable / days +
                            if (elapsed < depreciable % days) 1L else 0L
                        accruals += Accrual("depreciation_${asset.id}", asset.locationId,
                            "Daily depreciation · ${asset.name}", "5600", "1590", amount)
                    }
                }
                for (a in accruals) {
                    if (a.amount <= 0L) continue
                    val id = "daily_close_${day}_${a.kind}_${a.locationId}"
                    if (id in existing) continue
                    val eventId = "event_$id"
                    db.accountingDao().saveAccountingOperation(
                        BusinessEventEntity(
                            id = eventId, sessionId = sessionId, businessId = business.id,
                            eventType = "DAILY_ACCRUAL", sourceObjectType = "DAILY_CLOSE",
                            sourceObjectId = id, gameDay = day, gameHour = 23,
                            gameMinute = 59, description = a.description
                        ),
                        JournalEntryEntity(
                            id = id, sessionId = sessionId, businessId = business.id,
                            sourceEventId = eventId, description = a.description,
                            gameDay = day, gameHour = 23, gameMinute = 59
                        ),
                        listOf(
                            JournalLineEntity(
                                id = "${id}_1", entryId = id, lineNumber = 1,
                                accountCode = a.debit, locationId = a.locationId,
                                debitCents = a.amount, creditCents = 0L
                            ),
                            JournalLineEntity(
                                id = "${id}_2", entryId = id, lineNumber = 2,
                                accountCode = a.credit, locationId = a.locationId,
                                debitCents = 0L, creditCents = a.amount
                            )
                        )
                    )
                }
                if (accruals.any { it.amount > 0L && "daily_close_${day}_${it.kind}_${it.locationId}" !in existing }) {
                    CafePaymentConsequencesRepository.invalidate()
                }
            }
        }
}
