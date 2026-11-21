package com.example.runthebusiness.data.local.dao

import androidx.room.*
import com.example.runthebusiness.data.local.entity.*

@Dao
interface CafeMenuDao {
    @Insert suspend fun insertProducts(products: List<CafeProductEntity>)
    @Insert suspend fun insertRecipes(recipes: List<CafeRecipeItemEntity>)

    @Query("SELECT * FROM cafe_products WHERE locationId = :locationId ORDER BY name")
    suspend fun getProducts(locationId: String): List<CafeProductEntity>

    @Query("SELECT * FROM cafe_recipe_items WHERE productId = :productId ORDER BY ingredientId")
    suspend fun getRecipe(productId: String): List<CafeRecipeItemEntity>

    @Query("DELETE FROM cafe_recipe_items WHERE productId = :productId")
    suspend fun deleteRecipe(productId: String)

    @Query("UPDATE cafe_products SET fulfillment = :fulfillment, finishedStockItemId = :stockItemId WHERE id = :productId")
    suspend fun setFulfillment(productId: String, fulfillment: String, stockItemId: String?): Int

    @Query("SELECT COUNT(*) FROM cafe_products WHERE locationId = :locationId")
    suspend fun countProducts(locationId: String): Int
}
