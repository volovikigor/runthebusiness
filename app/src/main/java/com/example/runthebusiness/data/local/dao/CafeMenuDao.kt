package com.example.runthebusiness.data.local.dao

import androidx.room.*
import com.example.runthebusiness.data.local.entity.*

@Dao
interface CafeMenuDao {
    @Insert suspend fun insertProducts(products: List<CafeProductEntity>)
    @Insert suspend fun insertRecipes(recipes: List<CafeRecipeItemEntity>)

    @Query("SELECT * FROM cafe_products WHERE locationId = :locationId ORDER BY name")
    suspend fun getProducts(locationId: String): List<CafeProductEntity>

    @Query("SELECT * FROM cafe_products WHERE id = :productId AND locationId = :locationId")
    suspend fun getProduct(locationId: String, productId: String): CafeProductEntity?

    @Query("SELECT * FROM cafe_price_changes WHERE productId = :productId ORDER BY gameDay DESC LIMIT 2")
    suspend fun latestPriceChanges(productId: String): List<CafePriceChangeEntity>

    @Query("""
        SELECT c.* FROM cafe_price_changes c JOIN cafe_products p ON p.id = c.productId
        WHERE p.locationId = :locationId AND c.gameDay = (
            SELECT MAX(h.gameDay) FROM cafe_price_changes h WHERE h.productId = c.productId
        ) AND c.previousCents != c.currentCents
        ORDER BY c.gameDay DESC, c.rowid DESC
    """)
    suspend fun latestChangedProducts(locationId: String): List<CafePriceChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePriceChange(change: CafePriceChangeEntity)

    @Query("SELECT * FROM cafe_recipe_items WHERE productId = :productId ORDER BY ingredientId")
    suspend fun getRecipe(productId: String): List<CafeRecipeItemEntity>

    @Query("DELETE FROM cafe_recipe_items WHERE productId = :productId")
    suspend fun deleteRecipe(productId: String)

    @Query("UPDATE cafe_products SET fulfillment = :fulfillment, finishedStockItemId = :stockItemId WHERE id = :productId")
    suspend fun setFulfillment(productId: String, fulfillment: String, stockItemId: String?): Int

    @Query("UPDATE cafe_products SET sellingPriceCents = :priceCents WHERE id = :productId AND locationId = :locationId")
    suspend fun updatePrice(productId: String, locationId: String, priceCents: Long): Int

    @Query("UPDATE cafe_products SET isAvailable = :enabled WHERE id = :productId AND locationId = :locationId")
    suspend fun setAvailability(productId: String, locationId: String, enabled: Boolean): Int

    @Query("SELECT COUNT(*) FROM cafe_products WHERE locationId = :locationId")
    suspend fun countProducts(locationId: String): Int
}
