package com.daex.android.services

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import java.io.File

data class DeviceSpecs(
    val totalRAM: Long,
    val freeStorage: Long,
    val hasVulkan: Boolean
)

class DeviceService(private val context: Context) {

    fun getDeviceSpecs(): DeviceSpecs {
        return DeviceSpecs(
            totalRAM = getTotalRAM(),
            freeStorage = getFreeStorage(),
            hasVulkan = hasVulkanSupport()
        )
    }

    private fun getTotalRAM(): Long {
        val mi = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(mi)
        return mi.totalMem
    }

    private fun getFreeStorage(): Long {
        val path: File = context.filesDir
        val stat = StatFs(path.path)
        return stat.availableBytes
    }

    private fun hasVulkanSupport(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 0) ||
               context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0)
    }
}
