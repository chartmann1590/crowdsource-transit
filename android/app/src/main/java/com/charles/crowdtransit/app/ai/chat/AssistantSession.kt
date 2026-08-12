package com.charles.crowdtransit.app.ai.chat

import android.content.Context
import com.charles.crowdtransit.app.ai.context.AssistantContextBuilder
import com.charles.crowdtransit.app.ai.engine.AssistantBackend
import com.charles.crowdtransit.app.ai.engine.AssistantEngine
import com.charles.crowdtransit.app.ai.engine.AssistantEngineFactory
import com.charles.crowdtransit.app.ai.engine.AssistantEngineState
import com.charles.crowdtransit.app.ai.model.AssistantModelCatalog
import com.charles.crowdtransit.app.ai.model.AssistantModelDownloader
import com.charles.crowdtransit.app.ai.model.AssistantModelStore
import com.charles.crowdtransit.app.data.preferences.UserPreferencesStore
import com.charles.crowdtransit.app.data.trip.TripSessionHolder
import com.charles.crowdtransit.app.service.AssistantInferenceService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point the UI talks to for chatting with Hopper: resolves the right
 * engine (real or unsupported, via [AssistantEngineFactory]), keeps it loaded against
 * the installed model, builds fresh context per message, and persists both sides of the
 * conversation to [AssistantChatRepository]. Mirrors PlanTripUseCase's role as the one
 * orchestrator sitting above several single-purpose collaborators.
 */
@Singleton
class AssistantSession @Inject constructor(
    @ApplicationContext private val context: Context,
    engineFactory: AssistantEngineFactory,
    private val modelStore: AssistantModelStore,
    private val contextBuilder: AssistantContextBuilder,
    private val chatRepository: AssistantChatRepository,
    private val userPreferences: UserPreferencesStore,
    private val tripSession: TripSessionHolder,
    downloader: AssistantModelDownloader,
) {
    private val engine: AssistantEngine = engineFactory.create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val engineState: StateFlow<AssistantEngineState> = engine.engineState

    val messages: Flow<List<AssistantChatMessage>> = chatRepository.observeMessages().map { entities ->
        entities.map { AssistantChatMessage(it.role, it.text, it.createdAt) }
    }

    /**
     * Which variant is actually installed on disk right now, or null if none — the UI
     * uses this to decide whether the chat screen is even shown (see AssistantScreen's
     * setup gate) rather than letting the user into a chat that can't do anything yet.
     * Recomputed whenever the selected variant preference or the downloader's state
     * changes (i.e. the instant a download finishes), not just once at screen open.
     */
    val installedVariant: StateFlow<AssistantModelCatalog.Variant?> = combine(
        userPreferences.assistantVariant,
        downloader.state,
    ) { variantName, _ ->
        val variant = variantName?.let { runCatching { AssistantModelCatalog.Variant.valueOf(it) }.getOrNull() }
        variant?.takeIf { modelStore.isInstalled(AssistantModelCatalog.byVariant(it)) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), modelStore.installedVariant())

    /**
     * True once `planTrip` (see [com.charles.crowdtransit.app.ai.context.TransitToolSet])
     * has selected a real trip into [TripSessionHolder] — the chat UI uses this to show a
     * "View route" action so the rider can open the full itinerary (map, steps, and a
     * Navigate button) instead of only reading Hopper's text summary.
     */
    val hasPlan: StateFlow<Boolean> = tripSession.selectedPlan
        .map { it != null }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), tripSession.selectedPlan.value != null)

    suspend fun ensureLoaded() {
        if (engine.engineState.value is AssistantEngineState.Ready) return
        val variantName = userPreferences.assistantVariant.first() ?: return
        val variant = runCatching { AssistantModelCatalog.Variant.valueOf(variantName) }.getOrNull() ?: return
        val info = AssistantModelCatalog.byVariant(variant)
        if (!modelStore.isInstalled(info)) return

        val backendName = userPreferences.assistantBackend.first()
        val backend = if (backendName == "gpu") AssistantBackend.GPU else AssistantBackend.CPU
        engine.load(
            modelPath = modelStore.finalFile(info).absolutePath,
            backend = backend,
            cacheDir = modelStore.cacheDir.absolutePath,
        )
    }

    /**
     * Sends [text], streaming the reply; both sides are persisted to history once
     * complete. Failures (no model installed, engine error, …) are turned into a
     * friendly model-role message rather than propagated as an exception, so they show
     * up in the chat like a normal reply instead of silently vanishing — a plain
     * try/catch in the caller isn't enough here since the caller's own `finally` would
     * otherwise clear a transient error state before the user ever sees it.
     */
    suspend fun send(text: String, imagePath: String? = null): Flow<String> {
        // Model load (~10s) plus CPU inference over an image (can run well past a
        // minute) together make up the whole vulnerable window — confirmed on a real
        // device that the app process gets killed by the system the moment it's even
        // briefly backgrounded during either part (most commonly the system camera
        // taking focus right before this call, for "scan a stop sign"), silently losing
        // the reply. This foreground service raises process priority for the duration so
        // that doesn't happen — see AssistantInferenceService. Started before
        // ensureLoaded() (not inside the flow below) since loading is just as vulnerable
        // as generating and happens first.
        AssistantInferenceService.start(context)
        ensureLoaded()
        chatRepository.addUserMessage(text)
        // systemInstruction is static for the whole session (see AssistantContextBuilder)
        // so the model's Conversation — and its memory of earlier turns — survives across
        // messages; live trip/time context is prepended per-turn instead.
        val systemInstruction = contextBuilder.staticInstruction()
        val dynamicContext = contextBuilder.buildCurrent()
        val turnText = "$dynamicContext\n\n$text"
        val reply = StringBuilder()
        return flow {
            try {
                val planBefore = tripSession.selectedPlan.value
                streamStrippingEcho(systemInstruction, turnText, imagePath, reply) { emit(reply.toString()) }

                // planTrip (see TransitToolSet) runs synchronously inside the model's tool
                // call, so by the time this turn's generation resumes the tool result is
                // already known — but a 2B model, confirmed live on-device, sometimes
                // commits to a "let me check that, one moment" stalling reply *before* the
                // tool result lands and just stops there, never actually narrating the
                // trip it already found. Detect that (a new plan appeared this turn) and
                // give the model one more nudge, now with the plan already in its context.
                val planAfter = tripSession.selectedPlan.value
                if (planAfter != null && planAfter !== planBefore) {
                    val nudgeContext = contextBuilder.buildCurrent()
                    val nudgeText = "$nudgeContext\n\nTell the rider about the trip you just found, in 2-3 sentences."
                    if (reply.isNotEmpty()) reply.append("\n\n")
                    streamStrippingEcho(systemInstruction, nudgeText, imagePath = null, reply) { emit(reply.toString()) }
                }
            } catch (e: Exception) {
                val message = if (engine.engineState.value !is AssistantEngineState.Ready) {
                    "Hopper isn't set up yet — enable it and download a model from Settings > AI Assistant."
                } else {
                    "Sorry, something went wrong: ${e.message ?: "unknown error"}"
                }
                reply.clear()
                reply.append(message)
                emit(message)
            } finally {
                AssistantInferenceService.stop(context)
            }
            if (reply.isNotEmpty()) chatRepository.addModelMessage(reply.toString())
        }
    }

    /**
     * Streams one model turn into [reply], stripping a leading echo of the injected
     * "[Current status]" context block if the model repeats it back verbatim before
     * actually answering — confirmed live on-device. Keys off the short, fixed marker
     * line plus the blank line that ends the block, rather than an exact character-for-
     * character match against the whole (often 200+ char) context string: a first version
     * did the latter and missed real leaks where the model reformatted the block slightly
     * (e.g. different line spacing) while still clearly echoing it.
     */
    private suspend fun streamStrippingEcho(
        systemInstruction: String,
        turnText: String,
        imagePath: String?,
        reply: StringBuilder,
        onProgress: suspend () -> Unit,
    ) {
        val marker = "[Current status]"
        val raw = StringBuilder()
        var resolved = false
        engine.sendMessage(systemInstruction, turnText, imagePath).collect { chunk ->
            if (resolved) {
                reply.append(chunk.textDelta)
                onProgress()
                return@collect
            }
            raw.append(chunk.textDelta)
            val prefixLen = minOf(raw.length, marker.length)
            if (!raw.regionMatches(0, marker, 0, prefixLen)) {
                // Doesn't look like an echo at all — flush what's buffered and stop checking.
                resolved = true
                reply.append(raw)
                if (reply.isNotEmpty()) onProgress()
                return@collect
            }
            if (raw.length < marker.length) return@collect // still buffering the marker itself
            val blankLineEnd = raw.indexOf("\n\n").let { if (it >= 0) it + 2 else -1 }
            if (blankLineEnd >= 0) {
                resolved = true
                reply.append(raw.substring(blankLineEnd).trimStart('\n', ' '))
                if (reply.isNotEmpty()) onProgress()
            }
        }
        if (!resolved && raw.isNotEmpty()) {
            // Stream ended before the echoed block's closing blank line ever arrived
            // (a short reply) — flush whatever was buffered rather than dropping it.
            reply.append(raw)
            onProgress()
        }
    }

    fun resetConversation() = engine.resetConversation()

    suspend fun clearHistory() {
        chatRepository.clear()
        resetConversation()
    }

    fun unload() = engine.unload()
}

data class AssistantChatMessage(val role: String, val text: String, val createdAt: Long)
