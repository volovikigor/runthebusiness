package com.example.runthebusiness.engine

import com.example.runthebusiness.engine.model.SimulationState
import com.example.runthebusiness.engine.model.StartingConfiguration
import com.example.runthebusiness.industries.cafe.CafeStartingFactory

/** Compatibility facade for existing callers; café construction lives in its industry module. */
object InitialGameFactory {
    fun create(config: StartingConfiguration = StartingConfiguration()): SimulationState =
        CafeStartingFactory.create(config)
}
