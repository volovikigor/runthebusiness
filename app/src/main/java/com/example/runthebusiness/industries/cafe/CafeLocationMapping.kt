package com.example.runthebusiness.industries.cafe

import com.example.runthebusiness.core.business.BusinessLocation
import com.example.runthebusiness.engine.model.CafeLocation

/** Explicit projection from the café model to the shared location identity. */
fun CafeLocation.toBusinessLocation(): BusinessLocation = BusinessLocation(
    id = id,
    businessId = businessId,
    name = name,
    openingHour = openingHour,
    closingHour = closingHour,
    monthlyRentCents = monthlyRentCents,
    isOpen = isOpen
)
