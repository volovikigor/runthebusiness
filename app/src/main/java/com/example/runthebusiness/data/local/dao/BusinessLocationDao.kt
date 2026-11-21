package com.example.runthebusiness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.runthebusiness.data.local.entity.BusinessLocationEntity

@Dao
interface BusinessLocationDao {
    @Insert
    suspend fun insertLocations(locations: List<BusinessLocationEntity>)

    @Query("SELECT * FROM locations WHERE businessId = :businessId ORDER BY id")
    suspend fun getLocations(businessId: String): List<BusinessLocationEntity>

    @Query("SELECT * FROM locations WHERE id = :locationId")
    suspend fun getLocation(locationId: String): BusinessLocationEntity?

    @Query("UPDATE locations SET name = :name, openingHour = :openingHour, closingHour = :closingHour, monthlyRentCents = :rentCents, isOpen = :isOpen WHERE id = :locationId")
    suspend fun updateSharedFields(locationId: String, name: String, openingHour: Int, closingHour: Int, rentCents: Long, isOpen: Boolean): Int

    @Query("SELECT COUNT(*) FROM locations WHERE businessId = :businessId")
    suspend fun countLocations(businessId: String): Int
}
