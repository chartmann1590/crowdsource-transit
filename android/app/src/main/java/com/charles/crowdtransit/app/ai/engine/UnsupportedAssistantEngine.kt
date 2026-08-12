package com.charles.crowdtransit.app.ai.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

private const val UNSUPPORTED_MESSAGE = "Hopper isn't supported on this device"

/**
 * Used on devices below API 31, where the LiteRT-LM runtime can't run. Never references
 * com.google.ai.edge.litertlm.* — that's the whole point of this class existing.
 */
class UnsupportedAssistantEngine : AssistantEngine {
    override val engineState: StateFlow<AssistantEngineState> =
        MutableStateFlow(AssistantEngineState.Error(UNSUPPORTED_MESSAGE))

    override suspend fun load(modelPath: String, backend: AssistantBackend, cacheDir: String) = Unit

    override fun unload() = Unit

    override fun sendMessage(systemInstruction: String, text: String, imagePath: String?): Flow<AssistantChunk> =
        flow { throw IllegalStateException(UNSUPPORTED_MESSAGE) }

    override fun resetConversation() = Unit
}
