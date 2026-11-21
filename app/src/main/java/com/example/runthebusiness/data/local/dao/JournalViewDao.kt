package com.example.runthebusiness.data.local.dao
import androidx.room.*
import com.example.runthebusiness.data.local.entity.*

data class JournalWithLines(
    @Embedded val entry: JournalEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val lines: List<JournalLineEntity>
)

@Dao
interface JournalViewDao {
    @Transaction
    @Query("SELECT * FROM journal_entries WHERE sessionId = :sessionId ORDER BY gameDay, gameHour, gameMinute, id")
    suspend fun getJournal(sessionId: String): List<JournalWithLines>
}
