package com.example.runthebusiness.data.local.dao

import androidx.room.*
import com.example.runthebusiness.data.local.entity.*

@Dao
interface GameSetupDao {
    @Insert suspend fun insertBusiness(business: BusinessEntity)
    @Insert suspend fun insertAccounts(accounts: List<LedgerAccountEntity>)
    @Insert suspend fun insertLoans(loans: List<LoanEntity>)

    @Query("SELECT * FROM businesses WHERE sessionId = :sessionId")
    suspend fun getBusinesses(sessionId: String): List<BusinessEntity>

    @Query("SELECT * FROM ledger_accounts WHERE sessionId = :sessionId ORDER BY code")
    suspend fun getAccounts(sessionId: String): List<LedgerAccountEntity>

    @Query("SELECT * FROM loans WHERE sessionId = :sessionId")
    suspend fun getLoans(sessionId: String): List<LoanEntity>
}
