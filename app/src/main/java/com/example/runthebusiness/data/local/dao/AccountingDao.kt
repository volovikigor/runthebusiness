
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

    @Query("UPDATE game_sessions SET currentDay = :day, currentHour = :hour, currentMinute = :minute, updatedAtMillis = :updatedAt WHERE id = :sessionId")
    suspend fun updateGameTime(sessionId: String, day: Int, hour: Int, minute: Int, updatedAt: Long): Int

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

    @Query("SELECT MAX(gameDay) FROM journal_entries WHERE sessionId = :sessionId AND id LIKE 'daily_close_%'")
    suspend fun latestCloseDayCandidate(sessionId: String): Int?

    @Query("SELECT id FROM journal_entries WHERE sessionId = :sessionId AND id LIKE :prefix")
    suspend fun entryIdsWithPrefix(sessionId: String, prefix: String): List<String>

    @Query("""
        SELECT MAX(e.gameDay) FROM journal_entries e
        INNER JOIN journal_lines l ON l.entryId = e.id
        WHERE e.sessionId = :sessionId AND e.id LIKE 'settlement_%'
          AND l.accountCode = :accountCode AND l.debitCents > 0
    """)
    suspend fun lastSettlementDay(sessionId: String, accountCode: String): Int?

    @Query("""
        SELECT COALESCE(SUM(l.debitCents - l.creditCents), 0)
        FROM journal_lines l INNER JOIN journal_entries e ON e.id = l.entryId
        WHERE e.sessionId = :sessionId AND l.accountCode = :accountCode
    """)
    suspend fun accountBalance(sessionId: String, accountCode: String): Long

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN e.gameDay < :dueDay THEN l.creditCents ELSE 0 END - l.debitCents), 0)
        FROM journal_lines l JOIN journal_entries e ON e.id = l.entryId
        WHERE e.sessionId = :sessionId AND l.accountCode = :accountCode
    """)
    suspend fun payableDue(sessionId: String, accountCode: String, dueDay: Int): Long

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
