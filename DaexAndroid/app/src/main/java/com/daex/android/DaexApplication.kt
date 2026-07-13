package com.daex.android

import android.app.Application
import com.daex.android.services.CrashLogWriter

class DaexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashLogWriter.writeCrashLog(applicationContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let crash logging itself take down the crash handler.
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
