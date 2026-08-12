package com.charles.crowdtransit.app.ai.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gathers [DeviceCapability.DeviceSpec] from the running device. All Android-specific
 * measurement lives here; [DeviceCapability.classify] stays pure and unit-testable.
 */
@Singleton
class DeviceProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun currentSpec(): DeviceCapability.DeviceSpec {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val stat = StatFs(context.filesDir.path)

        return DeviceCapability.DeviceSpec(
            sdkInt = Build.VERSION.SDK_INT,
            totalMemBytes = memInfo.totalMem,
            isLowRamDevice = activityManager.isLowRamDevice,
            hasArm64 = Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" },
            freeStorageBytes = stat.availableBytes,
        )
    }

    fun tier(): DeviceCapability.Tier = DeviceCapability.classify(currentSpec())
}
