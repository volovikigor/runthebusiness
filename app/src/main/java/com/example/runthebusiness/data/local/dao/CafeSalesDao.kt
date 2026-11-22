package com.example.runthebusiness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.runthebusiness.data.local.entity.CustomerOrderEntity
import com.example.runthebusiness.data.local.entity.CustomerOrderLineEntity
import com.example.runthebusiness.data.local.entity.StockMovementEntity

@Dao
interface CafeSalesDao {
    @Insert suspend fun insertOrder(order: CustomerOrderEntity)

    @Insert suspend fun insertOrderLine(line: CustomerOrderLineEntity)
    @Insert suspend fun insertStockMovements(rows: List<StockMovementEntity>)

    @Query("SELECT * FROM customer_order_lines WHERE orderId = :orderId ORDER BY id")
    suspend fun getOrderLines(orderId: String): List<CustomerOrderLineEntity>

    @Query("SELECT * FROM stock_movements WHERE sourceId = :sourceId ORDER BY id")
    suspend fun getMovementsForSource(sourceId: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements ORDER BY gameDay DESC, gameHour DESC, gameMinute DESC, id DESC LIMIT :limit")
    suspend fun recentStockMovements(limit: Int = 100): List<StockMovementEntity>

    @Query("SELECT * FROM customer_orders WHERE locationId = :locationId ORDER BY gameDay DESC, gameHour DESC, gameMinute DESC, id DESC")
    suspend fun getOrders(locationId: String): List<CustomerOrderEntity>

    @Query("SELECT * FROM customer_orders WHERE id = :orderId")
    suspend fun getOrder(orderId: String): CustomerOrderEntity?

    @Query("UPDATE inventory_batches SET quantityRemaining = :remaining WHERE id = :batchId AND quantityRemaining = :expectedPrevious")
    suspend fun updateBatchRemaining(batchId: String, remaining: Double, expectedPrevious: Double): Int
}
