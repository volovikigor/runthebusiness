package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.runthebusiness.data.local.dao.CafeArrivalDao
import com.example.runthebusiness.data.local.dao.CafeMenuDao
import com.example.runthebusiness.data.local.dao.CafeSalesDao
import com.example.runthebusiness.data.local.dao.AccountingDao
import com.example.runthebusiness.data.local.dao.OperationsDao
import com.example.runthebusiness.data.local.dao.JournalViewDao
import com.example.runthebusiness.data.local.dao.BusinessLocationDao
import com.example.runthebusiness.data.local.dao.GameSetupDao
import com.example.runthebusiness.data.local.entity.*

@Database(
    entities = [
        GameSessionEntity::class, BusinessEventEntity::class,
        JournalEntryEntity::class, JournalLineEntity::class,
        BusinessEntity::class, LedgerAccountEntity::class, LoanEntity::class,
        CafeLocationEntity::class, EmployeeEntity::class, EquipmentEntity::class,
        InventoryItemEntity::class, InventoryBatchEntity::class,
        BusinessLocationEntity::class,
        CustomerOrderEntity::class, CustomerOrderLineEntity::class, StockMovementEntity::class,
        CafeProductEntity::class, CafeRecipeItemEntity::class,
        CafeArrivalEntity::class, CafePriceChangeEntity::class
    ],
    version = 14,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun cafeArrivalDao(): CafeArrivalDao
    abstract fun cafeMenuDao(): CafeMenuDao
    abstract fun cafeSalesDao(): CafeSalesDao
    abstract fun accountingDao(): AccountingDao
    abstract fun gameSetupDao(): GameSetupDao
    abstract fun operationsDao(): OperationsDao
    abstract fun businessLocationDao(): BusinessLocationDao
    abstract fun journalViewDao(): JournalViewDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `businesses` (
                    `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `ownerId` TEXT NOT NULL, `industry` TEXT NOT NULL,
                    `currencyCode` TEXT NOT NULL, `isActive` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `game_sessions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_businesses_sessionId` ON `businesses` (`sessionId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `ledger_accounts` (
                    `sessionId` TEXT NOT NULL, `businessId` TEXT NOT NULL,
                    `code` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL,
                    PRIMARY KEY(`sessionId`, `businessId`, `code`),
                    FOREIGN KEY(`sessionId`) REFERENCES `game_sessions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`businessId`) REFERENCES `businesses`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_accounts_businessId` ON `ledger_accounts` (`businessId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `loans` (
                    `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `businessId` TEXT NOT NULL,
                    `principalCents` INTEGER NOT NULL, `outstandingPrincipalCents` INTEGER NOT NULL,
                    `annualInterestRate` REAL NOT NULL, `termMonths` INTEGER NOT NULL,
                    `monthlyPaymentCents` INTEGER NOT NULL, `nextPaymentDay` INTEGER NOT NULL,
                    `nextPaymentHour` INTEGER NOT NULL, `nextPaymentMinute` INTEGER NOT NULL,
                    `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `game_sessions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`businessId`) REFERENCES `businesses`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_sessionId` ON `loans` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_businessId` ON `loans` (`businessId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `cafe_locations` (
                    `id` TEXT NOT NULL, `businessId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `interiorStyle` TEXT NOT NULL, `tableCount` INTEGER NOT NULL,
                    `seatsPerTable` INTEGER NOT NULL, `averageVisitMinutes` INTEGER NOT NULL,
                    `targetSeatOccupancy` REAL NOT NULL, `takeawayShare` REAL NOT NULL,
                    `openingHour` INTEGER NOT NULL, `closingHour` INTEGER NOT NULL,
                    `monthlyRentCents` INTEGER NOT NULL, `isOpen` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`businessId`) REFERENCES `businesses`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cafe_locations_businessId` ON `cafe_locations` (`businessId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `employees` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `role` TEXT NOT NULL, `monthlyGrossSalaryCents` INTEGER NOT NULL,
                    `weeklyWorkingHours` REAL NOT NULL, `contractType` TEXT NOT NULL,
                    `payrollProfileId` TEXT NOT NULL, `productivity` REAL NOT NULL,
                    `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `cafe_locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_employees_locationId` ON `employees` (`locationId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `equipment` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `purchaseCostCents` INTEGER NOT NULL, `usefulLifeMonths` INTEGER NOT NULL,
                    `conditionPercent` INTEGER NOT NULL, `capacityPerHour` INTEGER NOT NULL,
                    `accumulatedDepreciationCents` INTEGER NOT NULL, `residualValueCents` INTEGER NOT NULL,
                    `placedInServiceDay` INTEGER NOT NULL, `placedInServiceHour` INTEGER NOT NULL,
                    `placedInServiceMinute` INTEGER NOT NULL, `isOperational` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `cafe_locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipment_locationId` ON `equipment` (`locationId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `inventory_items` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `unit` TEXT NOT NULL, `reorderLevel` REAL NOT NULL,
                    `isPerishable` INTEGER NOT NULL, `shelfLifeDays` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `cafe_locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_locationId` ON `inventory_items` (`locationId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `inventory_batches` (
                    `id` TEXT NOT NULL, `inventoryItemId` TEXT NOT NULL,
                    `locationId` TEXT NOT NULL, `quantityReceived` REAL NOT NULL,
                    `quantityRemaining` REAL NOT NULL, `unitCostCents` INTEGER NOT NULL,
                    `receivedDay` INTEGER NOT NULL, `receivedHour` INTEGER NOT NULL,
                    `receivedMinute` INTEGER NOT NULL, `expiresDay` INTEGER,
                    `expiresHour` INTEGER, `expiresMinute` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`inventoryItemId`) REFERENCES `inventory_items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`locationId`) REFERENCES `cafe_locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_batches_inventoryItemId` ON `inventory_batches` (`inventoryItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_batches_locationId` ON `inventory_batches` (`locationId`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `locations` (
                    `id` TEXT NOT NULL, `businessId` TEXT NOT NULL,
                    `name` TEXT NOT NULL, `openingHour` INTEGER NOT NULL,
                    `closingHour` INTEGER NOT NULL, `monthlyRentCents` INTEGER NOT NULL,
                    `isOpen` INTEGER NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`businessId`) REFERENCES `businesses`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_locations_businessId` ON `locations` (`businessId`)")
                // Preserve existing v3 locations, including player-customized values.
                db.execSQL("""INSERT INTO `locations`
                    (`id`, `businessId`, `name`, `openingHour`, `closingHour`, `monthlyRentCents`, `isOpen`)
                    SELECT `id`, `businessId`, `name`, `openingHour`, `closingHour`, `monthlyRentCents`, `isOpen`
                    FROM `cafe_locations`""")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve all rows before rebuilding tables whose foreign keys change.
                // The operation runs in Room's migration transaction.
                db.execSQL("PRAGMA defer_foreign_keys = ON")
                val tables = listOf("employees", "equipment", "inventory_items", "inventory_batches", "cafe_locations")
                tables.forEach { table ->
                    db.execSQL("CREATE TEMP TABLE backup_$table AS SELECT * FROM `$table`")
                }
                // Drop dependent children before removing the old café parent.
                listOf("inventory_batches", "inventory_items", "equipment", "employees", "cafe_locations").forEach {
                    db.execSQL("DROP TABLE `$it`")
                }

                db.execSQL("""CREATE TABLE IF NOT EXISTS `cafe_locations` (
                    `id` TEXT NOT NULL, `interiorStyle` TEXT NOT NULL,
                    `tableCount` INTEGER NOT NULL, `seatsPerTable` INTEGER NOT NULL,
                    `averageVisitMinutes` INTEGER NOT NULL,
                    `targetSeatOccupancy` REAL NOT NULL, `takeawayShare` REAL NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`id`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("""INSERT INTO `cafe_locations`
                    (`id`,`interiorStyle`,`tableCount`,`seatsPerTable`,`averageVisitMinutes`,`targetSeatOccupancy`,`takeawayShare`)
                    SELECT `id`,`interiorStyle`,`tableCount`,`seatsPerTable`,`averageVisitMinutes`,`targetSeatOccupancy`,`takeawayShare`
                    FROM backup_cafe_locations""")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `employees` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `role` TEXT NOT NULL, `monthlyGrossSalaryCents` INTEGER NOT NULL,
                    `weeklyWorkingHours` REAL NOT NULL, `contractType` TEXT NOT NULL,
                    `payrollProfileId` TEXT NOT NULL, `productivity` REAL NOT NULL,
                    `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("INSERT INTO `employees` SELECT * FROM backup_employees")
                db.execSQL("CREATE INDEX `index_employees_locationId` ON `employees` (`locationId`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `equipment` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `purchaseCostCents` INTEGER NOT NULL, `usefulLifeMonths` INTEGER NOT NULL,
                    `conditionPercent` INTEGER NOT NULL, `capacityPerHour` INTEGER NOT NULL,
                    `accumulatedDepreciationCents` INTEGER NOT NULL, `residualValueCents` INTEGER NOT NULL,
                    `placedInServiceDay` INTEGER NOT NULL, `placedInServiceHour` INTEGER NOT NULL,
                    `placedInServiceMinute` INTEGER NOT NULL, `isOperational` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("INSERT INTO `equipment` SELECT * FROM backup_equipment")
                db.execSQL("CREATE INDEX `index_equipment_locationId` ON `equipment` (`locationId`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `inventory_items` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `unit` TEXT NOT NULL, `reorderLevel` REAL NOT NULL,
                    `isPerishable` INTEGER NOT NULL, `shelfLifeDays` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("INSERT INTO `inventory_items` SELECT * FROM backup_inventory_items")
                db.execSQL("CREATE INDEX `index_inventory_items_locationId` ON `inventory_items` (`locationId`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `inventory_batches` (
                    `id` TEXT NOT NULL, `inventoryItemId` TEXT NOT NULL,
                    `locationId` TEXT NOT NULL, `quantityReceived` REAL NOT NULL,
                    `quantityRemaining` REAL NOT NULL, `unitCostCents` INTEGER NOT NULL,
                    `receivedDay` INTEGER NOT NULL, `receivedHour` INTEGER NOT NULL,
                    `receivedMinute` INTEGER NOT NULL, `expiresDay` INTEGER,
                    `expiresHour` INTEGER, `expiresMinute` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`inventoryItemId`) REFERENCES `inventory_items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("INSERT INTO `inventory_batches` SELECT * FROM backup_inventory_batches")
                db.execSQL("CREATE INDEX `index_inventory_batches_inventoryItemId` ON `inventory_batches` (`inventoryItemId`)")
                db.execSQL("CREATE INDEX `index_inventory_batches_locationId` ON `inventory_batches` (`locationId`)")
                tables.forEach { db.execSQL("DROP TABLE backup_$it") }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `customer_orders` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL,
                    `gameDay` INTEGER NOT NULL, `gameHour` INTEGER NOT NULL,
                    `gameMinute` INTEGER NOT NULL, `grossCents` INTEGER NOT NULL,
                    `netCents` INTEGER NOT NULL, `vatCents` INTEGER NOT NULL,
                    `cogsCents` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL,
                    `status` TEXT NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_orders_locationId` ON `customer_orders` (`locationId`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `cafe_products` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL,
                    `name` TEXT NOT NULL, `type` TEXT NOT NULL,
                    `sellingPriceCents` INTEGER NOT NULL, `taxCategoryCode` TEXT NOT NULL,
                    `preparationMinutes` INTEGER NOT NULL, `isAvailable` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cafe_products_locationId` ON `cafe_products` (`locationId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `cafe_recipe_items` (
                    `productId` TEXT NOT NULL, `ingredientId` TEXT NOT NULL,
                    `quantity` REAL NOT NULL, PRIMARY KEY(`productId`, `ingredientId`),
                    FOREIGN KEY(`productId`) REFERENCES `cafe_products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`ingredientId`) REFERENCES `inventory_items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cafe_recipe_items_ingredientId` ON `cafe_recipe_items` (`ingredientId`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cafe_products` ADD COLUMN `fulfillment` TEXT NOT NULL DEFAULT 'PREPARED'")
                db.execSQL("ALTER TABLE `cafe_products` ADD COLUMN `finishedStockItemId` TEXT")
                // Existing croissant recipes consume a placeholder ingredient. Keep their
                // current behavior until the stock conversion is implemented in v9.
                // Do not silently relabel them RESALE while those recipes remain.
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `cafe_arrivals` (
                    `id` TEXT NOT NULL, `locationId` TEXT NOT NULL,
                    `gameDay` INTEGER NOT NULL, `gameHour` INTEGER NOT NULL,
                    `gameMinute` INTEGER NOT NULL, `guests` INTEGER NOT NULL,
                    `status` TEXT NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cafe_arrivals_locationId` ON `cafe_arrivals` (`locationId`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `customer_order_lines` (
                    `id` TEXT NOT NULL, `orderId` TEXT NOT NULL,
                    `productId` TEXT NOT NULL, `productName` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL, `unitPriceCents` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`orderId`) REFERENCES `customer_orders`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_order_lines_orderId` ON `customer_order_lines` (`orderId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `stock_movements` (
                    `id` TEXT NOT NULL, `inventoryItemId` TEXT NOT NULL,
                    `inventoryItemName` TEXT NOT NULL, `batchId` TEXT NOT NULL,
                    `sourceType` TEXT NOT NULL, `sourceId` TEXT NOT NULL,
                    `quantityDelta` REAL NOT NULL, `unitCostCents` INTEGER NOT NULL,
                    `gameDay` INTEGER NOT NULL, `gameHour` INTEGER NOT NULL,
                    `gameMinute` INTEGER NOT NULL, PRIMARY KEY(`id`)
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_inventoryItemId` ON `stock_movements` (`inventoryItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_batchId` ON `stock_movements` (`batchId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_sourceId` ON `stock_movements` (`sourceId`)")
                // Historical orders have no product identity and historical stock deltas
                // cannot be reconstructed reliably. Preserve them without invented lines.
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    INSERT OR IGNORE INTO ledger_accounts (sessionId, businessId, code, name, type)
                    SELECT sessionId, id, '2010', 'Supplier invoices payable', 'LIABILITY'
                    FROM businesses
                """.trimIndent())
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE employees ADD COLUMN activeNextDay INTEGER")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cafe_arrivals ADD COLUMN preferredProductId TEXT")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cafe_price_changes (
                        productId TEXT NOT NULL, gameDay INTEGER NOT NULL,
                        previousCents INTEGER NOT NULL, currentCents INTEGER NOT NULL,
                        PRIMARY KEY(productId, gameDay),
                        FOREIGN KEY(productId) REFERENCES cafe_products(id) ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        @Volatile private var INSTANCE: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, GameDatabase::class.java, "runthebusiness.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14).build().also { INSTANCE = it }
            }
    }
}
