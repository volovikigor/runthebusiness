
package com.example.runthebusiness.engine.model

enum class Industry {
    CAFE, HOTEL, RETAIL, MANUFACTURING
}

enum class InteriorStyle {
    CLASSIC, MODERN, RETRO, INDUSTRIAL
}

enum class EmployeeRole {
    BARISTA, COOK, WAITER, MANAGER, CLEANER
}

data class Business(
    var id: String = "",
    var name: String = "",
    var ownerId: String = "",
    var industry: Industry = Industry.CAFE,
    var currencyCode: String = "EUR",
    var isActive: Boolean = true
)

data class CafeLocation(
    var id: String = "",
    var businessId: String = "",
    var name: String = "",
    var interiorStyle: InteriorStyle = InteriorStyle.CLASSIC,
    var tableCount: Int = 6,
    var seatsPerTable: Int = 4,
    var averageVisitMinutes: Int = 45,
    var targetSeatOccupancy: Double = 0.30,
    var takeawayShare: Double = 0.25,
    var openingHour: Int = 8,
    var closingHour: Int = 20,
    var monthlyRentCents: Long = 150_000L,
    var isOpen: Boolean = false
)

data class Employee(
    var id: String = "",
    var locationId: String = "",
    var name: String = "",
    var role: EmployeeRole = EmployeeRole.BARISTA,
    var monthlyGrossSalaryCents: Long = 180_000L,
    var weeklyWorkingHours: Double = 40.0,
    var contractType: EmploymentContractType = EmploymentContractType.PERMANENT,
    var payrollProfileId: String = "ES_GENERAL",
    var productivity: Double = 1.0,
    var isActive: Boolean = true
)

data class Equipment(
    var id: String = "",
    var locationId: String = "",
    var name: String = "",
    var purchaseCostCents: Long = 0L,
    var usefulLifeMonths: Int = 60,
    var conditionPercent: Int = 100,
    var capacityPerHour: Int = 0,
    var accumulatedDepreciationCents: Long = 0L,
    var residualValueCents: Long = 0L,
    var placedInServiceAt: GameTime = GameTime(),
    var isOperational: Boolean = true
)


data class StartingConfiguration(
    var businessName: String = "Maison Café",
    var countryCode: String = "ES",
    var currencyCode: String = "EUR",
    var taxProfileId: String = "ES_2026_PILOT",

    var startingLoanCents: Long = 5_000_000L,
    var annualInterestRate: Double = 0.06,
    var loanTermMonths: Int = 60,

    var employeeCount: Int = 3,
    var tableCount: Int = 6,
    var seatsPerTable: Int = 4,

    var equipmentBudgetCents: Long = 1_200_000L,
    var initialInventoryBudgetCents: Long = 300_000L,

    var targetCustomersPerDay: Int = 90,
    var operatingDaysPerMonth: Int = 26,
    var averageSpendPerCustomerCents: Long = 850L,

    var initialCashCents: Long = 0L
)

enum class EmploymentContractType {
    PERMANENT,
    TEMPORARY,
    PART_TIME
}
