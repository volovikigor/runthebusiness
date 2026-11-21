package com.example.runthebusiness.data.local

import android.content.Context
import com.example.runthebusiness.data.local.entity.CafeArrivalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CafeArrivalRepository(context: Context) {
    private val db = GameDatabase.getInstance(context)

    suspend fun recent(sessionId: String = "debug_session_001"): List<CafeArrivalEntity> =
        withContext(Dispatchers.IO) {
            val business = db.gameSetupDao().getBusinesses(sessionId).singleOrNull()
                ?: return@withContext emptyList()
            val location = db.businessLocationDao().getLocations(business.id).singleOrNull()
                ?: return@withContext emptyList()
            db.cafeArrivalDao().recent(location.id)
        }
}
