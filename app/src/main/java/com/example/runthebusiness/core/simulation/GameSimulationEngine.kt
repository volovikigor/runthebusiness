package com.example.runthebusiness.core.simulation

import android.content.Context
import com.example.runthebusiness.data.local.CafeServiceRepository
import com.example.runthebusiness.data.local.CafeDailyCloseRepository
import com.example.runthebusiness.data.local.CafeArrivalRepository
import com.example.runthebusiness.data.local.CafeManagementAlertsRepository
import com.example.runthebusiness.data.local.GameRepository
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
    private val clock = GameRepository(context.applicationContext)
    private val cafe = CafeServiceRepository(context.applicationContext)
    private val close = CafeDailyCloseRepository(context.applicationContext)
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
            // Recover any previously generated but unprocessed arrivals first.
            cafe.processPending(sessionId)
            val before = clock.loadGameTime(sessionId)
            if (before != null && before.day > 1) {
                // Recover all unposted prior days (e.g. interrupted close or old saves).
                val lastClosed = close.lastClosedDay(sessionId)
                for (day in (lastClosed + 1) until before.day) close.closeDay(day, sessionId)
            }
            val next = clock.advanceGameTime(minutes, sessionId)
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
        val initial = checkNotNull(clock.loadGameTime(sessionId))
        val knownAlerts = alerts.load(sessionId).map { it.id }.toSet()
        var previousArrivalIds = arrivals.recent(sessionId).map { it.id }.toSet()
        var latest = initial
        var groups = 0
        val steps = when (mode) {
            JumpMode.NEXT_EVENT -> 96
            JumpMode.NEXT_DECISION -> 96 * 14
            JumpMode.NEXT_DAY -> 96
            JumpMode.NEXT_WEEK -> 96 * 7
        }
        val targetMinutes = when (mode) {
            JumpMode.NEXT_DAY -> (initial.day.toLong() * 1440L)
            JumpMode.NEXT_WEEK -> ((initial.day + 6L) * 1440L)
            else -> Long.MAX_VALUE
        }
        repeat(steps) {
            val step = advance(15L, sessionId)
            latest = step.time
            groups += step.groupsServed
            val newDecision = alerts.load(sessionId).firstOrNull { it.id !in knownAlerts }
            if (newDecision != null) {
                val isCritical = newDecision.priority == 0
                if (mode == JumpMode.NEXT_DECISION || mode == JumpMode.NEXT_EVENT ||
                    (mode == JumpMode.NEXT_WEEK && isCritical)) {
                    return NextEventResult(latest, "Management alert · ${newDecision.title}", groups)
                }
            }
            if (mode == JumpMode.NEXT_EVENT) {
                val newArrival = arrivals.recent(sessionId).firstOrNull { it.id !in previousArrivalIds }
                if (newArrival != null) {
                    return NextEventResult(latest, "Customer arrival · ${newArrival.guests} guests", groups)
                }
                previousArrivalIds = arrivals.recent(sessionId).map { it.id }.toSet()
                if (latest.day > initial.day) {
                    return NextEventResult(latest, "Day ${initial.day} closed", groups)
                }
            }
            val absoluteMinutes = (latest.day - 1L) * 1440L + latest.hour * 60L + latest.minute
            if (absoluteMinutes >= targetMinutes) {
                return NextEventResult(latest, "Calendar advance completed · ${groups} groups served", groups)
            }
        }
        return NextEventResult(latest,
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

    suspend fun resumePending(sessionId: String = "debug_session_001"): Int =
        mutex.withLock { cafe.processPending(sessionId) }
}
