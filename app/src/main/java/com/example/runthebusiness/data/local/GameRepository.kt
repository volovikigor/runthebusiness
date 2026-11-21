package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.data.local.entity.*
import com.example.runthebusiness.core.business.BusinessLocation
import com.example.runthebusiness.industries.cafe.toBusinessLocation
import com.example.runthebusiness.industries.cafe.CafeStartingConfiguration
import com.example.runthebusiness.industries.cafe.usecase.CreateCafeUseCase
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
    private val operations = db.operationsDao()
    private val locations = db.businessLocationDao()

    suspend fun initializeOpeningGame(sessionId: String = "debug_session_001") = withContext(Dispatchers.IO) {
        val state = CreateCafeUseCase()(CafeStartingConfiguration())
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
            // Existing v3 locations are migrated before this method is invoked.
            if (locations.countLocations(business.id) == 0) {
                locations.insertLocations(state.locations.map {
                    val shared = it.toBusinessLocation()
                    BusinessLocationEntity(
                        id = shared.id, businessId = shared.businessId, name = shared.name,
                        openingHour = shared.openingHour, closingHour = shared.closingHour,
                        monthlyRentCents = shared.monthlyRentCents, isOpen = shared.isOpen
                    )
                })
            }
            // Backfill operational objects for existing v2 saves, without duplicating them.
            if (operations.getLocations(business.id).isEmpty()) {
                operations.insertLocations(state.locations.map {
                    CafeLocationEntity(it.id, it.businessId, it.name, it.interiorStyle.name,
                        it.tableCount, it.seatsPerTable, it.averageVisitMinutes,
                        it.targetSeatOccupancy, it.takeawayShare, it.openingHour,
                        it.closingHour, it.monthlyRentCents, it.isOpen)
                })
                operations.insertEmployees(state.employees.map {
                    EmployeeEntity(it.id, it.locationId, it.name, it.role.name,
                        it.monthlyGrossSalaryCents, it.weeklyWorkingHours,
                        it.contractType.name, it.payrollProfileId, it.productivity, it.isActive)
                })
                operations.insertEquipment(state.equipment.map {
                    EquipmentEntity(it.id, it.locationId, it.name, it.purchaseCostCents,
                        it.usefulLifeMonths, it.conditionPercent, it.capacityPerHour,
                        it.accumulatedDepreciationCents, it.residualValueCents,
                        it.placedInServiceAt.day, it.placedInServiceAt.hour,
                        it.placedInServiceAt.minute, it.isOperational)
                })
                operations.insertInventoryItems(state.inventory.map {
                    InventoryItemEntity(it.id, it.locationId, it.name, it.unit.name,
                        it.reorderLevel, it.isPerishable, it.shelfLifeDays)
                })
                operations.insertInventoryBatches(state.inventoryBatches.map {
                    InventoryBatchEntity(it.id, it.inventoryItemId, it.locationId,
                        it.quantityReceived, it.quantityRemaining, it.unitCostCents,
                        it.receivedAt.day, it.receivedAt.hour, it.receivedAt.minute,
                        it.expiresAt?.day, it.expiresAt?.hour, it.expiresAt?.minute)
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

    /** Shared location identity loaded from SQLite. */
    suspend fun loadBusinessLocations(sessionId: String = "debug_session_001"): List<BusinessLocation> =
        withContext(Dispatchers.IO) {
            val business = setup.getBusinesses(sessionId).singleOrNull()
                ?: return@withContext emptyList()
            locations.getLocations(business.id).map {
                BusinessLocation(it.id, it.businessId, it.name, it.openingHour,
                    it.closingHour, it.monthlyRentCents, it.isOpen)
            }
        }

    /** Read operational records from SQLite, not from InitialGameFactory. */
    suspend fun loadOperations(sessionId: String = "debug_session_001"): OpeningOperations? =
        withContext(Dispatchers.IO) {
            val business = setup.getBusinesses(sessionId).singleOrNull() ?: return@withContext null
            val id = business.id
            OpeningOperations(
                locations = operations.getLocations(id).map {
                    CafeLocation(it.id, it.businessId, it.name,
                        InteriorStyle.valueOf(it.interiorStyle), it.tableCount, it.seatsPerTable,
                        it.averageVisitMinutes, it.targetSeatOccupancy, it.takeawayShare,
                        it.openingHour, it.closingHour, it.monthlyRentCents, it.isOpen)
                },
                employees = operations.getEmployees(id).map {
                    Employee(it.id, it.locationId, it.name, EmployeeRole.valueOf(it.role),
                        it.monthlyGrossSalaryCents, it.weeklyWorkingHours,
                        EmploymentContractType.valueOf(it.contractType), it.payrollProfileId,
                        it.productivity, it.isActive)
                },
                equipment = operations.getEquipment(id).map {
                    Equipment(it.id, it.locationId, it.name, it.purchaseCostCents,
                        it.usefulLifeMonths, it.conditionPercent, it.capacityPerHour,
                        it.accumulatedDepreciationCents, it.residualValueCents,
                        GameTime(it.placedInServiceDay, it.placedInServiceHour,
                            it.placedInServiceMinute), it.isOperational)
                },
                inventory = operations.getInventoryItems(id).map {
                    InventoryItem(it.id, it.locationId, it.name, StockUnit.valueOf(it.unit),
                        it.reorderLevel, it.isPerishable, it.shelfLifeDays)
                },
                batches = operations.getInventoryBatches(id).map {
                    InventoryBatch(it.id, it.inventoryItemId, it.locationId,
                        it.quantityReceived, it.quantityRemaining, it.unitCostCents,
                        GameTime(it.receivedDay, it.receivedHour, it.receivedMinute),
                        it.expiresDay?.let { day ->
                            GameTime(day, it.expiresHour ?: 0, it.expiresMinute ?: 0)
                        })
                }
            )
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

data class OpeningOperations(
    val locations: List<CafeLocation>,
    val employees: List<Employee>,
    val equipment: List<Equipment>,
    val inventory: List<InventoryItem>,
    val batches: List<InventoryBatch>
)
