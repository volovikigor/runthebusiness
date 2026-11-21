package com.example.runthebusiness

import com.example.runthebusiness.industries.cafe.CafeStartingConfiguration
import com.example.runthebusiness.industries.cafe.usecase.CreateCafeUseCase
import com.example.runthebusiness.industries.cafe.toBusinessLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedLocationMappingTest {
    @Test fun cafeProjectionPreservesSharedFields() {
        val state = CreateCafeUseCase()(CafeStartingConfiguration(tableCount = 9))
        val cafe = state.locations.single()
        val common = cafe.toBusinessLocation()
        assertEquals(cafe.id, common.id)
        assertEquals(cafe.businessId, common.businessId)
        assertEquals(cafe.name, common.name)
        assertEquals(cafe.openingHour, common.openingHour)
        assertEquals(cafe.closingHour, common.closingHour)
        assertEquals(cafe.monthlyRentCents, common.monthlyRentCents)
        assertEquals(cafe.isOpen, common.isOpen)
        assertEquals(9, cafe.tableCount)
    }
}
