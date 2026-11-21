package com.example.runthebusiness.core.business

/** Shared financing and jurisdiction settings. No industry-specific capacity fields. */
data class BusinessStartingConfiguration(
    val businessName: String = "Maison Café",
    val countryCode: String = "ES",
    val currencyCode: String = "EUR",
    val taxProfileId: String = "ES_2026_PILOT",
    val startingLoanCents: Long = 5_000_000L,
    val annualInterestRate: Double = 0.06,
    val loanTermMonths: Int = 60,
    val equipmentBudgetCents: Long = 1_200_000L,
    val initialInventoryBudgetCents: Long = 300_000L,
    val initialCashCents: Long = 0L
)
