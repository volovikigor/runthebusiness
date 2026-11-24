package com.example.runthebusiness

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import com.example.runthebusiness.data.local.CafeArrivalRepository
import com.example.runthebusiness.data.local.CafeDailyPerformanceRepository
import com.example.runthebusiness.data.local.CafeDailyCloseRepository
import com.example.runthebusiness.data.local.CafeDashboardRepository
import com.example.runthebusiness.data.local.CafeSaleRepository
import com.example.runthebusiness.data.local.GameDatabase
import com.example.runthebusiness.data.local.GameRepository
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

        simulation.jump(GameSimulationEngine.JumpMode.NEXT_OPENING)
        assertEquals(GameTime(2, 8), game.loadGameTime())
        assertTrue(dashboard.snapshot()!!.isTrading)
        assertTrue(CafeArrivalRepository(context).recent().any { it.gameDay == 2 && it.gameHour == 8 })
        game.setCafeOpen(false)
        simulation.advance(540)
        assertEquals(GameTime(2, 17), game.loadGameTime())
        game.setCafeOpen(true)
        simulation.jump(GameSimulationEngine.JumpMode.NEXT_OPENING)
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
