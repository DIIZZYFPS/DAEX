package com.daex.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object LogShareHelper {
    fun shareAppLogs(context: Context) {
        try {
            // Get self logcat output using Runtime process execution
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val logText = process.inputStream.bufferedReader().use { it.readText() }

            // Write logs to a file in the app's cache directory
            val logsDir = File(context.cacheDir, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            val logFile = File(logsDir, "daex_logs.txt")
            logFile.writeText(logText)

            // Obtain URI using FileProvider configured in AndroidManifest
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, logFile)

            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "DAEX Application Logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Open sharing chooser sheet
            val chooserIntent = Intent.createChooser(shareIntent, "Share Logs via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            android.util.Log.e("LogShareHelper", "Failed to compile and share system logs", e)
        }
    }
}
