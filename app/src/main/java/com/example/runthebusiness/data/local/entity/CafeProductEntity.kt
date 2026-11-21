package com.example.runthebusiness.data.local.entity

import androidx.room.*

@Entity(tableName = "cafe_products",
    foreignKeys = [ForeignKey(entity = BusinessLocationEntity::class,
        parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("locationId")])
data class CafeProductEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val name: String,
    val type: String,
    val sellingPriceCents: Long,
    val taxCategoryCode: String,
    val preparationMinutes: Int,
    val isAvailable: Boolean
)

@Entity(tableName = "cafe_recipe_items",
    primaryKeys = ["productId", "ingredientId"],
    foreignKeys = [
        ForeignKey(entity = CafeProductEntity::class, parentColumns = ["id"],
            childColumns = ["productId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"],
            childColumns = ["ingredientId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("ingredientId")])
data class CafeRecipeItemEntity(
    val productId: String,
    val ingredientId: String,
    val quantity: Double
)
