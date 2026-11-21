package com.example.runthebusiness.core.business

/** Shared location identity; sector-specific capacity belongs to industry modules. */
data class BusinessLocation(
    val id: String,
    val businessId: String,
    val name: String,
    val openingHour: Int,
    val closingHour: Int,
    val monthlyRentCents: Long,
    val isOpen: Boolean
)
