package com.daex.android

import android.app.Application
import com.daex.android.data.CrashLogWriter
import com.daex.android.data.MyObjectBox
import io.objectbox.BoxStore

class DaexApplication : Application() {
    // Scoped to the process lifetime, not the Activity lifetime - ObjectBox only allows
    // one BoxStore open per database directory, and Android can recreate MainActivity
    // (config changes, rapid relaunches) faster than a previous instance's BoxStore
    // gets garbage-collected.
    val boxStore: BoxStore by lazy {
        MyObjectBox.builder().androidContext(this).build()
    }

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
