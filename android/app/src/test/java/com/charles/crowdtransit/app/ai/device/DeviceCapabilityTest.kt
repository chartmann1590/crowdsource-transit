package com.charles.crowdtransit.app.ai.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityTest {

    private fun spec(
        sdkInt: Int = 33,
        totalMemBytes: Long = 6_500_000_000L,
        isLowRamDevice: Boolean = false,
        hasArm64: Boolean = true,
        freeStorageBytes: Long = 10_000_000_000L,
    ) = DeviceCapability.DeviceSpec(sdkInt, totalMemBytes, isLowRamDevice, hasArm64, freeStorageBytes)

    @Test
    fun `API 30 is unsupported even with plenty of RAM`() {
        assertEquals(DeviceCapability.Tier.Unsupported, DeviceCapability.classify(spec(sdkInt = 30, totalMemBytes = 12_000_000_000L)))
    }

    @Test
    fun `API 31 with enough RAM is at least Basic`() {
        assertEquals(DeviceCapability.Tier.Basic, DeviceCapability.classify(spec(sdkInt = 31, totalMemBytes = 4_100_000_000L)))
    }

    @Test
    fun `low RAM device flag forces Unsupported regardless of reported memory`() {
        assertEquals(
            DeviceCapability.Tier.Unsupported,
            DeviceCapability.classify(spec(isLowRamDevice = true, totalMemBytes = 12_000_000_000L)),
        )
    }

    @Test
    fun `missing arm64 forces Unsupported`() {
        assertEquals(DeviceCapability.Tier.Unsupported, DeviceCapability.classify(spec(hasArm64 = false)))
    }

    @Test
    fun `3point9 GB is unsupported`() {
        assertEquals(DeviceCapability.Tier.Unsupported, DeviceCapability.classify(spec(totalMemBytes = 3_900_000_000L)))
    }

    @Test
    fun `4point1 GB is Basic`() {
        assertEquals(DeviceCapability.Tier.Basic, DeviceCapability.classify(spec(totalMemBytes = 4_100_000_000L)))
    }

    @Test
    fun `6point1 GB is Standard`() {
        assertEquals(DeviceCapability.Tier.Standard, DeviceCapability.classify(spec(totalMemBytes = 6_100_000_000L)))
    }

    @Test
    fun `8point1 GB is Advanced regardless of GPU delegate availability`() {
        // Tier is RAM-only: a real-device check found System.loadLibrary("OpenCL") throws
        // on real hardware (Android blocks apps from dlopen-ing system driver libs), so
        // gating Advanced on that probe meant no device could ever reach it.
        assertEquals(DeviceCapability.Tier.Advanced, DeviceCapability.classify(spec(totalMemBytes = 8_100_000_000L)))
    }

    @Test
    fun `hasStorageFor requires headroom beyond the model size`() {
        val modelSize = 2_590_000_000L
        assertTrue(DeviceCapability.hasStorageFor(modelSize, freeStorageBytes = modelSize + 1_600_000_000L))
        assertFalse(DeviceCapability.hasStorageFor(modelSize, freeStorageBytes = modelSize + 1_000_000_000L))
    }
}
