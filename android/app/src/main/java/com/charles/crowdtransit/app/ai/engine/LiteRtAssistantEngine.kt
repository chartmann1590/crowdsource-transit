package com.charles.crowdtransit.app.ai.engine

import com.charles.crowdtransit.app.ai.context.TransitToolSet
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real Hopper engine — the only file besides TransitToolSet allowed to import
 * com.google.ai.edge.litertlm.*. Only ever constructed behind AssistantEngineFactory's
 * SDK_INT >= 31 check, so this class (and the native runtime it loads) is never touched
 * on a device the app's minSdk-26 floor would otherwise still support.
 *
 * Sampler tuning (topK=40, topP=0.95, temperature=0.7) is a reasonable chat default;
 * there's no evidence yet it needs to differ from Gemma's own recommendation.
 */
@Singleton
class LiteRtAssistantEngine @Inject constructor(
    private val toolSet: TransitToolSet,
) : AssistantEngine {

    companion object {
        private val SAMPLER_CONFIG = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 0)
        private const val IDLE_UNLOAD_MS = 5 * 60 * 1000L
    }

    private val _engineState = MutableStateFlow<AssistantEngineState>(AssistantEngineState.Unloaded)
    override val engineState: StateFlow<AssistantEngineState> = _engineState.asStateFlow()

    private val loadMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationSystemInstruction: String? = null
    private var idleWatchdog: Job? = null

    override suspend fun load(modelPath: String, backend: AssistantBackend, cacheDir: String) {
        loadMutex.withLock {
            if (engine != null) return
            _engineState.value = AssistantEngineState.Loading
            try {
                engine = tryInitialize(modelPath, backend, cacheDir)
            } catch (e: Exception) {
                // GPU backend selection is RAM-tier-based (DeviceCapability), not a real
                // delegate probe — retry on CPU once before giving up, so a device that
                // qualifies for GPU by RAM but lacks a working delegate still gets Hopper.
                if (backend == AssistantBackend.GPU) {
                    try {
                        engine = tryInitialize(modelPath, AssistantBackend.CPU, cacheDir)
                    } catch (cpuFailure: Exception) {
                        _engineState.value = AssistantEngineState.Error(cpuFailure.message ?: "failed to load Hopper")
                        return
                    }
                } else {
                    _engineState.value = AssistantEngineState.Error(e.message ?: "failed to load Hopper")
                    return
                }
            }
            _engineState.value = AssistantEngineState.Ready
            armIdleWatchdog()
        }
    }

    // ============================================================================
    // Working end-to-end on a real Pixel 8 Pro, both variants — history of what it took:
    //   1. litertlm-android 0.9.0-beta could not run "scan a stop sign" at all: passing a
    //      non-null visionBackend threw "must have exactly one signature but got 3" at
    //      Engine.initialize() time, and passing null (which avoided that) instead
    //      SIGSEGV-crashed the whole app process the moment an image was actually sent —
    //      confirmed via a real device crash tombstone, null pointer deref inside
    //      liblitertlm_jni.so's vision path. Neither config was fixable from app code;
    //      it was a genuine bug in that specific SDK pin. Fixed 2026-08-08 by bumping
    //      litertlm-android 0.9.0-beta -> 0.15.0 (which itself required bumping Kotlin
    //      2.1.0 -> 2.3.0, AGP 8.7.3 -> 8.13.2, Hilt 2.56.2 -> 2.58, and moving Moshi's
    //      codegen off kapt onto KSP — see gradle/libs.versions.toml and
    //      app/build.gradle.kts for the full trail). On 0.15.0, a null visionBackend now
    //      throws a normal, catchable error instead of crashing ("Vision executor should
    //      not be null, please TryLoadingVisionExecutor() first"), and passing a real
    //      Backend.CPU() for it initializes cleanly with no signature-count error — that
    //      old validation bug is gone too. gemma-4-E2B-it-web.litertlm (the "web"
    //      variant) is still NOT usable here — it's built for a browser/WASM runtime, not
    //      this Android engine, and throws an unrelated "TF_LITE_PREFILL_DECODE not
    //      found" regardless of SDK version. Use gemma-4-E2B-it.litertlm for both
    //      text-only and multimodal use — text-only mode just never calls the vision tool.
    //   2. GPU backend for the MAIN text model: Engine.initialize() succeeds even though
    //      the OpenCL delegate isn't actually usable — the failure only surfaces at the
    //      first real generation call ("Can not find OpenCL library on this device"),
    //      so the load-time CPU-fallback below never catches it. CPU is therefore the
    //      only backend actually proven to work; see AssistantSettingsViewModel, which
    //      no longer selects "gpu" for any tier.
    //   3. The native-library-loading race described below.
    // ============================================================================

    // visionBackend now mirrors the main text backend (see fix #1 above) instead of
    // always being null — needed for real image analysis to actually run rather than
    // throw "Vision executor should not be null". audioBackend stays null: Hopper never
    // sends audio, and there's no evidence yet the same fix is needed there.
    // initialize() can take up to ~10s; load() is already expected to run on a
    // background coroutine, but withContext guards callers that don't.
    private suspend fun tryInitialize(modelPath: String, backend: AssistantBackend, cacheDir: String): Engine {
        val litertBackend = when (backend) {
            AssistantBackend.CPU -> Backend.CPU()
            AssistantBackend.GPU -> Backend.GPU()
        }

        // Works around a real native-library-loading race in litertlm-android 0.9.0-beta,
        // confirmed on a Pixel 8 Pro: constructing Engine/EngineConfig can call into the
        // native side before liblitertlm_jni.so has finished registering its JNI methods
        // ("No implementation found for NativeLibraryLoader.nativeCheckLoaded() ... is the
        // library loaded, e.g. System.loadLibrary?"), which then makes initialize() fail
        // with "Engine is not initialized." Forcing the load synchronously first — and
        // retrying once after a short delay if initialize() still fails — resolves it.
        // NativeLibraryLoader is `internal` at the Kotlin level (public bytecode, gated by
        // Kotlin metadata), so it's reached via reflection rather than a direct call.
        withContext(Dispatchers.Default) {
            runCatching {
                val loaderClass = Class.forName("com.google.ai.edge.litertlm.NativeLibraryLoader")
                val instance = loaderClass.getField("INSTANCE").get(null)
                loaderClass.getMethod("load").invoke(instance)
            }
        }

        var lastError: Exception? = null
        repeat(2) { attempt ->
            val newEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = litertBackend,
                    visionBackend = litertBackend,
                    audioBackend = null,
                    maxNumTokens = null,
                    cacheDir = cacheDir,
                ),
            )
            try {
                withContext(Dispatchers.Default) { newEngine.initialize() }
                return newEngine
            } catch (e: Exception) {
                newEngine.close()
                lastError = e
                if (attempt == 0) delay(750)
            }
        }
        throw checkNotNull(lastError)
    }

    override fun unload() {
        idleWatchdog?.cancel()
        idleWatchdog = null
        conversation?.close()
        conversation = null
        conversationSystemInstruction = null
        engine?.close()
        engine = null
        _engineState.value = AssistantEngineState.Unloaded
    }

    override fun resetConversation() {
        conversation?.close()
        conversation = null
        conversationSystemInstruction = null
    }

    override fun sendMessage(systemInstruction: String, text: String, imagePath: String?): Flow<AssistantChunk> = flow {
        val activeEngine = engine ?: throw IllegalStateException("Hopper isn't loaded")
        armIdleWatchdog()

        val activeConversation = conversationFor(activeEngine, systemInstruction)
        val contents = if (imagePath != null) {
            Contents.of(Content.ImageFile(imagePath), Content.Text(text))
        } else {
            Contents.of(text)
        }

        var chunkCount = 0
        activeConversation.sendMessageAsync(contents, emptyMap()).collect { message ->
            chunkCount++
            val textDelta = message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
            android.util.Log.d(
                "HopperEngine",
                "chunk #$chunkCount: contentTypes=${message.contents.contents.map { it.javaClass.simpleName }} textDelta=\"$textDelta\"",
            )
            if (textDelta.isNotEmpty()) emit(AssistantChunk(textDelta))
        }
        android.util.Log.d("HopperEngine", "sendMessageAsync flow completed after $chunkCount chunk(s)")
    }

    private fun conversationFor(engine: Engine, systemInstruction: String): Conversation {
        val existing = conversation
        if (existing != null && existing.isAlive && conversationSystemInstruction == systemInstruction) {
            return existing
        }
        existing?.close()
        val created = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                tools = listOf(tool(toolSet)),
                samplerConfig = SAMPLER_CONFIG,
                automaticToolCalling = true,
            ),
        )
        conversation = created
        conversationSystemInstruction = systemInstruction
        return created
    }

    /**
     * Unloads the ~2 GB resident model after a period of inactivity — left running
     * indefinitely it competes with MapLibre's tile cache for memory and risks the OS
     * killing the whole app. Restarted on every message.
     */
    private fun armIdleWatchdog() {
        idleWatchdog?.cancel()
        idleWatchdog = scope.launch {
            delay(IDLE_UNLOAD_MS)
            if (isActive) unload()
        }
    }
}
