package com.example.runthebusiness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.runthebusiness.data.local.entity.CustomerOrderEntity
import com.example.runthebusiness.data.local.entity.CustomerOrderLineEntity
import com.example.runthebusiness.data.local.entity.StockMovementEntity

data class ProductUnitsSold(val productId: String, val units: Long)

data class ProductSalesTotals(val units: Long, val grossCents: Long)

data class ProductSalesMix(val productId: String, val productName: String, val units: Long)

data class IngredientSaleCost(
    val ingredientId: String,
    val ingredientName: String,
    val unit: String,
    val unitCostCents: Double
)

@Dao
interface CafeSalesDao {
    @Insert suspend fun insertOrder(order: CustomerOrderEntity)

    @Insert suspend fun insertOrderLine(line: CustomerOrderLineEntity)
    @Insert suspend fun insertStockMovements(rows: List<StockMovementEntity>)

    @Query("SELECT * FROM customer_order_lines")
    suspend fun getAllOrderLines(): List<CustomerOrderLineEntity>

    @Query("SELECT * FROM customer_order_lines WHERE orderId = :orderId ORDER BY id")
    suspend fun getOrderLines(orderId: String): List<CustomerOrderLineEntity>

    @Query("SELECT * FROM customer_order_lines WHERE orderId IN (:orderIds)")
    suspend fun getOrderLinesForOrders(orderIds: List<String>): List<CustomerOrderLineEntity>

    @Query("""
        SELECT l.productId, SUM(l.quantity) AS units
        FROM customer_order_lines l JOIN customer_orders o ON o.id = l.orderId
        WHERE o.locationId = :locationId AND o.gameDay >= :fromDay AND o.gameDay < :today
        GROUP BY l.productId
    """)
    suspend fun unitsSold(locationId: String, fromDay: Int, today: Int): List<ProductUnitsSold>

    @Query("""
        SELECT COALESCE(SUM(l.quantity), 0) AS units,
            COALESCE(SUM(l.quantity * l.unitPriceCents), 0) AS grossCents
        FROM customer_order_lines l JOIN customer_orders o ON o.id = l.orderId
        WHERE o.locationId = :locationId AND l.productId = :productId
            AND o.gameDay BETWEEN :fromDay AND :throughDay
    """)
    suspend fun productSales(locationId: String, productId: String, fromDay: Int, throughDay: Int): ProductSalesTotals

    @Query("""
        SELECT l.productId, MAX(l.productName) AS productName, SUM(l.quantity) AS units
        FROM customer_order_lines l JOIN customer_orders o ON o.id = l.orderId
        WHERE o.locationId = :locationId AND o.gameDay BETWEEN :fromDay AND :throughDay
            AND (o.gameDay < :throughDay OR o.gameHour * 60 + o.gameMinute <= :throughMinute)
        GROUP BY l.productId
        ORDER BY units DESC, productName
    """)
    suspend fun productSalesMix(locationId: String, fromDay: Int, throughDay: Int, throughMinute: Int): List<ProductSalesMix>

    @Query("""
        SELECT m.inventoryItemId AS ingredientId, MAX(m.inventoryItemName) AS ingredientName, i.unit,
            SUM(m.quantityDelta * m.unitCostCents) / SUM(m.quantityDelta) AS unitCostCents
        FROM stock_movements m
        JOIN customer_orders o ON o.id = m.sourceId
        JOIN inventory_items i ON i.id = m.inventoryItemId
        WHERE m.sourceType = 'CUSTOMER_ORDER' AND m.quantityDelta < 0 AND o.locationId = :locationId
            AND o.gameDay BETWEEN :fromDay AND :throughDay
            AND (o.gameDay < :throughDay OR o.gameHour * 60 + o.gameMinute <= :throughMinute)
        GROUP BY m.inventoryItemId, i.unit
        ORDER BY ingredientName
    """)
    suspend fun ingredientSaleCosts(locationId: String, fromDay: Int, throughDay: Int, throughMinute: Int): List<IngredientSaleCost>

    @Query("SELECT * FROM stock_movements WHERE sourceId = :sourceId ORDER BY id")
    suspend fun getMovementsForSource(sourceId: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE gameDay >= :fromDay ORDER BY gameDay, gameHour, gameMinute, id")
    suspend fun movementsSince(fromDay: Int): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements ORDER BY gameDay DESC, gameHour DESC, gameMinute DESC, id DESC LIMIT :limit")
    suspend fun recentStockMovements(limit: Int = 100): List<StockMovementEntity>

    @Query("SELECT * FROM customer_orders WHERE locationId = :locationId ORDER BY gameDay DESC, gameHour DESC, gameMinute DESC, id DESC")
    suspend fun getOrders(locationId: String): List<CustomerOrderEntity>

    @Query("SELECT * FROM customer_orders WHERE id = :orderId")
    suspend fun getOrder(orderId: String): CustomerOrderEntity?

    @Query("UPDATE inventory_batches SET quantityRemaining = :remaining WHERE id = :batchId AND quantityRemaining = :expectedPrevious")
    suspend fun updateBatchRemaining(batchId: String, remaining: Double, expectedPrevious: Double): Int
}
