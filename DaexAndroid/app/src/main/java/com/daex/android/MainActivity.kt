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
    LANDING, EXECUTION, GALLERY
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val boxStore = MyObjectBox.builder().androidContext(this.applicationContext).build()
        val daexMemory = DaexMemory(boxStore)
        val deviceService = DeviceService(this)
        val llamaService = com.daex.android.services.LlamaServiceImpl(this)
        val modelManager = ModelManager(this)
        val daexEmbedder = DaexEmbedder(this, modelManager)
        val daexRag = DaexRagImpl(daexMemory, daexEmbedder)

        setContent {
            val daexPreferences = remember { DaexPreferences(this@MainActivity) }
            val viewModel = remember { 
                DaexInferenceViewModel(
                    llamaService = llamaService,
                    modelManager = modelManager,
                    deviceService = deviceService,
                    daexMemory = daexMemory,
                    preferences = daexPreferences,
                    daexRag = daexRag
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
                            viewModel = viewModel
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
                            onOpenGallery = { currentScreen = Screen.GALLERY }
                        )
                    }
                    Screen.GALLERY -> {
                        GalleryScreen(
                            viewModel = viewModel,
                            modelManager = modelManager,
                            onBack = { currentScreen = Screen.EXECUTION }
                        )
                    }
                }
            }
        }
    }
}
