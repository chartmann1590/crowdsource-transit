package com.charles.crowdtransit.app.ai.engine

import android.os.Build
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The single place AssistantEngine resolves to a concrete implementation. Below API 31
 * this never touches LiteRtAssistantEngine's class file — a `Provider<LiteRtAssistantEngine>`
 * is erased to a raw `Provider` in the compiled AssistantEngineFactory, so LiteRtAssistantEngine
 * (and everything it drags in, including com.google.ai.edge.litertlm.* and TransitToolSet)
 * is only classloaded when [create] actually calls `.get()` on it — i.e. never on an
 * unsupported device. See DeviceCapability for the full tier logic this mirrors.
 */
@Singleton
class AssistantEngineFactory @Inject constructor(
    private val liteRtEngineProvider: Provider<LiteRtAssistantEngine>,
) {
    companion object {
        const val MIN_SUPPORTED_SDK_INT = 31
    }

    fun create(): AssistantEngine =
        if (Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK_INT) {
            liteRtEngineProvider.get()
        } else {
            UnsupportedAssistantEngine()
        }
}
