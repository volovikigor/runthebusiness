package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.runthebusiness.data.local.dao.AccountingDao
import com.example.runthebusiness.data.local.dao.GameSetupDao
import com.example.runthebusiness.data.local.entity.*

@Database(
    entities = [
        GameSessionEntity::class, BusinessEventEntity::class,
        JournalEntryEntity::class, JournalLineEntity::class,
        BusinessEntity::class, LedgerAccountEntity::class, LoanEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun accountingDao(): AccountingDao
    abstract fun gameSetupDao(): GameSetupDao

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

        @Volatile private var INSTANCE: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, GameDatabase::class.java, "runthebusiness.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
