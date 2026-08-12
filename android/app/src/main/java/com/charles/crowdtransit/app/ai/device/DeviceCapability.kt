package com.charles.crowdtransit.app.ai.device

/**
 * Pure classification of what a device can do with the Hopper AI assistant. No Android
 * dependencies — [DeviceProbe] gathers the inputs on-device, this decides the tier.
 * Unit-tested directly (DeviceCapabilityTest), mirroring the domain/ convention used by
 * NavigationEngine and TransitRouter.
 */
object DeviceCapability {

    private const val MIN_SDK_INT = 31
    private const val MIN_TOTAL_MEM_BYTES = 4_000_000_000L
    private const val STANDARD_TOTAL_MEM_BYTES = 6_000_000_000L
    private const val ADVANCED_TOTAL_MEM_BYTES = 8_000_000_000L

    enum class Tier {
        /** AI never appears: no FAB, no onboarding page, no download option. */
        Unsupported,

        /** Text-only offered alone; CPU backend; honest "this will be slow" copy. */
        Basic,

        /** Both variants offered; text-only recommended; GPU backend if available. */
        Standard,

        /** Both variants offered; multimodal recommended; unlocks camera scanning. */
        Advanced,
    }

    data class DeviceSpec(
        val sdkInt: Int,
        val totalMemBytes: Long,
        val isLowRamDevice: Boolean,
        val hasArm64: Boolean,
        val freeStorageBytes: Long,
    )

    fun classify(spec: DeviceSpec): Tier {
        if (spec.sdkInt < MIN_SDK_INT) return Tier.Unsupported
        if (spec.isLowRamDevice) return Tier.Unsupported
        if (!spec.hasArm64) return Tier.Unsupported
        if (spec.totalMemBytes < MIN_TOTAL_MEM_BYTES) return Tier.Unsupported

        // Tier is RAM-based only. hasOpenCl is deliberately not a gate here: apps can't
        // reliably dlopen a system driver lib like libOpenCL.so directly (Android's linker
        // namespace sandboxing blocks it on real hardware), so a probe built on
        // System.loadLibrary("OpenCL") reports false almost universally — gating Advanced
        // on it meant no device could ever reach Advanced. GPU-vs-CPU backend selection is
        // handled separately by the engine, which falls back to CPU if GPU init fails.
        return when {
            spec.totalMemBytes < STANDARD_TOTAL_MEM_BYTES -> Tier.Basic
            spec.totalMemBytes < ADVANCED_TOTAL_MEM_BYTES -> Tier.Standard
            else -> Tier.Advanced
        }
    }

    /** Extra free space required beyond the model's own size (partial file + margin). */
    private const val STORAGE_HEADROOM_BYTES = 1_500_000_000L

    /** Whether there's enough free space to download the given model size right now. */
    fun hasStorageFor(modelSizeBytes: Long, freeStorageBytes: Long): Boolean =
        freeStorageBytes >= modelSizeBytes + STORAGE_HEADROOM_BYTES
}
