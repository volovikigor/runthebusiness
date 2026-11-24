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

    @Query("SELECT c.* FROM cafe_locations c JOIN locations l ON c.id = l.id WHERE l.businessId = :businessId")
    suspend fun getLocations(businessId: String): List<CafeLocationEntity>
    @Query("SELECT * FROM employees WHERE locationId IN (SELECT id FROM locations WHERE businessId = :businessId)")
    suspend fun getEmployees(businessId: String): List<EmployeeEntity>
    @Query("UPDATE employees SET activeNextDay = :active WHERE id = :id")
    suspend fun planEmployeeStatus(id: String, active: Boolean?): Int

    @Query("""
        UPDATE employees SET isActive = activeNextDay, activeNextDay = NULL
        WHERE activeNextDay IS NOT NULL AND locationId IN (SELECT id FROM locations WHERE businessId = :businessId)
    """)
    suspend fun applyNextDayStaffing(businessId: String)

    @Query("""
        SELECT COALESCE(MAX(workload), 0) FROM (
            SELECT SUM(l.quantity * MAX(1, p.preparationMinutes)) AS workload
            FROM customer_orders o JOIN customer_order_lines l ON l.orderId = o.id
            JOIN cafe_products p ON p.id = l.productId
            WHERE o.locationId = :locationId AND o.gameDay = :day
            GROUP BY o.gameHour, o.gameMinute / 15
        )
    """)
    suspend fun peakStaffWorkload(locationId: String, day: Int): Int

    @Query("SELECT * FROM equipment WHERE locationId IN (SELECT id FROM locations WHERE businessId = :businessId)")
    suspend fun getEquipment(businessId: String): List<EquipmentEntity>
    @Query("SELECT * FROM inventory_items WHERE locationId IN (SELECT id FROM locations WHERE businessId = :businessId)")
    suspend fun getInventoryItems(businessId: String): List<InventoryItemEntity>
    @Query("SELECT * FROM inventory_batches WHERE locationId IN (SELECT id FROM locations WHERE businessId = :businessId)")
    suspend fun getInventoryBatches(businessId: String): List<InventoryBatchEntity>
    @Query("SELECT * FROM inventory_batches WHERE quantityRemaining > 0 AND locationId IN (SELECT id FROM locations WHERE businessId = :businessId)")
    suspend fun getActiveInventoryBatches(businessId: String): List<InventoryBatchEntity>
}
