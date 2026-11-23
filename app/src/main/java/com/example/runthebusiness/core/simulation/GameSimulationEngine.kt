package com.example.runthebusiness.core.simulation

import android.content.Context
import com.example.runthebusiness.data.local.CafeServiceRepository
import com.example.runthebusiness.data.local.CafeDailyCloseRepository
import com.example.runthebusiness.data.local.CafeArrivalRepository
import com.example.runthebusiness.data.local.CafeManagementAlertsRepository
import com.example.runthebusiness.data.local.GameRepository
import com.example.runthebusiness.data.local.GameDatabase
import com.example.runthebusiness.data.local.CafeMenuRepository
import com.example.runthebusiness.data.local.CafeInventorySetupRepository
import com.example.runthebusiness.data.local.CafeExpandedMenuRepository
import com.example.runthebusiness.data.local.CafeKitchenRepository
import com.example.runthebusiness.data.local.CafeSpoilageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.runthebusiness.engine.model.GameTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry point for advancing the playable simulation.
 *
 * The clock/arrival write is atomic in GameRepository. Each individual sale
 * and its arrival status are atomic in CafeServiceRepository. These are NOT
 * one global transaction: pending arrivals are durable and retried on resume.
 */
class GameSimulationEngine(context: Context) {
    private val appContext = context.applicationContext
    private val clock = GameRepository(context.applicationContext)
    private val cafe = CafeServiceRepository(context.applicationContext)
    private val close = CafeDailyCloseRepository(context.applicationContext)
    private val spoilage = CafeSpoilageRepository(context.applicationContext)
    private val arrivals = CafeArrivalRepository(context.applicationContext)
    private val alerts = CafeManagementAlertsRepository(context.applicationContext)
    // Shared across UI screens so manual jumps and automatic ticks cannot interleave.
    private val mutex get() = simulationMutex

    companion object {
        private val simulationMutex = Mutex()
    }

    data class StepResult(val time: GameTime, val groupsServed: Int)

    suspend fun advance(minutes: Long, sessionId: String = "debug_session_001"): StepResult =
        mutex.withLock {
            require(minutes in 1..1440) { "Advance must be between 1 and 1440 minutes" }
            // Remove expired stock before fulfilling pending customer orders.
            val before = clock.loadGameTime(sessionId)
            if (before != null) spoilage.expire(before, sessionId)
            cafe.processPending(sessionId)
            if (before != null && before.day > 1) {
                // Recover all unposted prior days (e.g. interrupted close or old saves).
                val lastClosed = close.lastClosedDay(sessionId)
                for (day in (lastClosed + 1) until before.day) close.closeDay(day, sessionId)
            }
            val next = clock.advanceGameTime(minutes, sessionId)
            spoilage.expire(next, sessionId)
            val served = cafe.processPending(sessionId)
            if (before != null && next.day > before.day) {
                for (day in before.day until next.day) close.closeDay(day, sessionId)
            }
            StepResult(next, served)
        }

    enum class JumpMode { NEXT_EVENT, NEXT_DECISION, NEXT_DAY, NEXT_WEEK }

    /**
     * Every jump uses the same 15-minute simulation path as the live clock.
     * No date teleportation: orders, inventory, journals and closes are processed.
     * Decision jumps skip routine arrivals; calendar jumps stop on new critical alerts.
     */
    suspend fun jump(mode: JumpMode, sessionId: String = "debug_session_001"): NextEventResult {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val initial = checkNotNull(clock.loadGameTime(sessionId))
        val knownAlerts = alerts.load(sessionId).map { it.id }.toSet()
        var previousArrivalIds = arrivals.recent(sessionId).map { it.id }.toSet()
        var latest = initial
        var groups = 0
        var advanceMs = 0L
        var alertsMs = 0L
        var alertChecks = 0
        var arrivalMs = 0L
        var stepsRun = 0
        fun result(time: GameTime, message: String, served: Int): NextEventResult {
            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            android.util.Log.i("CafeSimulation", "jump=$mode elapsedMs=$elapsed advanceMs=$advanceMs alertsMs=$alertsMs alertChecks=$alertChecks arrivalMs=$arrivalMs steps=$stepsRun groups=$served day=${time.day}")
            return NextEventResult(time, message, served)
        }
        val steps = when (mode) {
            JumpMode.NEXT_EVENT -> 96
            JumpMode.NEXT_DECISION -> 96 * 14
            JumpMode.NEXT_DAY -> 96
            JumpMode.NEXT_WEEK -> 96 * 7
        }
        val startMinutes = (initial.day - 1L) * 1440L + initial.hour * 60L + initial.minute
        val targetMinutes = when (mode) {
            JumpMode.NEXT_DAY -> initial.day.toLong() * 1440L
            JumpMode.NEXT_WEEK -> startMinutes + 7L * 1440L
            else -> Long.MAX_VALUE
        }
        repeat(steps) {
            val advanceStarted = android.os.SystemClock.elapsedRealtime()
            val step = advance(15L, sessionId)
            advanceMs += android.os.SystemClock.elapsedRealtime() - advanceStarted
            stepsRun++
            latest = step.time
            groups += step.groupsServed
            // Management state changes on arrivals/sales, day boundaries and settlements.
            // During a calendar/decision jump, check on hourly boundaries; keep
            // NEXT_EVENT precise at every 15-minute interval.
            // Hourly checks can report a new stock alert up to 45 minutes late.
            val shouldCheckAlerts = mode == JumpMode.NEXT_EVENT || latest.minute == 0
            if (shouldCheckAlerts) {
                val alertStarted = android.os.SystemClock.elapsedRealtime()
                val newDecision = alerts.load(sessionId).firstOrNull { it.id !in knownAlerts }
                alertsMs += android.os.SystemClock.elapsedRealtime() - alertStarted
                alertChecks++
                if (newDecision != null) {
                    val isCritical = newDecision.priority == 0
                    if (mode == JumpMode.NEXT_DECISION || mode == JumpMode.NEXT_EVENT ||
                        (mode == JumpMode.NEXT_WEEK && isCritical)) {
                        return result(latest, "Management alert · ${newDecision.title}", groups)
                    }
                }
            }
            if (mode == JumpMode.NEXT_EVENT) {
                val arrivalStarted = android.os.SystemClock.elapsedRealtime()
                val newArrival = arrivals.recent(sessionId).firstOrNull { it.id !in previousArrivalIds }
                arrivalMs += android.os.SystemClock.elapsedRealtime() - arrivalStarted
                if (newArrival != null) {
                    return result(latest, "Customer arrival · ${newArrival.guests} guests", groups)
                }
                previousArrivalIds = arrivals.recent(sessionId).map { it.id }.toSet()
                if (latest.day > initial.day) {
                    return result(latest, "Day ${initial.day} closed", groups)
                }
            }
            val absoluteMinutes = (latest.day - 1L) * 1440L + latest.hour * 60L + latest.minute
            if (absoluteMinutes >= targetMinutes) {
                return result(latest, "Calendar advance completed · ${groups} groups served", groups)
            }
        }
        return result(latest,
            if (mode == JumpMode.NEXT_DECISION) "No new management decisions within 14 days"
            else "Time advance limit reached", groups)
    }

    suspend fun advanceToNextEvent(sessionId: String = "debug_session_001"): NextEventResult =
        jump(JumpMode.NEXT_EVENT, sessionId)

    data class NextEventResult(
        val time: GameTime,
        val description: String,
        val groupsServed: Int
    )

    /**
     * Explicit destructive new-game action. Serialized with clock ticks and jumps.
     * The current MVP supports a single local save only.
     */
    suspend fun startFreshGame() = mutex.withLock {
        withContext(Dispatchers.IO) {
            GameDatabase.getInstance(appContext).clearAllTables()
            GameRepository(appContext).initializeOpeningGame()
            CafeMenuRepository(appContext).seedMenu()
            CafeInventorySetupRepository(appContext).initializeRealStock()
            CafeExpandedMenuRepository(appContext).install()
            CafeKitchenRepository(appContext).activate()
        }
    }

    suspend fun resumePending(sessionId: String = "debug_session_001"): Int =
        mutex.withLock {
            clock.loadGameTime(sessionId)?.let { spoilage.expire(it, sessionId) }
            cafe.processPending(sessionId)
        }
}
