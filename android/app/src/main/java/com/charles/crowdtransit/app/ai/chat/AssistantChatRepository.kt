package com.charles.crowdtransit.app.ai.chat

import com.charles.crowdtransit.app.data.local.dao.AssistantMessageDao
import com.charles.crowdtransit.app.data.local.entities.AssistantMessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Local-only chat history — never written to Firebase (see PRIVACY.md). */
@Singleton
class AssistantChatRepository @Inject constructor(
    private val dao: AssistantMessageDao,
) {
    companion object {
        /** Single ongoing conversation per install — no multi-session UI in v1. */
        const val DEFAULT_SESSION_ID = "default"
    }

    fun observeMessages(sessionId: String = DEFAULT_SESSION_ID): Flow<List<AssistantMessageEntity>> =
        dao.observeMessages(sessionId)

    suspend fun addUserMessage(text: String, sessionId: String = DEFAULT_SESSION_ID) {
        dao.insert(AssistantMessageEntity(sessionId = sessionId, role = "user", text = text, createdAt = System.currentTimeMillis()))
    }

    suspend fun addModelMessage(text: String, sessionId: String = DEFAULT_SESSION_ID) {
        dao.insert(AssistantMessageEntity(sessionId = sessionId, role = "model", text = text, createdAt = System.currentTimeMillis()))
    }

    suspend fun clear() {
        dao.clearAll()
    }
}
