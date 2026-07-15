package com.daex.android

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device smoke test: launches the actual [MainActivity] with its true production wiring
 * (real ObjectBox, real ModelManager/DeviceService/DaexEmbedder, nothing mocked - unlike every
 * JVM test in this suite) and confirms it reaches a resumed state without crashing. This is the
 * only test in the whole suite that exercises the true on-device dependency graph end to end.
 * Requires a connected device or emulator (`:app:connectedDebugAndroidTest`) - verified passing
 * on a real device (Galaxy S24 Ultra class hardware, API 37).
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun mainActivityLaunchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }
}
