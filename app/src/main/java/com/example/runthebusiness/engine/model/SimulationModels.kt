
package com.example.runthebusiness.engine.model

data class GameTime(
    var day: Int = 1,
    var hour: Int = 8,
    var minute: Int = 0
) : Comparable<GameTime> {

    val totalMinutes: Long
        get() = (day - 1L) * 1440L +
                hour * 60L + minute

    override fun compareTo(other: GameTime): Int {
        return totalMinutes.compareTo(other.totalMinutes)
    }
}

enum class EventType {
    CAFE_OPENED,
    CAFE_CLOSED,
    DELIVERY_ARRIVED,
    CUSTOMER_ARRIVED,
    ORDER_COMPLETED,
    STOCK_DEPLETED,
    STOCK_EXPIRED,
    STOCK_WRITTEN_OFF,
    EQUIPMENT_BROKEN,
    EQUIPMENT_REPAIRED,
    PAYMENT_DUE,
    SALARY_DUE,
    LOAN_PAYMENT_DUE,
    MONTH_ENDED,
    MANAGEMENT_DECISION,
    PAYROLL_ACCRUED,
    TAX_ACCRUED,
    TAX_PAYMENT_DUE,
    DEPRECIATION_POSTED,
    CARD_SETTLEMENT_RECEIVED,
}

enum class EventPriority {
    ROUTINE,
    IMPORTANT,
    DECISION_REQUIRED
}

enum class SimulationSpeed(val multiplier: Int) {
    PAUSED(0),
    NORMAL(1),
    FAST(20),
    VERY_FAST(100)
}

data class GameEvent(
    var id: String = "",
    var time: GameTime = GameTime(),
    var type: EventType = EventType.CAFE_OPENED,
    var priority: EventPriority = EventPriority.ROUTINE,
    var locationId: String = "",
    var description: String = "",
    var isProcessed: Boolean = false
)

data class SimulationState(
    var currentTime: GameTime = GameTime(),
    var speed: SimulationSpeed = SimulationSpeed.PAUSED,
    var businesses: List<Business> = emptyList(),
    var locations: List<CafeLocation> = emptyList(),
    var employees: List<Employee> = emptyList(),
    var equipment: List<Equipment> = emptyList(),
    var products: List<Product> = emptyList(),
    var inventory: List<InventoryItem> = emptyList(),
    var inventoryBatches: List<InventoryBatch> = emptyList(),
    var suppliers: List<Supplier> = emptyList(),
    var purchaseOrders: List<PurchaseOrder> = emptyList(),
    var customerOrders: List<CustomerOrder> = emptyList(),
    var stockWriteOffs: List<StockWriteOff> = emptyList(),
    var ledgerAccounts: List<LedgerAccount> = emptyList(),
    var journalEntries: List<JournalEntry> = emptyList(),
    var paymentObligations: List<PaymentObligation> = emptyList(),
    var loans: List<Loan> = emptyList(),
    var cashMovements: List<CashMovement> = emptyList(),
    var pendingEvents: List<GameEvent> = emptyList(),
    var financeConfiguration: FinanceConfiguration =
        FinanceConfiguration(),
    var taxRules: List<TaxRule> = emptyList(),
    var taxLiabilities: List<TaxLiability> = emptyList(),
    var payrollCalculations: List<PayrollCalculation> = emptyList(),
    var depreciationSchedules: List<DepreciationSchedule> = emptyList()
)
