package com.example.runthebusiness.core.simulation

import android.content.Context
import com.example.runthebusiness.data.local.CafeServiceRepository
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
    private val mutex = Mutex()

    data class StepResult(val time: GameTime, val groupsServed: Int)

    suspend fun advance(minutes: Long, sessionId: String = "debug_session_001"): StepResult =
        mutex.withLock {
            require(minutes in 1..1440) { "Advance must be between 1 and 1440 minutes" }
            // Recover any previously generated but unprocessed arrivals first.
            cafe.processPending(sessionId)
            val next = clock.advanceGameTime(minutes, sessionId)
            val served = cafe.processPending(sessionId)
            StepResult(next, served)
        }

    suspend fun resumePending(sessionId: String = "debug_session_001"): Int =
        mutex.withLock { cafe.processPending(sessionId) }
}
