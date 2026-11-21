package com.example.runthebusiness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.runthebusiness.data.local.entity.CustomerOrderEntity

@Dao
interface CafeSalesDao {
    @Insert suspend fun insertOrder(order: CustomerOrderEntity)

    @Query("SELECT * FROM customer_orders WHERE locationId = :locationId ORDER BY gameDay DESC, gameHour DESC, gameMinute DESC, id DESC")
    suspend fun getOrders(locationId: String): List<CustomerOrderEntity>

    @Query("SELECT * FROM customer_orders WHERE id = :orderId")
    suspend fun getOrder(orderId: String): CustomerOrderEntity?

    @Query("UPDATE inventory_batches SET quantityRemaining = :remaining WHERE id = :batchId AND quantityRemaining >= :minimumPrevious")
    suspend fun updateBatchRemaining(batchId: String, remaining: Double, minimumPrevious: Double): Int
}
