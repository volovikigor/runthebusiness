package com.example.runthebusiness.data.local.dao

import androidx.room.*
import com.example.runthebusiness.data.local.entity.CafeArrivalEntity

@Dao
interface CafeArrivalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArrivals(rows: List<CafeArrivalEntity>): List<Long>

    @Query("SELECT * FROM cafe_arrivals WHERE locationId = :locationId ORDER BY gameDay DESC, gameHour DESC, gameMinute DESC LIMIT :limit")
    suspend fun recent(locationId: String, limit: Int = 20): List<CafeArrivalEntity>

    @Query("SELECT COUNT(*) FROM cafe_arrivals WHERE locationId = :locationId AND gameDay = :day")
    suspend fun countForDay(locationId: String, day: Int): Int
}
