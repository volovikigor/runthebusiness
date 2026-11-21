package com.example.runthebusiness.industries.cafe.usecase

import com.example.runthebusiness.engine.model.SimulationState
import com.example.runthebusiness.industries.cafe.CafeIndustryModule
import com.example.runthebusiness.industries.cafe.CafeStartingConfiguration

/** Domain-only operation; persistence is orchestrated by GameRepository. */
class CreateCafeUseCase {
    operator fun invoke(configuration: CafeStartingConfiguration): SimulationState {
        require(configuration.tableCount > 0) { "A café needs at least one table" }
        require(configuration.seatsPerTable > 0) { "Seats per table must be positive" }
        return CafeIndustryModule.createInitialState(configuration)
    }
}
