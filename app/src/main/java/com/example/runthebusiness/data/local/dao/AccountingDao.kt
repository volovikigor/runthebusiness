
package com.example.runthebusiness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.runthebusiness.data.local.entity.*

@Dao
interface AccountingDao {

    @Insert
    suspend fun insertSession(
        session: GameSessionEntity
    )

    @Insert
    suspend fun insertBusinessEvent(
        event: BusinessEventEntity
    )

    @Insert
    suspend fun insertJournalEntry(
        entry: JournalEntryEntity
    )

    @Insert
    suspend fun insertJournalLines(
        lines: List<JournalLineEntity>
    )

    @Query("""
        SELECT * FROM game_sessions
        WHERE id = :sessionId
    """)
    suspend fun getSession(
        sessionId: String
    ): GameSessionEntity?

    @Query("""
        SELECT * FROM journal_entries
        WHERE sessionId = :sessionId
        ORDER BY gameDay, gameHour, gameMinute, id
    """)
    suspend fun getJournalEntries(
        sessionId: String
    ): List<JournalEntryEntity>

    @Query("""
        SELECT * FROM journal_lines
        WHERE entryId = :entryId
        ORDER BY lineNumber
    """)
    suspend fun getJournalLines(
        entryId: String
    ): List<JournalLineEntity>

    @Transaction
    suspend fun saveAccountingOperation(
        event: BusinessEventEntity,
        entry: JournalEntryEntity,
        lines: List<JournalLineEntity>
    ) {
        require(entry.sourceEventId == event.id)
        require(entry.sessionId == event.sessionId)
        require(entry.businessId == event.businessId)
        require(lines.size >= 2)

        require(lines.all {
            it.entryId == entry.id &&
                    it.debitCents >= 0 &&
                    it.creditCents >= 0 &&
                    (it.debitCents > 0L) !=
                    (it.creditCents > 0L)
        })

        require(
            lines.map { it.lineNumber }.distinct().size ==
                    lines.size
        )

        val debit = lines.fold(0L) { sum, line ->
            Math.addExact(sum, line.debitCents)
        }

        val credit = lines.fold(0L) { sum, line ->
            Math.addExact(sum, line.creditCents)
        }

        require(debit > 0L && debit == credit) {
            "Unbalanced journal entry"
        }

        insertBusinessEvent(event)
        insertJournalEntry(entry)
        insertJournalLines(lines)
    }
}
