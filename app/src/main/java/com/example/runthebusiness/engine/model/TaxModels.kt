
package com.example.runthebusiness.engine.model

enum class PaymentMethod {
    CASH,
    BANK_CARD,
    BANK_TRANSFER,
    CUSTOMER_CREDIT
}

enum class TaxType {
    VAT,
    EMPLOYEE_SOCIAL_SECURITY,
    EMPLOYER_SOCIAL_SECURITY,
    PAYROLL_WITHHOLDING,
    CORPORATE_INCOME_TAX
}

enum class DepreciationMethod {
    STRAIGHT_LINE
}

data class TaxRule(
    var id: String = "",
    var countryCode: String = "ES",
    var profileId: String = "ES_2026_PILOT",
    var taxType: TaxType = TaxType.VAT,
    var categoryCode: String = "",
    var rate: Double = 0.0,
    var validFromDay: Int = 1,
    var validUntilDay: Int = 0
)

data class TaxLiability(
    var id: String = "",
    var businessId: String = "",
    var taxType: TaxType = TaxType.VAT,
    var periodStart: GameTime = GameTime(),
    var periodEnd: GameTime = GameTime(),
    var dueAt: GameTime = GameTime(),
    var amountCents: Long = 0L,
    var paidCents: Long = 0L,
    var journalEntryId: String = ""
)

data class PayrollCalculation(
    var id: String = "",
    var employeeId: String = "",
    var periodStart: GameTime = GameTime(),
    var periodEnd: GameTime = GameTime(),
    var grossSalaryCents: Long = 0L,
    var employeeSocialSecurityCents: Long = 0L,
    var employerSocialSecurityCents: Long = 0L,
    var incomeTaxWithheldCents: Long = 0L,
    var netSalaryCents: Long = 0L,
    var journalEntryId: String = ""
)

data class DepreciationSchedule(
    var id: String = "",
    var equipmentId: String = "",
    var businessId: String = "",
    var method: DepreciationMethod =
        DepreciationMethod.STRAIGHT_LINE,
    var acquisitionCostCents: Long = 0L,
    var residualValueCents: Long = 0L,
    var usefulLifeMonths: Int = 60,
    var accumulatedDepreciationCents: Long = 0L,
    var nextPostingAt: GameTime = GameTime()
)

data class FinanceConfiguration(
    var countryCode: String = "ES",
    var currencyCode: String = "EUR",
    var taxProfileId: String = "ES_2026_PILOT",
    var chartOfAccountsId: String = "GAME_STANDARD"
)
