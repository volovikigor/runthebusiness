
package com.example.runthebusiness.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.runthebusiness.data.local.dao.AccountingDao
import com.example.runthebusiness.data.local.entity.*

@Database(
    entities = [
        GameSessionEntity::class,
        BusinessEventEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {

    abstract fun accountingDao(): AccountingDao

    companion object {

        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "runthebusiness.db"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
