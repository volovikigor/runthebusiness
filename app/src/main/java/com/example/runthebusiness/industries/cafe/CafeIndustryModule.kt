package com.example.runthebusiness.industries.cafe

import com.example.runthebusiness.engine.model.Industry
import com.example.runthebusiness.engine.model.SimulationState
import com.example.runthebusiness.industries.IndustryModule

/** Café-specific composition root; other industries will implement IndustryModule. */
object CafeIndustryModule : IndustryModule<CafeStartingConfiguration> {
    override val industry: Industry = Industry.CAFE

    override fun createInitialState(configuration: CafeStartingConfiguration): SimulationState =
        CafeStartingFactory.create(configuration.toLegacy(), configuration.openingGrossSalaryCents)
}
