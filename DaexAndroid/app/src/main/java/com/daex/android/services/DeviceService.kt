package com.daex.android.services

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

data class DeviceSpecs(
    val totalRAM: Long,
    val freeStorage: Long,
    val hasVulkan: Boolean,
    val manufacturer: String,
    val model: String,
    val board: String,
    val hardware: String,
    val npuSupported: Boolean,
    val socModel: String,
    val socManufacturer: String
)

class DeviceService(private val context: Context) {

    fun getDeviceSpecs(): DeviceSpecs {
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            ""
        }
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER
        } else {
            ""
        }

        return DeviceSpecs(
            totalRAM = getTotalRAM(),
            freeStorage = getFreeStorage(),
            hasVulkan = hasVulkanSupport(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            npuSupported = hasNpuDriver(),
            socModel = socModel,
            socManufacturer = socManufacturer
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

    fun hasNpuDriver(): Boolean {
        try {
            val libDir = context.applicationInfo.nativeLibraryDir
            val hasDispatchLibInNative = File(libDir).listFiles()?.any {
                it.name.startsWith("libLiteRtDispatch") && it.name.endsWith(".so")
            } == true
            val hasDispatchLibInFiles = context.filesDir.listFiles()?.any {
                it.name.startsWith("libLiteRtDispatch") && it.name.endsWith(".so")
            } == true
            return hasDispatchLibInNative || hasDispatchLibInFiles
        } catch (e: Exception) {
            return false
        }
    }
}
