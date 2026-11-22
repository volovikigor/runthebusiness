package com.example.runthebusiness.data.local

import android.content.Context
import com.example.runthebusiness.data.local.entity.CustomerOrderLineEntity
import com.example.runthebusiness.data.local.entity.StockMovementEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SaleDetail(
    val products: List<CustomerOrderLineEntity>,
    val stock: List<StockMovementEntity>
)

class CafeTransactionDetailRepository(context: Context) {
    private val sales = GameDatabase.getInstance(context.applicationContext).cafeSalesDao()

    suspend fun sale(orderId: String): SaleDetail = withContext(Dispatchers.IO) {
        SaleDetail(sales.getOrderLines(orderId), sales.getMovementsForSource(orderId))
    }

    suspend fun movements(limit: Int = 100): List<StockMovementEntity> =
        withContext(Dispatchers.IO) { sales.recentStockMovements(limit) }

    suspend fun purchase(batchId: String): List<StockMovementEntity> =
        withContext(Dispatchers.IO) { sales.getMovementsForSource(batchId) }
}
