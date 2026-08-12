package com.charles.crowdtransit.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One turn of Hopper chat history. Local-only — never synced to Firebase. */
@Entity(tableName = "assistant_message")
data class AssistantMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    /** "user" or "model" — mirrors LiteRT-LM's Role without depending on it here. */
    val role: String,
    val text: String,
    val createdAt: Long,
)
