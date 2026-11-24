package com.example.runthebusiness

import com.example.runthebusiness.core.business.BusinessStartingConfiguration
import com.example.runthebusiness.engine.InitialGameFactory
import com.example.runthebusiness.engine.model.StartingConfiguration
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.industries.cafe.CafeStartingConfiguration
import com.example.runthebusiness.industries.cafe.toBusinessLocation
import com.example.runthebusiness.industries.cafe.usecase.CreateCafeUseCase
import org.junit.Assert.*
import org.junit.Test

class CafeArchitectureTest {
    @Test fun cafeConfigurationMapsCapacityAndFinancing() {
        val config = CafeStartingConfiguration(
            business = BusinessStartingConfiguration(startingLoanCents = 6_000_000L),
            tableCount = 8,
            seatsPerTable = 3
        )
        val state = CreateCafeUseCase()(config)
        assertEquals(8, state.locations.single().tableCount)
        assertEquals(3, state.locations.single().seatsPerTable)
        assertEquals(6_000_000L, state.loans.single().principalCents)
        assertEquals(6_000_000L, state.journalEntries.first().lines.first().debitCents)
    }

    @Test fun newCafeStartsAtEightOnDayOne() {
        val state = CreateCafeUseCase()(CafeStartingConfiguration())
        assertEquals(GameTime(1, 8, 0), state.currentTime)
        assertTrue(state.journalEntries.all { it.timestamp == state.currentTime })
    }

    @Test fun legacyFactoryRemainsCompatible() {
        val legacy = InitialGameFactory.create(StartingConfiguration(tableCount = 7))
        assertEquals(7, legacy.locations.single().tableCount)
        assertEquals(3, legacy.journalEntries.size)
    }

    @Test fun locationProjectionDoesNotIncludeCafeCapacity() {
        val state = CreateCafeUseCase()(CafeStartingConfiguration())
        val location = state.locations.single().toBusinessLocation()
        assertEquals(state.businesses.single().id, location.businessId)
        assertEquals(state.locations.single().monthlyRentCents, location.monthlyRentCents)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSeatingIsRejected() {
        CreateCafeUseCase()(CafeStartingConfiguration(seatsPerTable = 0))
    }
}
