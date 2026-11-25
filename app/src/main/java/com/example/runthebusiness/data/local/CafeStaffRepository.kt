package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.runthebusiness.core.finance.CafeTaxPolicy
import com.example.runthebusiness.core.finance.dailyMonthlyAccrual
import com.example.runthebusiness.data.local.entity.EmployeeEntity
import com.example.runthebusiness.engine.model.Employee
import com.example.runthebusiness.industries.cafe.simulation.CafeStaffCapacityRules
import java.util.UUID

internal val EmployeeEntity.plannedActive: Boolean get() = activeNextDay ?: isActive

internal fun List<EmployeeEntity>.staffCostCents(day: Int): Long = sumOf { employee ->
    val gross = dailyMonthlyAccrual(employee.monthlyGrossSalaryCents, day)
    gross + CafeTaxPolicy.employerSocialCents(gross)
}

data class CafeStaffSnapshot(
    val today: Int,
    val currentCount: Int,
    val plannedCount: Int,
    val currentCapacity: Double,
    val plannedCapacity: Double,
    val currentCostCents: Long,
    val plannedCostCents: Long,
    val peakWorkload: Int,
    val capacityLosses: Int,
    val recentCapacityLosses: Int
)

class CafeStaffRepository(context: Context) {
    private val db = GameDatabase.getInstance(context.applicationContext)
    private val operations = db.operationsDao()

    suspend fun snapshot(sessionId: String = "debug_session_001"): CafeStaffSnapshot? = db.withTransaction {
        val today = db.accountingDao().getSession(sessionId)?.currentDay ?: return@withTransaction null
        val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull() ?: return@withTransaction null
        val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
            ?: return@withTransaction null
        val employees = operations.getEmployees(business.id).filter { it.locationId == location.id }
        val current = employees.filter { it.isActive }
        val planned = employees.filter { it.plannedActive }
        CafeStaffSnapshot(
            today, current.size, planned.size,
            CafeStaffCapacityRules.slotCapacity(current.map { it.productivity }),
            CafeStaffCapacityRules.slotCapacity(planned.map { it.productivity }),
            current.staffCostCents(today), planned.staffCostCents(today + 1),
            operations.peakStaffWorkload(location.id, today - 1),
            db.cafeArrivalDao().capacityLossesForDay(location.id, today - 1),
            db.cafeArrivalDao().capacityLosses(location.id, (today - 30).coerceAtLeast(1), today - 1)
        )
    }

    suspend fun changePlannedSize(delta: Int, sessionId: String = "debug_session_001") {
        require(delta == 1 || delta == -1)
        db.withTransaction {
            val business = db.gameSetupDao().getBusinesses(sessionId).single()
            val location = db.businessLocationDao().getLocations(business.id).single()
            val employees = operations.getEmployees(business.id)
                .filter { it.locationId == location.id }.sortedBy { it.id }
            val planned = employees.filter { it.plannedActive }
            if (delta < 0) {
                require(planned.size > 1) { "Keep at least one employee on the team" }
                val employee = planned.lastOrNull { !it.isActive } ?: planned.last()
                check(operations.planEmployeeStatus(employee.id, if (employee.isActive) false else null) == 1)
            } else {
                val employee = employees.firstOrNull { it.isActive && !it.plannedActive }
                    ?: employees.firstOrNull { !it.plannedActive }
                if (employee != null) {
                    check(operations.planEmployeeStatus(employee.id, if (employee.isActive) null else true) == 1)
                } else {
                    val hire = Employee()
                    operations.insertEmployees(listOf(EmployeeEntity(
                        id = "hire_${UUID.randomUUID()}",
                        locationId = location.id,
                        name = "Café employee",
                        role = hire.role.name,
                        monthlyGrossSalaryCents = hire.monthlyGrossSalaryCents,
                        weeklyWorkingHours = hire.weeklyWorkingHours,
                        contractType = hire.contractType.name,
                        payrollProfileId = hire.payrollProfileId,
                        productivity = hire.productivity,
                        isActive = false,
                        activeNextDay = true
                    )))
                }
            }
        }
    }

    internal suspend fun applyNextDay(sessionId: String) {
        val business = db.gameSetupDao().getBusinesses(sessionId).single()
        operations.applyNextDayStaffing(business.id)
    }
}
