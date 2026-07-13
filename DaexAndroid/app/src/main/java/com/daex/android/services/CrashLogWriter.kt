package com.daex.android.services

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogWriter {
    private const val CRASH_FILE_PREFIX = "daex_crash_"
    private const val MAX_RETAINED_CRASH_LOGS = 3

    private fun logsDir(context: Context): File = File(context.cacheDir, "logs")

    /** Writes a crash report to cacheDir/logs and prunes older ones. Safe to call from an uncaught-exception handler. */
    fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable): File {
        val dir = logsDir(context)
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val crashFile = File(dir, "$CRASH_FILE_PREFIX$timestamp.txt")

        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }

        val report = buildString {
            appendLine("DAEX crash report")
            appendLine("Time: ${Date()}")
            appendLine("App version: $versionName")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(android.util.Log.getStackTraceString(throwable))
        }
        crashFile.writeText(report)

        pruneOldCrashLogs(dir)
        return crashFile
    }

    /** Returns the most recent pending crash log, if any, for a "share crash log?" prompt on next launch. */
    fun findPendingCrashLog(context: Context): File? {
        val dir = logsDir(context)
        if (!dir.exists()) return null
        return dir.listFiles { f -> f.name.startsWith(CRASH_FILE_PREFIX) }
            ?.maxByOrNull { it.lastModified() }
    }

    fun clearCrashLogs(context: Context) {
        logsDir(context).listFiles { f -> f.name.startsWith(CRASH_FILE_PREFIX) }?.forEach { it.delete() }
    }

    private fun pruneOldCrashLogs(dir: File) {
        val crashFiles = dir.listFiles { f -> f.name.startsWith(CRASH_FILE_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        crashFiles.drop(MAX_RETAINED_CRASH_LOGS).forEach { it.delete() }
    }
}
