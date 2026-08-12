package com.charles.crowdtransit.app.ai.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class AssistantBackend { CPU, GPU }

sealed class AssistantEngineState {
    data object Unloaded : AssistantEngineState()
    data object Loading : AssistantEngineState()
    data object Ready : AssistantEngineState()
    data class Error(val message: String) : AssistantEngineState()
}

/** One incremental piece of a streaming assistant reply. */
data class AssistantChunk(val textDelta: String)

/**
 * Talks to whatever on-device LLM runtime backs Hopper. Every caller (ViewModel, chat
 * repository, UI) goes through this interface rather than the concrete implementation —
 * only LiteRtAssistantEngine.kt and TransitToolSet.kt are allowed to import
 * com.google.ai.edge.litertlm.*, so no LiteRT-LM class is ever loaded on a device below
 * API 31 (see AssistantEngineFactory, which is the only place this interface is resolved
 * to a concrete implementation).
 */
interface AssistantEngine {
    val engineState: StateFlow<AssistantEngineState>

    suspend fun load(modelPath: String, backend: AssistantBackend, cacheDir: String)

    /** Frees the resident model. Safe to call even if never loaded. */
    fun unload()

    /**
     * Sends one message in Hopper's single ongoing conversation, streaming the reply as
     * incremental text chunks. [systemInstruction] is re-applied whenever it changes
     * (e.g. the rider starts navigating) — see [resetConversation].
     */
    fun sendMessage(systemInstruction: String, text: String, imagePath: String? = null): Flow<AssistantChunk>

    /** Starts a fresh conversation (e.g. "clear chat" in settings). */
    fun resetConversation()
}
