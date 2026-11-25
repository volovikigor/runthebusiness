package com.example.runthebusiness.industries.cafe

import com.example.runthebusiness.core.business.BusinessStartingConfiguration
import com.example.runthebusiness.engine.model.StartingConfiguration

data class CafeStartingConfiguration(
    val business: BusinessStartingConfiguration = BusinessStartingConfiguration(startingLoanCents = 2_000_000L),
    val employeeCount: Int = 3,
    val tableCount: Int = 6,
    val seatsPerTable: Int = 4,
    val targetCustomersPerDay: Int = 90,
    val operatingDaysPerMonth: Int = 26,
    val averageSpendPerCustomerCents: Long = 850L
) {
    /** Transitional adapter: preserve the existing factory and saved game format. */
    internal fun toLegacy(): StartingConfiguration = StartingConfiguration(
        businessName = business.businessName,
        countryCode = business.countryCode,
        currencyCode = business.currencyCode,
        taxProfileId = business.taxProfileId,
        startingLoanCents = business.startingLoanCents,
        annualInterestRate = business.annualInterestRate,
        loanTermMonths = business.loanTermMonths,
        employeeCount = employeeCount,
        tableCount = tableCount,
        seatsPerTable = seatsPerTable,
        equipmentBudgetCents = business.equipmentBudgetCents,
        initialInventoryBudgetCents = business.initialInventoryBudgetCents,
        targetCustomersPerDay = targetCustomersPerDay,
        operatingDaysPerMonth = operatingDaysPerMonth,
        averageSpendPerCustomerCents = averageSpendPerCustomerCents,
        initialCashCents = business.initialCashCents
    )
}
