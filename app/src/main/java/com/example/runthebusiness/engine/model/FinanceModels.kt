
package com.example.runthebusiness.engine.model

enum class AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}

enum class ObligationStatus {
    OPEN,
    PARTIALLY_PAID,
    PAID,
    OVERDUE
}

enum class CashFlowCategory {
    OPERATING,
    INVESTING,
    FINANCING
}

enum class CashDirection {
    INFLOW,
    OUTFLOW
}

data class LedgerAccount(
    var code: String = "",
    var name: String = "",
    var type: AccountType = AccountType.ASSET
)

data class JournalLine(
    var accountCode: String = "",
    var debitCents: Long = 0L,
    var creditCents: Long = 0L,
    var locationId: String = ""
)


data class JournalEntry(
    var id: String = "",
    var businessId: String = "",
    var timestamp: GameTime = GameTime(),
    var description: String = "",
    var lines: List<JournalLine> = emptyList(),

    var sourceEventId: String = "",
    var sourceObjectType: String = "",
    var sourceObjectId: String = ""
)


data class PaymentObligation(
    var id: String = "",
    var businessId: String = "",
    var counterpartyId: String = "",
    var description: String = "",
    var amountCents: Long = 0L,
    var paidCents: Long = 0L,
    var dueAt: GameTime = GameTime(),
    var status: ObligationStatus = ObligationStatus.OPEN
)

data class Loan(
    var id: String = "",
    var businessId: String = "",
    var principalCents: Long = 0L,
    var outstandingPrincipalCents: Long = 0L,
    var annualInterestRate: Double = 0.06,
    var termMonths: Int = 60,
    var monthlyPaymentCents: Long = 0L,
    var nextPaymentAt: GameTime = GameTime(),
    var isActive: Boolean = true
)

data class CashMovement(
    var id: String = "",
    var businessId: String = "",
    var locationId: String = "",
    var timestamp: GameTime = GameTime(),
    var amountCents: Long = 0L,
    var direction: CashDirection = CashDirection.OUTFLOW,
    var category: CashFlowCategory = CashFlowCategory.OPERATING,
    var journalEntryId: String = "",
    var description: String = ""
)
