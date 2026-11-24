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
        assertEquals(3_500_000L, report.days.first().bankCents)
        val due = report.days.last()
        assertEquals(14, due.day)
        val bills = due.payments.associate { it.label to it.amountCents }
        assertEquals(mapOf("Rent" to 65_000L, "Payroll" to 234_000L), bills)
        assertEquals(3_201_000L, due.bankCents)
        val loan = report.installments.first()
        assertEquals(31, loan.dueDay)
        assertEquals(25_000L, loan.interestCents)
        assertEquals(96_664L, loan.totalCents)
        assertEquals(3_500_000L, GameDatabase.getInstance(context).accountingDao()
            .accountBalance("debug_session_001", "1100"))

        GameDatabase.getInstance(context).accountingDao()
            .updateGameTime("debug_session_001", 32, 8, 0, System.currentTimeMillis())
        val later = checkNotNull(CafeCashForecastRepository(context).load())
        assertEquals(61, later.installments.first().dueDay)
        assertTrue(later.days.none { day -> day.payments.any { it.label == "Loan installment" } })
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
        val cashOnHand = 3_480_000L
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
