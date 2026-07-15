package com.daex.android.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start timing benchmark for [com.daex.android.MainActivity] - measures time-to-first-frame
 * against the real app APK built with the `benchmark` build type (non-debuggable, so JIT/ART
 * behave like a genuine release build, unlike every JVM test in this suite). Requires a real,
 * unlocked device or emulator with USB debugging
 * (`:macrobenchmark:connectedBenchmarkAndroidTest`) - cannot run in this environment, which has
 * no adb/device access. Written for correctness and reviewed, not executed here.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupToFirstFrame() = benchmarkRule.measureRepeated(
        packageName = "com.daex.android",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
