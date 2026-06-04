package com.daex.android.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceTools(private val context: Context) : ToolSet {

    companion object {
        private const val TAG = "DeviceTools"
    }

    @Tool(description = "Get the current date and local system time")
    fun getDeviceTime(): String {
        Log.d(TAG, "getDeviceTime execution triggered")
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val result = formatter.format(Date())
        Log.d(TAG, "getDeviceTime returning: $result")
        return result
    }

    @Tool(description = "Get the current battery level percentage and charging state")
    fun getBatteryStatus(): String {
        Log.d(TAG, "getBatteryStatus execution triggered")
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL
        
        val result = "Battery Level: $batteryPct%, Charging: $isCharging"
        Log.d(TAG, "getBatteryStatus returning: $result")
        return result
    }

    @Tool(description = "Get the available free disk space and total disk space in GB")
    fun getStorageStatus(): String {
        Log.d(TAG, "getStorageStatus execution triggered")
        val path: File = context.filesDir
        val stat = StatFs(path.path)
        val free = stat.availableBytes
        val total = stat.totalBytes
        val freeGb = String.format(Locale.US, "%.2f", free / (1024.0 * 1024.0 * 1024.0))
        val totalGb = String.format(Locale.US, "%.2f", total / (1024.0 * 1024.0 * 1024.0))
        val result = "Storage Free: $freeGb GB, Total: $totalGb GB"
        Log.d(TAG, "getStorageStatus returning: $result")
        return result
    }

    @Tool(description = "Get the device name, manufacturer, model, and Android OS version info")
    fun getDeviceInfo(): String {
        Log.d(TAG, "getDeviceInfo execution triggered")
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val sdk = Build.VERSION.SDK_INT
        val release = Build.VERSION.RELEASE
        val result = "Device: $manufacturer $model, Android SDK: $sdk, OS Version: $release"
        Log.d(TAG, "getDeviceInfo returning: $result")
        return result
    }

    @Tool(description = "Launch an installed application on the device by its name")
    fun launchApp(
        @ToolParam(description = "The display name of the application to launch (e.g. 'Spotify', 'YouTube')") appName: String
    ): String {
        Log.d(TAG, "launchApp execution triggered for appName: $appName")
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in packages) {
                val name = pm.getApplicationLabel(appInfo).toString()
                if (name.equals(appName, ignoreCase = true)) {
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    return if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        val msg = "Launched $appName successfully"
                        Log.d(TAG, msg)
                        msg
                    } else {
                        val msg = "App $appName found but has no launchable main Activity"
                        Log.d(TAG, msg)
                        msg
                    }
                }
            }
            val msg = "App '$appName' not found on this device"
            Log.d(TAG, msg)
            return msg
        } catch (e: Exception) {
            val msg = "Failed to launch $appName: ${e.message}"
            Log.e(TAG, msg, e)
            return msg
        }
    }
}
