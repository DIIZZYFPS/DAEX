package com.daex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.daex.android.database.MyObjectBox
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.services.DaexMemory
import com.daex.android.services.DeviceService
import com.daex.android.services.ModelManager
import com.daex.android.services.DaexEmbedder
import com.daex.android.services.DaexRagImpl
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
import com.daex.android.services.DaexPreferences

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

        val boxStore = MyObjectBox.builder().androidContext(this.applicationContext).build()
        val daexMemory = DaexMemory(boxStore)
        val deviceService = DeviceService(this)
        val daexService = com.daex.android.services.DaexServiceImpl(this)
        val modelManager = ModelManager(this)
        val daexEmbedder = DaexEmbedder(this, modelManager)
        val daexCoreMemory = com.daex.android.services.DaexCoreMemoryImpl(this)
        val daexRag = DaexRagImpl(boxStore, daexEmbedder)
        val daexSkillManager = com.daex.android.services.DaexSkillManagerImpl(this)

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
            
            LaunchedEffect(Unit) {
                val completed = daexPreferences.hasCompletedLandingFlow.first()
                currentScreen = if (completed) Screen.EXECUTION else Screen.LANDING
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
                when (screen) {
                    Screen.LANDING -> {
                        LandingScreen(
                            onContinue = { 
                                currentScreen = Screen.EXECUTION 
                            },
                            daexPreferences = daexPreferences,
                            viewModel = viewModel,
                            modelManager = modelManager
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
                            onOpenGallery = { currentScreen = Screen.GALLERY }
                        )
                    }
                }
            }
        }
    }
}
