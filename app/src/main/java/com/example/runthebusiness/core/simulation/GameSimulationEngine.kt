package com.example.runthebusiness.core.simulation

import android.content.Context
import androidx.room.withTransaction
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
import com.example.runthebusiness.data.local.CafeAutoPurchasingRepository
import com.example.runthebusiness.data.local.CafePaymentConsequencesRepository
import com.example.runthebusiness.data.local.CafePaymentRegisterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.example.runthebusiness.engine.model.GameTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single entry point for advancing the playable simulation. */
class GameSimulationEngine(context: Context) {
    private val appContext = context.applicationContext
    private val db = GameDatabase.getInstance(appContext)
    private val clock = GameRepository(appContext)
    private val cafe = CafeServiceRepository(appContext)
    private val close = CafeDailyCloseRepository(appContext)
    private val spoilage = CafeSpoilageRepository(appContext)
    private val autoPurchasing = CafeAutoPurchasingRepository(appContext)
    private val bills = CafePaymentRegisterRepository(appContext)
    private val arrivals = CafeArrivalRepository(appContext)
    private val alerts = CafeManagementAlertsRepository(appContext)
    // Shared across UI screens so manual jumps and automatic ticks cannot interleave.
    private val mutex get() = simulationMutex

    companion object {
        private val simulationMutex = Mutex()
    }

    data class StepResult(val time: GameTime, val groupsServed: Int)

    suspend fun advance(minutes: Long, sessionId: String = "debug_session_001"): StepResult =
        mutex.withLock { advanceLocked(minutes, sessionId) }

    private suspend fun advanceLocked(minutes: Long, sessionId: String): StepResult {
        require(minutes in 1..1440) { "Advance must be between 1 and 1440 minutes" }
        var time = checkNotNull(clock.loadGameTime(sessionId))
        var remaining = minutes
        var served = 0
        while (remaining > 0) {
            currentCoroutineContext().ensureActive()
            val before = time
            val minuteOfDay = before.hour * 60L + before.minute
            val step = db.withTransaction {
                val location = clock.loadBusinessLocations(sessionId).singleOrNull()
                val opening = location?.openingHour?.times(60L) ?: 0L
                val closing = location?.closingHour?.times(60L) ?: 0L
                val untilEvent = when {
                    location?.isOpen != true || minuteOfDay >= closing -> 1440L - minuteOfDay
                    minuteOfDay < opening -> opening - minuteOfDay
                    else -> 15L - minuteOfDay % 15L
                }
                val next = clock.advanceGameTime(minOf(remaining, untilEvent), sessionId)
                if (next.day != before.day) close.closeDay(before.day, sessionId)
                spoilage.expire(next, sessionId)
                if (next.day != before.day) {
                    autoPurchasing.replenish(next.day, sessionId)
                    bills.settleAutomatic(sessionId)
                }
                // Keep the clock, arrivals, sales and midnight accounting in the same commit.
                StepResult(next, cafe.processPending(sessionId))
            }
            time = step.time
            served += step.groupsServed
            remaining -= time.totalMinutes - before.totalMinutes
        }
        return StepResult(time, served)
    }

    enum class JumpMode { NEXT_EVENT, NEXT_DECISION, NEXT_DAY, NEXT_WEEK }

    /**
     * Every jump uses the same 15-minute simulation path as the live clock.
     * No date teleportation: orders, inventory, journals and closes are processed.
     * Decision jumps stop at management alerts; calendar jumps always run to target.
     */
    suspend fun jump(mode: JumpMode, sessionId: String = "debug_session_001",
        onProgress: ((day: Int, daysCompleted: Int, groupsServed: Int) -> Unit)? = null
    ): NextEventResult = mutex.withLock {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val initial = checkNotNull(clock.loadGameTime(sessionId))
        val knownAlerts = if (mode == JumpMode.NEXT_WEEK || mode == JumpMode.NEXT_DAY)
            emptySet() else alerts.load(sessionId).map { it.id }.toSet()
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
        val calendarJump = mode == JumpMode.NEXT_DAY || mode == JumpMode.NEXT_WEEK
        val location = if (calendarJump) clock.loadBusinessLocations(sessionId).singleOrNull() else null
        repeat(steps) {
            val advanceStarted = android.os.SystemClock.elapsedRealtime()
            val minuteOfDay = latest.hour * 60L + latest.minute
            val absolute = (latest.day - 1L) * 1440L + minuteOfDay
            // Never skip midnight or the first possible arrival slot.
            // Keep the regular 15-minute path throughout trading hours.
            val idleUntil = when {
                location == null -> minuteOfDay
                !location.isOpen -> 1440L
                minuteOfDay < location.openingHour * 60L ->
                    (location.openingHour * 60L - 15L).coerceAtLeast(minuteOfDay)
                minuteOfDay >= location.closingHour * 60L -> 1440L
                else -> minuteOfDay
            }
            val duration = if (calendarJump)
                minOf(maxOf(15L, idleUntil - minuteOfDay), targetMinutes - absolute,
                    1440L - minuteOfDay)
            else 15L
            val step = advanceLocked(duration, sessionId)
            advanceMs += android.os.SystemClock.elapsedRealtime() - advanceStarted
            stepsRun++
            latest = step.time
            groups += step.groupsServed
            if (mode == JumpMode.NEXT_WEEK && latest.day != initial.day &&
                latest.hour == 0 && latest.minute == 0) {
                onProgress?.invoke(latest.day, latest.day - initial.day, groups)
            }
            // Management state changes on arrivals/sales, day boundaries and settlements.
            // During a calendar/decision jump, check on hourly boundaries; keep
            // NEXT_EVENT precise at every 15-minute interval.
            // Hourly checks can report a new stock alert up to 45 minutes late.
            val shouldCheckAlerts = mode == JumpMode.NEXT_EVENT ||
                (mode == JumpMode.NEXT_DECISION && latest.minute == 0)
            if (shouldCheckAlerts) {
                val alertStarted = android.os.SystemClock.elapsedRealtime()
                val newDecision = alerts.load(sessionId).firstOrNull { it.id !in knownAlerts }
                alertsMs += android.os.SystemClock.elapsedRealtime() - alertStarted
                alertChecks++
                if (newDecision != null) {
                    if (mode == JumpMode.NEXT_DECISION || mode == JumpMode.NEXT_EVENT) {
                        return@withLock result(latest, "Management alert · ${newDecision.title}", groups)
                    }
                }
            }
            if (mode == JumpMode.NEXT_EVENT) {
                val arrivalStarted = android.os.SystemClock.elapsedRealtime()
                val newArrival = arrivals.recent(sessionId).firstOrNull { it.id !in previousArrivalIds }
                arrivalMs += android.os.SystemClock.elapsedRealtime() - arrivalStarted
                if (newArrival != null) {
                    return@withLock result(latest, "Customer arrival · ${newArrival.guests} guests", groups)
                }
                previousArrivalIds = arrivals.recent(sessionId).map { it.id }.toSet()
                if (latest.day > initial.day) {
                    return@withLock result(latest, "Day ${initial.day} closed", groups)
                }
            }
            val absoluteMinutes = (latest.day - 1L) * 1440L + latest.hour * 60L + latest.minute
            if (absoluteMinutes >= targetMinutes) {
                return@withLock result(latest, "Calendar advance completed · ${groups} groups served", groups)
            }
        }
        result(latest,
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

    /** The current MVP supports one local save. Reset is an explicit UI action. */
    suspend fun startGame(reset: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (reset) {
                db.clearAllTables()
                autoPurchasing.reset()
                bills.reset()
                CafePaymentConsequencesRepository.invalidate()
            }
            db.withTransaction {
                clock.initializeOpeningGame()
                CafeMenuRepository(appContext).seedMenu()
                CafeInventorySetupRepository(appContext).initializeRealStock()
                CafeExpandedMenuRepository(appContext).install()
                if (reset) CafeKitchenRepository(appContext).activate()
            }
        }
    }

    suspend fun resumePending(sessionId: String = "debug_session_001") {
        mutex.withLock {
            val time = clock.loadGameTime(sessionId) ?: return@withLock
            db.withTransaction {
                // Recover pending writes from saves created before clock steps were atomic.
                spoilage.expire(time, sessionId)
                cafe.processPending(sessionId)
                val lastClosed = close.lastClosedDay(sessionId)
                for (day in (lastClosed + 1) until time.day) close.closeDay(day, sessionId)
            }
        }
    }
}
