package com.daex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.services.DeviceService
import com.daex.android.services.ModelManager
import com.daex.android.ui.theme.DaexAppTheme
import com.daex.android.ui.ExecutionScreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.daex.android.ui.LandingScreen

enum class Screen {
    LANDING, EXECUTION
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val deviceService = DeviceService(this)
        val llamaService = com.daex.android.services.LlamaServiceImpl(this)
        val modelManager = ModelManager(this)

        setContent {
            val viewModel = remember { 
                DaexInferenceViewModel(
                    llamaService = llamaService,
                    modelManager = modelManager,
                    deviceService = deviceService
                ) 
            }
            var currentScreen by remember { mutableStateOf(Screen.LANDING) }

            DaexAppTheme {
                when (currentScreen) {
                    Screen.LANDING -> {
                        LandingScreen(
                            onContinue = { currentScreen = Screen.EXECUTION }
                        )
                    }
                    Screen.EXECUTION -> {
                        ExecutionScreen(
                            viewModel = viewModel,
                            modelManager = modelManager,
                            onBack = { 
                                viewModel.disconnect()
                                currentScreen = Screen.LANDING 
                            }
                        )
                    }
                }
            }
        }
    }
}
