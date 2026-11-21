package com.example.runthebusiness.industries

import com.example.runthebusiness.engine.model.Industry
import com.example.runthebusiness.engine.model.SimulationState

/** Industry modules create initial domain state; core accounting remains shared. */
interface IndustryModule<C : Any> {
    val industry: Industry
    fun createInitialState(configuration: C): SimulationState
}
