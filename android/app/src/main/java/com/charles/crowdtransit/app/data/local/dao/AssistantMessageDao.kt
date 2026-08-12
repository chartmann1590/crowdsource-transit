package com.charles.crowdtransit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.charles.crowdtransit.app.data.local.entities.AssistantMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantMessageDao {

    @Query("SELECT * FROM assistant_message WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeMessages(sessionId: String): Flow<List<AssistantMessageEntity>>

    @Insert
    suspend fun insert(message: AssistantMessageEntity): Long

    @Query("DELETE FROM assistant_message")
    suspend fun clearAll()
}
