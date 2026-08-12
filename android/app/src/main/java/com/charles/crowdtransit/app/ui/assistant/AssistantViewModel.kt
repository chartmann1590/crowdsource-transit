package com.charles.crowdtransit.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.ai.chat.AssistantChatMessage
import com.charles.crowdtransit.app.ai.chat.AssistantSession
import com.charles.crowdtransit.app.ai.engine.AssistantEngineState
import com.charles.crowdtransit.app.ai.model.AssistantModelCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val session: AssistantSession,
) : ViewModel() {

    val engineState: StateFlow<AssistantEngineState> = session.engineState

    /** Null until a model is actually installed — AssistantScreen shows a setup prompt
     * instead of the chat UI while this is null, so the user is never dropped into a
     * chat that can't do anything yet. */
    val installedVariant: StateFlow<AssistantModelCatalog.Variant?> = session.installedVariant

    val messages: StateFlow<List<AssistantChatMessage>> = session.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True once Hopper's planTrip tool has a real trip loaded — shows a "View route" action. */
    val hasPlan: StateFlow<Boolean> = session.hasPlan

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    /** Streaming reply-so-far, shown as a trailing bubble; null once no message is in flight. */
    private val _draftReply = MutableStateFlow<String?>(null)
    val draftReply: StateFlow<String?> = _draftReply.asStateFlow()

    init {
        viewModelScope.launch {
            installedVariant.collect { variant ->
                if (variant != null) session.ensureLoaded()
            }
        }
    }

    fun sendMessage(text: String, imagePath: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _draftReply.value = ""
            // AssistantSession.send() catches engine/model errors itself and persists a
            // friendly message to history — this catch is only a defensive fallback for
            // failures outside that (e.g. building context). Either way the draft is
            // cleared once messages (the Room-backed history) has the real result.
            try {
                session.send(trimmed, imagePath).collect { partial -> _draftReply.value = partial }
            } catch (e: Exception) {
                _draftReply.value = null
            } finally {
                _draftReply.value = null
                _isSending.value = false
                // The captured photo is processed on-device and must not linger — see
                // PRIVACY.md's "scan a stop sign" disclosure.
                imagePath?.let { runCatching { java.io.File(it).delete() } }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { session.clearHistory() }
    }

    // No unload-on-clear: AssistantSession/its engine are app-scoped singletons with their
    // own idle-timeout unload (see LiteRtAssistantEngine). Unloading here would force a
    // ~10s reload every time the rider re-opens the chat after navigating away.
}
