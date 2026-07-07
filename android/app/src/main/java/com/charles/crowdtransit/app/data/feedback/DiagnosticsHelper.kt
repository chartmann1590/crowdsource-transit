package com.charles.crowdtransit.app.data.feedback

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DiagnosticsHelper {

    fun collect(context: Context): String {
        val pkg = context.packageName
        val info = try {
            context.packageManager.getPackageInfo(pkg, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        val lines = mutableListOf<String>()
        lines.add("## Diagnostics")
        lines.add("")

        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        lines.add("- App: $appName")
        lines.add("- Package: $pkg")
        lines.add("- Version: ${info?.versionName ?: "N/A"} (${info?.versionCode ?: "N/A"})")
        lines.add("- Device: ${Build.MODEL}")
        lines.add("- Manufacturer: ${Build.MANUFACTURER}")
        lines.add("- Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        lines.add("- Locale: ${Locale.getDefault()}")
        lines.add("- Time Zone: ${TimeZone.getDefault().id}")

        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            lines.add("- Storage Free/Total: ${formatSize(free)} / ${formatSize(total)}")
        } catch (_: Exception) {
            lines.add("- Storage: N/A")
        }

        try {
            val runtime = Runtime.getRuntime()
            val maxMem = runtime.maxMemory()
            val freeMem = runtime.freeMemory()
            val totalMem = runtime.totalMemory()
            lines.add("- Memory Free/Total: ${formatSize(freeMem)} / ${formatSize(maxMem)}")
        } catch (_: Exception) {
            lines.add("- Memory: N/A")
        }

        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        lines.add("- Timestamp: ${df.format(Date())}")

        return lines.joinToString("\n")
    }

    private fun formatSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.size - 1) {
            size /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} B" else "%.1f %s".format(size, units[unit])
    }
}
