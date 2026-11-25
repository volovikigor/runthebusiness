package com.example.runthebusiness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import com.example.runthebusiness.data.local.CafeAuditExportRepository
import com.example.runthebusiness.data.local.CafeAutoPurchasingRepository
import com.example.runthebusiness.data.local.CafeDailyCloseRepository
import com.example.runthebusiness.data.local.GameDatabase
import com.example.runthebusiness.data.local.CafePaymentRegisterRepository
import com.example.runthebusiness.engine.model.GameTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthlyDiagnosticsTest {
    @Test
    fun freshCafeProducesThirtyCompletedDaysAndRetainsOpeningFinance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = GameSimulationEngine(context)
        engine.startGame(reset = true)
        CafeAutoPurchasingRepository(context).enableCoreIngredients()
        val bills = CafePaymentRegisterRepository(context)
        bills.snapshot().forEach { bills.setAutomatic(it.account, true) }
        bills.setAutomaticDeposits(true)

        val result = engine.jump(GameSimulationEngine.JumpMode.NEXT_MONTH)
        assertEquals(GameTime(31, 0, 0), result.time)
        assertTrue(result.groupsServed > 0)
        val session = "debug_session_001"
        val ledger = GameDatabase.getInstance(context).accountingDao()
        CafeDailyCloseRepository(context).closeDay(30)
        assertEquals(10_000L, ledger.accountBalance(session, "5700"))
        assertEquals(-10_000L, ledger.accountBalance(session, "2110"))
        assertEquals(-2_000_000L, ledger.accountBalance(session, "2100"))
        assertEquals(180_000L, ledger.accountBalance(session, "5400"))
        val report = CafeAuditExportRepository(context).saveDesktopReport(completedDays = 30).readText()
        val daily = report.substringAfter("\"TABLE\",\"daily_economics\"\n")
            .lineSequence().takeWhile { it.isNotBlank() }.toList()
        assertEquals(31, daily.size)
        assertTrue(daily[1].startsWith("\"debug_session_001\",\"1\","))
        assertTrue(daily.last().startsWith("\"debug_session_001\",\"30\","))
        assertTrue(report.contains("\"opening_loan\""))
        assertTrue(report.contains("\"TABLE\",\"loan_schedule_projected\""))
    }
}
