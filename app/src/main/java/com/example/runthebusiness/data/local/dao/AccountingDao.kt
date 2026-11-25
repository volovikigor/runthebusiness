
package com.example.runthebusiness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.example.runthebusiness.data.local.entity.*
import com.example.runthebusiness.data.local.SupplierBill

data class CafeDailyNetProfit(val day: Int, val cents: Long)

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

    @Query("SELECT * FROM game_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: String): Flow<GameSessionEntity?>

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

    @Query("""
        SELECT * FROM business_events WHERE sessionId = :sessionId
          AND eventType IN ('INVENTORY_PURCHASE', 'SUPPLIER_INVOICE')
        ORDER BY gameDay DESC, gameHour DESC, gameMinute DESC, id DESC LIMIT 1
    """)
    suspend fun latestSupplierDelivery(sessionId: String): BusinessEventEntity?

    @Query("""
        SELECT * FROM business_events WHERE sessionId = :sessionId
          AND eventType = 'PAYABLE_SETTLEMENT' AND sourceObjectType = 'AUTOMATIC_LEDGER_ACCOUNT'
        ORDER BY gameDay DESC, gameHour DESC, gameMinute DESC, id DESC LIMIT 1
    """)
    suspend fun latestAutomaticPayment(sessionId: String): BusinessEventEntity?

    @Query("""
        SELECT j.gameDay AS day, SUM(l.creditCents - l.debitCents) AS cents
        FROM journal_entries j JOIN journal_lines l ON l.entryId = j.id
        JOIN ledger_accounts a ON a.sessionId = j.sessionId AND a.businessId = j.businessId AND a.code = l.accountCode
        WHERE j.sessionId = :sessionId AND j.gameDay < :beforeDay AND a.type IN ('REVENUE', 'EXPENSE')
        GROUP BY j.gameDay
    """)
    suspend fun dailyNetProfit(sessionId: String, beforeDay: Int): List<CafeDailyNetProfit>

    @Query("""
        SELECT COALESCE(SUM(l.debitCents - l.creditCents), 0)
        FROM journal_lines l JOIN journal_entries j ON j.id = l.entryId
        WHERE j.sessionId = :sessionId AND l.accountCode = :account AND j.gameDay <= :throughDay
    """)
    suspend fun accountBalanceThroughDay(sessionId: String, account: String, throughDay: Int): Long

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
        SELECT COALESCE(SUM(CASE WHEN e.gameDay < :dueDay THEN l.creditCents - l.debitCents
            WHEN b.eventType = 'PAYABLE_SETTLEMENT' THEN -l.debitCents ELSE 0 END), 0)
        FROM journal_lines l JOIN journal_entries e ON e.id = l.entryId
        LEFT JOIN business_events b ON b.id = e.sourceEventId
        WHERE e.sessionId = :sessionId AND l.accountCode = :accountCode
    """)
    suspend fun payableDue(sessionId: String, accountCode: String, dueDay: Int): Long

    @Query("""
        SELECT COALESCE(SUM(l.debitCents - l.creditCents), 0)
        FROM journal_lines l JOIN journal_entries j ON j.id = l.entryId
        JOIN business_events e ON e.id = j.sourceEventId
        WHERE j.sessionId = :sessionId AND l.accountCode = :account
          AND j.gameDay >= :fromDay AND j.gameDay < :beforeDay
          AND (e.eventType = 'INVENTORY_PURCHASE' OR (:includeSales AND e.eventType = 'CUSTOMER_SALE'))
    """)
    suspend fun tradingCashMovement(
        sessionId: String, account: String, fromDay: Int, beforeDay: Int, includeSales: Boolean
    ): Long

    @Query("""
        SELECT e.id, e.description AS label,
          e.gameDay + CASE e.sourceObjectType WHEN 'PERISHABLE_INVOICE' THEN 7 ELSE 14 END AS dueDay,
          l.creditCents AS amountCents
        FROM business_events e JOIN journal_entries j ON j.sourceEventId = e.id
        JOIN journal_lines l ON l.entryId = j.id
        WHERE e.sessionId = :sessionId AND e.eventType = 'SUPPLIER_INVOICE'
          AND l.accountCode = '2010'
          AND NOT EXISTS (
            SELECT 1 FROM business_events p WHERE p.sessionId = e.sessionId
              AND p.eventType = 'SUPPLIER_SETTLEMENT' AND p.sourceObjectId = e.id
          )
        ORDER BY dueDay, e.id
    """)
    suspend fun supplierBills(sessionId: String): List<SupplierBill>

    @Query("""
        SELECT COALESCE(SUM(l.creditCents - l.debitCents), 0)
        FROM journal_lines l JOIN journal_entries j ON j.id = l.entryId
        JOIN ledger_accounts a ON a.sessionId = j.sessionId AND a.businessId = j.businessId
          AND a.code = l.accountCode
        WHERE j.sessionId = :sessionId AND j.gameDay BETWEEN :fromDay AND :throughDay
          AND a.type IN ('REVENUE', 'EXPENSE') AND a.code != '5800'
    """)
    suspend fun periodProfitBeforeTax(sessionId: String, fromDay: Int, throughDay: Int): Long

    @Query("""
        SELECT COALESCE(SUM(l.creditCents), 0)
        FROM journal_lines l JOIN journal_entries j ON j.id = l.entryId
        JOIN business_events e ON e.id = j.sourceEventId
        WHERE j.sessionId = :sessionId AND l.accountCode = '2200'
          AND j.gameDay >= :fromDay AND j.gameDay < :beforeDay AND e.eventType = 'CUSTOMER_SALE'
    """)
    suspend fun vatFromSales(sessionId: String, fromDay: Int, beforeDay: Int): Long

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
