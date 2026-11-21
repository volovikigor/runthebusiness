package com.example.runthebusiness.data.local.dao
import androidx.room.*
import com.example.runthebusiness.data.local.entity.*

@Dao
interface OperationsDao {
    @Insert suspend fun insertLocations(rows: List<CafeLocationEntity>)
    @Insert suspend fun insertEmployees(rows: List<EmployeeEntity>)
    @Insert suspend fun insertEquipment(rows: List<EquipmentEntity>)
    @Insert suspend fun insertInventoryItems(rows: List<InventoryItemEntity>)
    @Insert suspend fun insertInventoryBatches(rows: List<InventoryBatchEntity>)

    @Query("SELECT * FROM cafe_locations WHERE businessId = :businessId")
    suspend fun getLocations(businessId: String): List<CafeLocationEntity>
    @Query("SELECT * FROM employees WHERE locationId IN (SELECT id FROM cafe_locations WHERE businessId = :businessId)")
    suspend fun getEmployees(businessId: String): List<EmployeeEntity>
    @Query("SELECT * FROM equipment WHERE locationId IN (SELECT id FROM cafe_locations WHERE businessId = :businessId)")
    suspend fun getEquipment(businessId: String): List<EquipmentEntity>
    @Query("SELECT * FROM inventory_items WHERE locationId IN (SELECT id FROM cafe_locations WHERE businessId = :businessId)")
    suspend fun getInventoryItems(businessId: String): List<InventoryItemEntity>
    @Query("SELECT * FROM inventory_batches WHERE locationId IN (SELECT id FROM cafe_locations WHERE businessId = :businessId)")
    suspend fun getInventoryBatches(businessId: String): List<InventoryBatchEntity>
}
