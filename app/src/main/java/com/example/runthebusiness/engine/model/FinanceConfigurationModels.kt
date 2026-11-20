
package com.example.runthebusiness.engine.model

enum class OpenItemType {
    RECEIVABLE,
    PAYABLE
}

enum class OpenItemStatus {
    OPEN,
    PARTIALLY_SETTLED,
    SETTLED,
    OVERDUE
}

data class OpenItem(
    var id: String = "",
    var businessId: String = "",
    var counterpartyId: String = "",
    var type: OpenItemType = OpenItemType.PAYABLE,
    var accountCode: String = "",
    var sourceDocumentId: String = "",
    var originalAmountCents: Long = 0L,
    var outstandingAmountCents: Long = 0L,
    var createdAt: GameTime = GameTime(),
    var dueAt: GameTime = GameTime(),
    var status: OpenItemStatus = OpenItemStatus.OPEN
)

data class BankAccount(
    var id: String = "",
    var businessId: String = "",
    var name: String = "",
    var currencyCode: String = "EUR",
    var ledgerAccountCode: String = "1100",
    var isActive: Boolean = true
)

data class BankStatementLine(
    var id: String = "",
    var bankAccountId: String = "",
    var timestamp: GameTime = GameTime(),
    var description: String = "",
    var amountCents: Long = 0L,
    var externalReference: String = "",
    var journalEntryId: String = "",
    var isReconciled: Boolean = false
)

data class CardSettlement(
    var id: String = "",
    var businessId: String = "",
    var grossAmountCents: Long = 0L,
    var feeCents: Long = 0L,
    var expectedAt: GameTime = GameTime(),
    var settledAt: GameTime? = null,
    var receivableOpenItemId: String = "",
    var bankStatementLineId: String = "",
    var isSettled: Boolean = false
)
