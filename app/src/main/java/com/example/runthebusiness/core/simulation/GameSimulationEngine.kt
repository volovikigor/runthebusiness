package com.example.runthebusiness.core.simulation

import android.content.Context
import com.example.runthebusiness.data.local.CafeServiceRepository
import com.example.runthebusiness.data.local.CafeDailyCloseRepository
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
            if (before != null && before.day > 1 && before.hour == 0) {
                close.closeDay(before.day - 1, sessionId)
            }
            val next = clock.advanceGameTime(minutes, sessionId)
            val served = cafe.processPending(sessionId)
            if (before != null && next.day > before.day) {
                for (day in before.day until next.day) close.closeDay(day, sessionId)
            }
            StepResult(next, served)
        }

    suspend fun resumePending(sessionId: String = "debug_session_001"): Int =
        mutex.withLock { cafe.processPending(sessionId) }
}
