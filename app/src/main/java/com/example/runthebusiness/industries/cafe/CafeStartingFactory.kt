
package com.example.runthebusiness.industries.cafe

import com.example.runthebusiness.engine.accounting.AccountingEngine
import com.example.runthebusiness.engine.model.*

internal object CafeStartingFactory {

    private val accounting = AccountingEngine()

    fun create(
        config: StartingConfiguration = StartingConfiguration()
    ): SimulationState {

        require(config.startingLoanCents > 0L)
        require(config.equipmentBudgetCents >= 0L)
        require(config.initialInventoryBudgetCents >= 0L)
        require(config.employeeCount > 0)
        require(config.tableCount > 0)
        require(config.loanTermMonths > 0)
        require(config.annualInterestRate >= 0.0)

        val startingPurchases = Math.addExact(
            config.equipmentBudgetCents,
            config.initialInventoryBudgetCents
        )

        require(startingPurchases <= config.startingLoanCents) {
            "Starting purchases exceed available financing"
        }

        val businessId = "business_001"
        val locationId = "cafe_001"
        val startTime = GameTime(day = 1, hour = 8)

        val business = Business(
            id = businessId,
            name = config.businessName,
            industry = Industry.CAFE,
            currencyCode = config.currencyCode
        )

        val location = CafeLocation(
            id = locationId,
            businessId = businessId,
            name = config.businessName,
            tableCount = config.tableCount,
            seatsPerTable = config.seatsPerTable,
            openingHour = 8,
            closingHour = 17,
            isOpen = true,
            monthlyRentCents = 150_000L
        )

        val roles = listOf(
            EmployeeRole.BARISTA,
            EmployeeRole.COOK,
            EmployeeRole.WAITER
        )

        val employees = (1..config.employeeCount).map { index ->
            Employee(
                id = "employee_$index",
                locationId = locationId,
                name = "Employee $index",
                role = roles[(index - 1) % roles.size],
                monthlyGrossSalaryCents = 180_000L
            )
        }

        val equipment = Equipment(
            id = "equipment_001",
            locationId = locationId,
            name = "Café equipment and furniture",
            purchaseCostCents = config.equipmentBudgetCents,
            usefulLifeMonths = 48,
            residualValueCents = 0L,
            placedInServiceAt = startTime
        )

        val inventoryItem = InventoryItem(
            id = "inventory_001",
            locationId = locationId,
            name = "Opening inventory",
            unit = StockUnit.PIECE,
            reorderLevel = 0.0
        )

        val inventoryBatch = InventoryBatch(
            id = "batch_001",
            inventoryItemId = inventoryItem.id,
            locationId = locationId,
            quantityReceived = 1.0,
            quantityRemaining = 1.0,
            unitCostCents = config.initialInventoryBudgetCents,
            receivedAt = startTime
        )

        val loan = Loan(
            id = "loan_001",
            businessId = businessId,
            principalCents = config.startingLoanCents,
            outstandingPrincipalCents = config.startingLoanCents,
            annualInterestRate = config.annualInterestRate,
            termMonths = config.loanTermMonths,
            monthlyPaymentCents = calculateMonthlyPayment(
                config.startingLoanCents,
                config.annualInterestRate,
                config.loanTermMonths
            ),
            nextPaymentAt = GameTime(day = 31, hour = 9)
        )

        val accounts = listOf(
            LedgerAccount("1000", "Cash", AccountType.ASSET),
            LedgerAccount("1100", "Bank", AccountType.ASSET),
            LedgerAccount("1200", "Card receivables", AccountType.ASSET),
            LedgerAccount("1300", "Customer receivables", AccountType.ASSET),
            LedgerAccount("1400", "Inventory", AccountType.ASSET),
            LedgerAccount("1500", "Equipment", AccountType.ASSET),
            LedgerAccount("1590", "Accumulated depreciation", AccountType.ASSET),
            LedgerAccount("1600", "Input VAT", AccountType.ASSET),

            LedgerAccount("2000", "Accounts payable", AccountType.LIABILITY),
            LedgerAccount("2100", "Loan payable", AccountType.LIABILITY),
            LedgerAccount("2200", "Output VAT", AccountType.LIABILITY),
            LedgerAccount("2300", "Salaries payable", AccountType.LIABILITY),
            LedgerAccount("2400", "Social security payable", AccountType.LIABILITY),
            LedgerAccount("2500", "Payroll withholding payable", AccountType.LIABILITY),
            LedgerAccount("2600", "Corporate tax payable", AccountType.LIABILITY),

            LedgerAccount("3000", "Owner equity", AccountType.EQUITY),
            LedgerAccount("3100", "Retained earnings", AccountType.EQUITY),

            LedgerAccount("4000", "Sales revenue", AccountType.REVENUE),

            LedgerAccount("5000", "Cost of goods sold", AccountType.EXPENSE),
            LedgerAccount("5100", "Spoilage expense", AccountType.EXPENSE),
            LedgerAccount("5200", "Gross salaries", AccountType.EXPENSE),
            LedgerAccount("5300", "Employer social security", AccountType.EXPENSE),
            LedgerAccount("5400", "Rent expense", AccountType.EXPENSE),
            LedgerAccount("5500", "Utilities and other expenses", AccountType.EXPENSE),
            LedgerAccount("5600", "Depreciation expense", AccountType.EXPENSE),
            LedgerAccount("5700", "Interest expense", AccountType.EXPENSE),
            LedgerAccount("5800", "Corporate income tax expense", AccountType.EXPENSE),
            LedgerAccount("5900", "Bank fees", AccountType.EXPENSE)
        )

        var state = SimulationState(
            currentTime = startTime,
            businesses = listOf(business),
            locations = listOf(location),
            employees = employees,
            equipment = listOf(equipment),
            inventory = listOf(inventoryItem),
            inventoryBatches = listOf(inventoryBatch),
            ledgerAccounts = accounts,
            loans = listOf(loan),
            financeConfiguration = FinanceConfiguration(
                countryCode = config.countryCode,
                currencyCode = config.currencyCode,
                taxProfileId = config.taxProfileId
            )
        )

        // 1. Bank receives the loan.
        state = accounting.post(
            state,
            JournalEntry(
                id = "opening_loan",
                businessId = businessId,
                timestamp = startTime,
                description = "Initial bank loan",
                sourceEventId = "loan_001_disbursed",
                sourceObjectType = "LOAN",
                sourceObjectId = loan.id,
                lines = listOf(
                    JournalLine(
                        accountCode = "1100",
                        debitCents = config.startingLoanCents
                    ),
                    JournalLine(
                        accountCode = "2100",
                        creditCents = config.startingLoanCents
                    )
                )
            )
        )

        // 2. Equipment purchased by bank transfer.
        if (config.equipmentBudgetCents > 0L) {
            state = accounting.post(
                state,
                JournalEntry(
                    id = "opening_equipment",
                    businessId = businessId,
                    timestamp = startTime,
                    description = "Opening equipment purchase",
                    sourceEventId = "equipment_001_purchased",
                    sourceObjectType = "EQUIPMENT",
                    sourceObjectId = equipment.id,
                    lines = listOf(
                        JournalLine(
                            accountCode = "1500",
                            debitCents = config.equipmentBudgetCents
                        ),
                        JournalLine(
                            accountCode = "1100",
                            creditCents = config.equipmentBudgetCents
                        )
                    )
                )
            )
        }

        // 3. Initial inventory purchased by bank transfer.
        if (config.initialInventoryBudgetCents > 0L) {
            state = accounting.post(
                state,
                JournalEntry(
                    id = "opening_inventory",
                    businessId = businessId,
                    timestamp = startTime,
                    description = "Opening inventory purchase",
                    sourceEventId = "inventory_batch_001_purchased",
                    sourceObjectType = "INVENTORY_BATCH",
                    sourceObjectId = inventoryBatch.id,
                    lines = listOf(
                        JournalLine(
                            accountCode = "1400",
                            debitCents = config.initialInventoryBudgetCents
                        ),
                        JournalLine(
                            accountCode = "1100",
                            creditCents = config.initialInventoryBudgetCents
                        )
                    )
                )
            )
        }

        return state
    }

    private fun calculateMonthlyPayment(
        principalCents: Long,
        annualRate: Double,
        months: Int
    ): Long {
        val monthlyRate = annualRate / 12.0

        if (monthlyRate == 0.0) {
            return principalCents / months +
                    if (principalCents % months != 0L) 1L else 0L
        }

        val factor = Math.pow(1.0 + monthlyRate, months.toDouble())

        return Math.round(
            principalCents.toDouble() *
                    monthlyRate * factor / (factor - 1.0)
        )
    }
}
