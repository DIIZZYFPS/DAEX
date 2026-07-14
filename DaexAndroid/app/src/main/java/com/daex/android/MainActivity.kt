package com.daex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.remember
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.data.DaexMemory
import com.daex.android.framework.DeviceService
import com.daex.android.data.ModelManager
import com.daex.android.framework.DaexEmbedder
import com.daex.android.data.DaexRagImpl
import com.daex.android.ui.theme.DaexAppTheme
import com.daex.android.ui.ExecutionScreen
import com.daex.android.ui.GalleryScreen
import com.daex.android.ui.SettingsScreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first
import com.daex.android.ui.LandingScreen
import com.daex.android.ui.CrashReportModal
import com.daex.android.ui.ChangelogModal
import com.daex.android.ui.compareSemVer
import com.daex.android.data.CrashLogWriter
import com.daex.android.data.DaexPreferences
import java.io.File

enum class Screen {
    LANDING, EXECUTION, GALLERY, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val boxStore = (application as DaexApplication).boxStore
        val deviceService = DeviceService(this)
        val modelManager = ModelManager(this)
        val daexEmbedder = DaexEmbedder(this, modelManager)
        val daexMemory = DaexMemory(boxStore, daexEmbedder, this.applicationContext)
        val daexService = com.daex.android.framework.DaexServiceImpl(this, daexMemory)
        val daexCoreMemory = com.daex.android.framework.DaexCoreMemoryImpl(this)
        val daexRag = DaexRagImpl(this.applicationContext, boxStore, daexEmbedder)
        val daexSkillManager = com.daex.android.data.DaexSkillManagerImpl(this)

        setContent {
            val daexPreferences = remember { DaexPreferences(this@MainActivity) }
            val viewModel = remember { 
                DaexInferenceViewModel(
                    daexService = daexService,
                    modelManager = modelManager,
                    deviceService = deviceService,
                    daexMemory = daexMemory,
                    daexCoreMemory = daexCoreMemory,
                    preferences = daexPreferences,
                    daexRag = daexRag,
                    daexSkillManager = daexSkillManager,
                    context = applicationContext
                ) 
            }
            var currentScreen by remember { mutableStateOf<Screen?>(null) }
            var pendingCrashFile by remember { mutableStateOf<File?>(null) }
            var autoChangelogVisible by remember { mutableStateOf(false) }
            var changelogSinceVersion by remember { mutableStateOf<String?>(null) }
            var isOnboardingReplay by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val completed = daexPreferences.hasCompletedLandingFlow.first()
                currentScreen = if (completed) Screen.EXECUTION else Screen.LANDING
                pendingCrashFile = CrashLogWriter.findPendingCrashLog(applicationContext)

                val currentVersion = try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: Exception) {
                    null
                }
                if (currentVersion != null) {
                    val lastSeen = daexPreferences.lastSeenVersionFlow.first()
                    if (lastSeen == null) {
                        daexPreferences.setLastSeenVersion(currentVersion)
                    } else if (compareSemVer(currentVersion, lastSeen) > 0) {
                        changelogSinceVersion = lastSeen
                        autoChangelogVisible = true
                        daexPreferences.setLastSeenVersion(currentVersion)
                    }
                }
            }
            
            val primaryColor by viewModel.primaryColor.collectAsState()
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            val screen = currentScreen
            if (screen == null) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                return@setContent
            }

            DaexAppTheme(
                primaryColor = primaryColor,
                isDark = isDarkMode
            ) {
                AnimatedContent(
                    targetState = screen,
                    label = "screen_transition",
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 20 }) togetherWith
                            (fadeOut(tween(200)) + slideOutVertically(tween(300)) { -it / 20 })
                    }
                ) { targetScreen ->
                    when (targetScreen) {
                        Screen.LANDING -> {
                            LandingScreen(
                                onContinue = {
                                    if (isOnboardingReplay) {
                                        isOnboardingReplay = false
                                        currentScreen = Screen.SETTINGS
                                    } else {
                                        currentScreen = Screen.EXECUTION
                                    }
                                },
                                daexPreferences = daexPreferences,
                                viewModel = viewModel,
                                modelManager = modelManager,
                                isReplay = isOnboardingReplay
                            )
                        }
                        Screen.EXECUTION -> {
                            ExecutionScreen(
                                viewModel = viewModel,
                                modelManager = modelManager,
                                onBack = {
                                    viewModel.disconnect()
                                    currentScreen = Screen.LANDING
                                },
                                onOpenGallery = { currentScreen = Screen.GALLERY },
                                onOpenSettings = { currentScreen = Screen.SETTINGS }
                            )
                        }
                        Screen.GALLERY -> {
                            androidx.activity.compose.BackHandler {
                                currentScreen = Screen.EXECUTION
                            }
                            GalleryScreen(
                                viewModel = viewModel,
                                modelManager = modelManager,
                                onBack = { currentScreen = Screen.EXECUTION }
                            )
                        }
                        Screen.SETTINGS -> {
                            androidx.activity.compose.BackHandler {
                                currentScreen = Screen.EXECUTION
                            }
                            SettingsScreen(
                                viewModel = viewModel,
                                modelManager = modelManager,
                                onBack = { currentScreen = Screen.EXECUTION },
                                onOpenGallery = { currentScreen = Screen.GALLERY },
                                onReplayOnboarding = {
                                    isOnboardingReplay = true
                                    currentScreen = Screen.LANDING
                                }
                            )
                        }
                    }
                }

                pendingCrashFile?.let { crashFile ->
                    CrashReportModal(
                        crashFile = crashFile,
                        onDismiss = { pendingCrashFile = null }
                    )
                }

                ChangelogModal(
                    visible = autoChangelogVisible,
                    sinceVersion = changelogSinceVersion,
                    onClose = { autoChangelogVisible = false }
                )
            }
        }
    }
}
