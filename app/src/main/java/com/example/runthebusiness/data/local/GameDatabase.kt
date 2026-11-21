package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        BusinessLocationEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {
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

        @Volatile private var INSTANCE: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, GameDatabase::class.java, "runthebusiness.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}
