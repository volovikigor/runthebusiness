package com.example.runthebusiness

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import com.example.runthebusiness.data.local.CafeArrivalRepository
import com.example.runthebusiness.data.local.CafeCashManagementRepository
import com.example.runthebusiness.data.local.CafePaymentRegisterRepository
import com.example.runthebusiness.data.local.CafeCashForecastRepository
import com.example.runthebusiness.data.local.CafeDailyPerformanceRepository
import com.example.runthebusiness.data.local.CafeDailyCloseRepository
import com.example.runthebusiness.data.local.CafeDashboardRepository
import com.example.runthebusiness.data.local.CafeExpandedMenuRepository
import com.example.runthebusiness.data.local.CafeKitchenRepository
import com.example.runthebusiness.data.local.CafeMenuReadinessRepository
import com.example.runthebusiness.data.local.CafePurchasingRepository
import com.example.runthebusiness.data.local.stockShortages
import com.example.runthebusiness.data.local.CafeSaleRepository
import com.example.runthebusiness.data.local.CafeStaffRepository
import com.example.runthebusiness.data.local.GameDatabase
import com.example.runthebusiness.data.local.GameRepository
import com.example.runthebusiness.data.local.entity.BusinessEventEntity
import com.example.runthebusiness.data.local.entity.JournalEntryEntity
import com.example.runthebusiness.data.local.entity.JournalLineEntity
import com.example.runthebusiness.engine.model.GameTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimulationLifecycleTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val activity = ActivityScenarioRule(MainActivity::class.java)
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() = clearSave()
        override fun after() = clearSave()
    }).around(activity)

    @Test
    fun bulkPurchaseReceivesAllPacksAndCreatesOneSupplierInvoice() {
        startCafe()
        runBlocking {
            val purchasing = CafePurchasingRepository(context)
            val item = CafePurchasingRepository.supplies.first { it.itemId == "stock_brownie_001" }
            val before = purchasing.stock()[item.itemId] ?: 0.0
            val total = purchasing.purchasePacks(item.itemId, 3, onCredit = true)
            assertEquals(item.costCents * 3, total)
            assertEquals(before + item.quantity * 3, purchasing.stock().getValue(item.itemId), 0.0)
            assertEquals(total, CafePaymentRegisterRepository(context).supplierBills().single().amountCents)
        }
    }

    @Test
    fun immediatePurchasesUseBankAndLeaveTheTillUntouched() = runBlocking {
        GameSimulationEngine(context).startGame()
        val sessionId = "debug_session_001"
        val db = GameDatabase.getInstance(context)
        val businessId = db.gameSetupDao().getBusinesses(sessionId).single().id
        val dao = db.accountingDao()
        val bankBefore = dao.accountBalance(sessionId, "1100")
        val stock = CafePurchasingRepository(context)
        val item = CafePurchasingRepository.supplies.first { it.itemId == "stock_brownie_001" }
        val initialBank = 3 * item.costCents
        val tillFunding = bankBefore - initialBank
        dao.saveAccountingOperation(
            BusinessEventEntity("test_till", sessionId, businessId, "TEST_TRANSFER", "TEST",
                "test_till", 1, 8, 0, "Fund test till"),
            JournalEntryEntity("test_till", sessionId, businessId, "test_till", "Fund test till", 1, 8, 0),
            listOf(
                JournalLineEntity("test_till_1", "test_till", 1, "1000", "", tillFunding, 0L),
                JournalLineEntity("test_till_2", "test_till", 2, "1100", "", 0L, tillFunding)
            )
        )

        stock.purchasePacks(item.itemId, 2, onCredit = false)
        assertEquals(item.costCents, dao.accountBalance(sessionId, "1100"))
        assertEquals(tillFunding, dao.accountBalance(sessionId, "1000"))

        val before = stock.stock()
        assertTrue(runCatching { stock.purchasePacks(item.itemId, 2, onCredit = false) }.isFailure)
        assertEquals(before, stock.stock())
        assertEquals(item.costCents, dao.accountBalance(sessionId, "1100"))
        assertEquals(tillFunding, dao.accountBalance(sessionId, "1000"))
    }

    @Test
    fun clockStartsAtEightAndStaysPausedOnNavigationAndRecreation() {
        startCafe()
        assertVisible("Day 1 · 08:00")
        val game = GameRepository(context)
        click("×5")
        waitUntil("Clock advance") {
            runBlocking { checkNotNull(game.loadGameTime()) } > GameTime(1, 8)
        }
        val afterFirstTick = runBlocking { game.loadGameTime() }

        click("×20")
        waitUntil("Clock advance") { runBlocking { game.loadGameTime() } != afterFirstTick }
        click("Pause")
        val paused = assertClockStaysStill()

        click("×20")
        click("Operations")
        assertVisible("Pricing")
        assertEquals(null, findNode("Skip ▾"))
        assertEquals(null, findNode("×20"))
        val onOperations = assertClockStaysStill()
        assertTrue(onOperations >= paused)
        click("Finance")
        assertVisible("P&L")
        assertEquals(null, findNode("Pause"))
        activity.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Café")
        assertEquals(onOperations, assertClockStaysStill())

        val scenario = activity.scenario
        click("×20")
        scenario.moveToState(Lifecycle.State.STARTED)
        val backgroundTime = assertClockStaysStill()
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(backgroundTime, assertClockStaysStill())

        scenario.recreate()
        assertVisible("Day ${backgroundTime.day} · %02d:%02d".format(backgroundTime.hour, backgroundTime.minute))
        assertEquals(backgroundTime, assertClockStaysStill())
    }

    @Test
    fun midnightClosesOnceAndTradingResumesAtOpening() = runBlocking {
        val simulation = GameSimulationEngine(context)
        simulation.startGame()
        val game = GameRepository(context)
        val close = CafeDailyCloseRepository(context)
        val dashboard = CafeDashboardRepository(context)
        val accounting = GameDatabase.getInstance(context).accountingDao()

        simulation.advance(60)
        val orders = CafeSaleRepository(context).loadDemoSales()
        assertTrue(orders.isNotEmpty())
        assertTrue(orders.any { it.cogsCents > 0 && it.netCents > 0 })
        val afterSales = checkNotNull(dashboard.snapshot())
        assertTrue(afterSales.stockRemaining < afterSales.stockOriginal)

        simulation.advance(480)
        assertEquals(GameTime(1, 17), game.loadGameTime())
        val shift = CafeDailyPerformanceRepository(context).recent().single()
        val dayOrders = CafeSaleRepository(context).loadDemoSales()
        assertTrue(shift.arrivingGroups > 20)
        assertEquals(dayOrders.size, shift.servedGroups)
        assertEquals(dayOrders.sumOf { it.netCents - it.cogsCents }, shift.grossMarginCents)
        assertTrue(!dashboard.snapshot()!!.isTrading)
        assertEquals(0, close.lastClosedDay())
        simulation.jump(GameSimulationEngine.JumpMode.NEXT_DAY)
        assertEquals(GameTime(2, 0), game.loadGameTime())
        val dayOne = accounting.entryIdsWithPrefix("debug_session_001", "daily_close_1_%")
        assertTrue(dayOne.any { "_rent_" in it })
        assertTrue(dayOne.any { "_salary_" in it })
        assertTrue(dayOne.any { "_depreciation_" in it })

        GameSimulationEngine(context).resumePending()
        close.closeDay(1)
        assertEquals(dayOne.toSet(), accounting.entryIdsWithPrefix("debug_session_001", "daily_close_1_%").toSet())
        assertEquals(1, close.lastClosedDay())

        simulation.skipClosedHours()
        assertEquals(GameTime(2, 8), game.loadGameTime())
        assertTrue(dashboard.snapshot()!!.isTrading)
        assertTrue(CafeArrivalRepository(context).recent().any { it.gameDay == 2 && it.gameHour == 8 })
        game.setCafeOpen(false)
        simulation.advance(525)
        assertEquals(GameTime(2, 16, 45), game.loadGameTime())
        game.setCafeOpen(true)
        simulation.advancePlayback()
        assertEquals(GameTime(3, 8), game.loadGameTime())
        assertEquals(2, close.lastClosedDay())
        game.setCafeOpen(false)
        assertEquals(GameTime(3, 8), simulation.jump(GameSimulationEngine.JumpMode.NEXT_OPENING).time)
        simulation.jump(GameSimulationEngine.JumpMode.NEXT_DAY)
        simulation.advance(480)
        assertEquals(3, close.lastClosedDay())
        assertTrue(!dashboard.snapshot()!!.isTrading)
        assertTrue(CafeArrivalRepository(context).recent().none { it.gameDay == 4 })
    }

    @Test
    fun cashForecastReadsRentPayrollAndLoanWithoutPostingPayments() = runBlocking {
        val simulation = GameSimulationEngine(context)
        simulation.startGame()
        GameRepository(context).setCafeOpen(false)
        repeat(6) { simulation.jump(GameSimulationEngine.JumpMode.NEXT_DAY) }

        val report = checkNotNull(CafeCashForecastRepository(context).load())
        assertEquals(6, report.historyDays)
        assertEquals(7, report.days.first().day)
        assertEquals(500_000L, report.days.first().bankCents)
        val due = report.days.last()
        assertEquals(14, due.day)
        val bills = due.payments.associate { it.label to it.amountCents }
        assertEquals(mapOf("Rent" to 65_000L, "Payroll" to 219_960L), bills)
        assertEquals(215_040L, due.bankCents)
        val loan = report.installments.first()
        assertEquals(31, loan.dueDay)
        assertEquals(10_000L, loan.interestCents)
        assertEquals(38_666L, loan.totalCents)
        assertEquals(500_000L, GameDatabase.getInstance(context).accountingDao()
            .accountBalance("debug_session_001", "1100"))

        GameDatabase.getInstance(context).accountingDao()
            .updateGameTime("debug_session_001", 32, 8, 0, System.currentTimeMillis())
        val later = checkNotNull(CafeCashForecastRepository(context).load())
        assertEquals(31, later.installments.first().dueDay)
        assertTrue(later.days.first().payments.any { it.label == "Loan installment" })
    }

    @Test
    fun starterLoanPaysPrincipalAndAccruedInterestExactlyOnce() = runBlocking {
        val simulation = GameSimulationEngine(context)
        simulation.startGame()
        val session = "debug_session_001"
        val db = GameDatabase.getInstance(context)
        val dao = db.accountingDao()
        val bank = dao.accountBalance(session, "1100")
        dao.updateGameTime(session, 30, 23, 45, System.currentTimeMillis())

        simulation.jump(GameSimulationEngine.JumpMode.NEXT_DAY)
        assertEquals(bank - 38_666L, dao.accountBalance(session, "1100"))
        assertEquals(-1_971_334L, dao.accountBalance(session, "2100"))
        assertEquals(0L, dao.accountBalance(session, "2110"))
        assertEquals(10_000L, dao.accountBalance(session, "5700"))
        assertEquals(61, db.gameSetupDao().getLoans(session).single().nextPaymentDay)
        val payments = com.example.runthebusiness.data.local.CafeLoanPaymentRepository(context)
        assertEquals(0L, payments.settleDue(automatic = false))
        assertEquals(bank - 38_666L, dao.accountBalance(session, "1100"))
    }

    @Test
    fun creditLineDrawsIntoBankAndRepaymentClearsInterestFirst() = runBlocking {
        val simulation = GameSimulationEngine(context)
        simulation.startGame()
        val session = "debug_session_001"
        val dao = GameDatabase.getInstance(context).accountingDao()
        val credit = com.example.runthebusiness.data.local.CafeCreditLineRepository(context)
        val bankBefore = dao.accountBalance(session, "1100")
        credit.draw(300_000L)
        assertEquals(bankBefore + 300_000L, dao.accountBalance(session, "1100"))
        assertEquals(200_000L, credit.position().availableCents)
        assertTrue(runCatching { credit.draw(200_001L) }.isFailure)

        dao.updateGameTime(session, 1, 23, 45, System.currentTimeMillis())
        simulation.jump(GameSimulationEngine.JumpMode.NEXT_DAY)
        assertEquals(100L, credit.position().interestCents)
        credit.repay(10_100L)
        assertEquals(290_000L, credit.position().principalCents)
        assertEquals(0L, credit.position().interestCents)
        assertEquals(bankBefore + 289_900L, dao.accountBalance(session, "1100"))
    }

    @Test
    fun automaticDepositCoversOnlyTheDueRentShortfall() = runBlocking {
        val simulation = GameSimulationEngine(context)
        simulation.startGame()
        GameRepository(context).setCafeOpen(false)
        val sessionId = "debug_session_001"
        val db = GameDatabase.getInstance(context)
        val businessId = db.gameSetupDao().getBusinesses(sessionId).single().id
        val dao = db.accountingDao()
        val cashOnHand = 480_000L
        dao.saveAccountingOperation(
            BusinessEventEntity("test_cash", sessionId, businessId, "TEST_TRANSFER", "TEST",
                "test_cash", 1, 8, 0, "Fund test till"),
            JournalEntryEntity("test_cash", sessionId, businessId, "test_cash", "Fund test till", 1, 8, 0),
            listOf(
                JournalLineEntity("test_cash_1", "test_cash", 1, "1000", "", cashOnHand, 0L),
                JournalLineEntity("test_cash_2", "test_cash", 2, "1100", "", 0L, cashOnHand)
            )
        )
        val register = CafePaymentRegisterRepository(context)
        val cash = CafeCashManagementRepository(context)
        register.reset()
        try {
            register.setAutomatic("2000", true)
            repeat(13) { simulation.jump(GameSimulationEngine.JumpMode.NEXT_DAY) }
            assertEquals(65_000L, register.snapshot().single { it.account == "2000" }.due)
            assertEquals(20_000L, cash.position().bankCents)

            register.setAutomaticDeposits(true)
            register.settleAutomatic()
            val paid = cash.position()
            assertEquals(0L, paid.bankCents)
            assertEquals(cashOnHand - 45_000L, paid.cashCents)
            assertEquals(0L, paid.rentPayableCents)
            register.settleAutomatic()
            assertEquals(paid, cash.position())

            cash.transferCashToBank(4_321L)
            val manuallyDeposited = cash.position()
            assertEquals(4_321L, manuallyDeposited.bankCents)
            assertEquals(paid.totalCashCents, manuallyDeposited.totalCashCents)
        } finally {
            register.reset()
        }
    }

    @Test
    fun buyingSharedIngredientRefreshesTheShortageAcrossRecipes() = runBlocking {
        GameSimulationEngine(context).startGame()
        CafeKitchenRepository(context).activate()
        val menu = CafeExpandedMenuRepository(context)
        val affected = setOf("ham_cheese_001", "caesar_salad_001")
        for (id in affected) menu.setEnabled(id, true)
        val readiness = CafeMenuReadinessRepository(context)

        val cheese = readiness.snapshot().stockShortages().single { it.supply.itemId == "kitchen_cheese" }
        assertEquals(affected, cheese.dishes.map { it.productId }.toSet())
        val paid = CafePurchasingRepository(context).purchase(cheese.supply.itemId)
        assertEquals(checkNotNull(cheese.supply.supplierPackCents), paid)

        val afterPurchase = readiness.snapshot()
        assertTrue(afterPurchase.stockShortages().none { it.supply.itemId == cheese.supply.itemId })
        assertEquals(affected, afterPurchase.filter { it.productId in affected && !it.ready }
            .map { it.productId }.toSet())
    }

    @Test
    fun supplierCreditPreservesShelfLifeAndBlocksRenewalUntilOverdueInvoiceIsPaid() = runBlocking {
        GameSimulationEngine(context).startGame()
        val id = "debug_session_001"
        val db = GameDatabase.getInstance(context)
        val dao = db.accountingDao()
        val purchasing = CafePurchasingRepository(context)
        val cash = CafeCashManagementRepository(context)
        val register = CafePaymentRegisterRepository(context)
        val bank = cash.position().bankCents

        purchasing.purchase("stock_milk_001", onCredit = true)
        purchasing.purchase("stock_beans_001", onCredit = true)
        val invoices = register.supplierBills()
        assertEquals(listOf(8, 15), invoices.map { it.dueDay })
        assertEquals(bank, cash.position().bankCents)
        assertEquals(-14_000L, dao.accountBalance(id, "2010"))
        val business = db.gameSetupDao().getBusinesses(id).single()
        val batches = db.operationsDao().getInventoryBatches(business.id).filter { it.id.startsWith("purchase_batch_") }
        assertEquals(8, batches.single { it.inventoryItemId == "stock_milk_001" }.expiresDay)
        assertEquals(91, batches.single { it.inventoryItemId == "stock_beans_001" }.expiresDay)
        val forecast = checkNotNull(CafeCashForecastRepository(context).load())
        assertEquals(10_000L, forecast.days.last().payments.single().amountCents)

        dao.updateGameTime(id, 9, 8, 0, System.currentTimeMillis())
        assertTrue(runCatching { purchasing.purchase("stock_milk_001", onCredit = true) }.isFailure)
        cash.paySupplier(invoices.first().id)
        assertTrue(runCatching { cash.paySupplier(invoices.first().id) }.isFailure)
        assertEquals(bank - 10_000L, cash.position().bankCents)
        assertEquals(listOf(invoices.last()), register.supplierBills())
        purchasing.purchase("stock_milk_001", onCredit = true)
        assertEquals(2, register.supplierBills().size)
    }

    @Test
    fun monthEndTaxesAccrueOnceAndSettleFromBank() = runBlocking {
        val simulation = GameSimulationEngine(context)
        simulation.startGame()
        val id = "debug_session_001"
        val db = GameDatabase.getInstance(context)
        val dao = db.accountingDao()
        val business = db.gameSetupDao().getBusinesses(id).single().id
        dao.updateGameTime(id, 30, 23, 45, System.currentTimeMillis())
        dao.saveAccountingOperation(
            BusinessEventEntity("tax_test_sale", id, business, "CUSTOMER_SALE", "TEST",
                "tax_test_sale", 30, 12, 0, "Test net sales and VAT"),
            JournalEntryEntity("tax_test_sale", id, business, "tax_test_sale", "Test sales", 30, 12, 0),
            listOf(
                JournalLineEntity("tax_test_1", "tax_test_sale", 1, "1100", "", 110_000L, 0L),
                JournalLineEntity("tax_test_2", "tax_test_sale", 2, "4000", "", 0L, 100_000L),
                JournalLineEntity("tax_test_3", "tax_test_sale", 3, "2200", "", 0L, 10_000L)
            )
        )
        simulation.advance(15)
        val register = CafePaymentRegisterRepository(context)
        val taxes = register.snapshot().filter { it.account in setOf("2200", "2400", "2600") }
            .associate { it.account to it.due }
        assertEquals(mapOf("2200" to 10_000L, "2400" to 6_480L, "2600" to 17_692L), taxes)
        assertEquals(-16_920L, dao.accountBalance(id, "2300"))
        CafeDailyCloseRepository(context).closeDay(30)
        assertEquals(-17_692L, dao.accountBalance(id, "2600"))
        val projected = checkNotNull(CafeCashForecastRepository(context).load()).days.first().payments
        assertTrue(projected.any { it.label == "Corporate income tax" && it.amountCents == 17_692L })

        val cash = CafeCashManagementRepository(context)
        val bank = cash.position().bankCents
        taxes.forEach { (account, due) -> cash.pay(account, amountCents = due) }
        assertEquals(bank - taxes.values.sum(), cash.position().bankCents)
        assertTrue(register.snapshot().filter { it.account in taxes.keys }.all { it.due == 0L })
        assertEquals(17_692L, dao.accountBalance(id, "5800"))
    }

    @Test
    fun staffingPlanUpdatesForecastBeforeApplyingAtMidnight() = runBlocking {
        val simulation = GameSimulationEngine(context)
        simulation.startGame()
        GameRepository(context).setCafeOpen(false)
        val id = "debug_session_001"
        val dao = GameDatabase.getInstance(context).accountingDao()
        dao.updateGameTime(id, 7, 23, 45, System.currentTimeMillis())
        val staff = CafeStaffRepository(context)
        staff.changePlannedSize(-1)

        val planned = checkNotNull(staff.snapshot())
        assertEquals(3, planned.currentCount)
        assertEquals(2, planned.plannedCount)
        assertEquals(45.0, planned.currentCapacity, 0.001)
        assertEquals(30.0, planned.plannedCapacity, 0.001)
        val forecast = checkNotNull(CafeCashForecastRepository(context).load())
        assertEquals(23_400L, forecast.staffCostTodayCents)
        assertEquals(15_600L, forecast.staffCostTomorrowCents)
        assertEquals(84_600L, forecast.days.last().payments.single { it.label == "Payroll" }.amountCents)

        simulation.advance(15)
        assertEquals(GameTime(8, 0), GameRepository(context).loadGameTime())
        assertEquals(-16_920L, dao.accountBalance(id, "2300"))
        assertEquals(2, checkNotNull(staff.snapshot()).currentCount)
        staff.changePlannedSize(1)
        assertEquals(3, checkNotNull(staff.snapshot()).plannedCount)
        simulation.jump(GameSimulationEngine.JumpMode.NEXT_DAY)
        assertEquals(-28_200L, dao.accountBalance(id, "2300"))
        assertEquals(3, checkNotNull(staff.snapshot()).currentCount)
        staff.changePlannedSize(1)
        val hire = checkNotNull(staff.snapshot())
        assertEquals(4, hire.plannedCount)
        assertEquals(31_200L, hire.plannedCostCents)
        simulation.jump(GameSimulationEngine.JumpMode.NEXT_DAY)
        assertEquals(4, checkNotNull(staff.snapshot()).currentCount)
    }

    @Test
    fun ledgerNavigationKeepsStaffInventoryAndFinanceAvailable() {
        startCafe()
        click("Operations")
        assertVisible("Pricing")
        click("Staff")
        assertVisible("Service capacity")
        click("Inventory")
        assertVisible("Expired batches are written off automatically.")
        assertEquals(null, findNode("Write off 1 g · Coffee beans"))
        click("Purchasing")
        assertVisible("Automatic replenishment")
        click("Finance")
        assertVisible("Available now")
        assertVisible("7-day cash forecast")
    }

    private fun clearSave() {
        GameDatabase.getInstance(context).clearAllTables()
    }

    private fun startCafe() {
        assertVisible("Start café")
        click("Start café")
        assertVisible("Day 1 · 08:00")
    }

    private fun assertVisible(label: String) = waitUntil(label) { findNode(label) != null }

    private fun click(label: String) {
        waitUntil("Clickable $label") {
            var node = findNode(label)
            while (node != null && !node.isClickable) node = node.parent
            node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findNode(label: String, root: AccessibilityNodeInfo? = automation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isVisibleToUser && root.text?.lineSequence()?.any { it == label } == true) return root
        for (index in 0 until root.childCount) {
            findNode(label, root.getChild(index))?.let { return it }
        }
        return null
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 45_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for $description")
    }

    private fun assertClockStaysStill(): GameTime {
        val game = GameRepository(context)
        // Allow cancellation of any tick already in progress, then outwait two ×20 ticks.
        SystemClock.sleep(1_000)
        val time = runBlocking { checkNotNull(game.loadGameTime()) }
        SystemClock.sleep(1_600)
        assertEquals(time, runBlocking { game.loadGameTime() })
        return time
    }
}
