package com.daex.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.ui.components.DaexButton
import com.daex.android.ui.components.DaexLogo
import com.daex.android.ui.theme.DaexTheme

import kotlinx.coroutines.launch
import com.daex.android.services.DaexPreferences

@Composable
fun LandingScreen(
    onContinue: () -> Unit,
    daexPreferences: DaexPreferences
) {
    var animateSpacing by remember { mutableStateOf(false) }
    
    val spacing by animateFloatAsState(
        targetValue = if (animateSpacing) 16f else 0f,
        animationSpec = tween(durationMillis = 1500),
        label = "logo_spacing"
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        animateSpacing = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DaexTheme.colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top spacing
        Spacer(modifier = Modifier.height(32.dp))

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            DaexLogo(size = 100.dp, ambient = true)
            
            Spacer(modifier = Modifier.height(32.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    text = "DAEX",
                    style = DaexTheme.typography.h1.copy(
                        color = DaexTheme.colors.primary,
                        fontSize = 42.sp,
                        letterSpacing = spacing.sp
                    )
                )
                BasicText(
                    text = "DAEDALUS EXECUTION ENGINE",
                    style = DaexTheme.typography.mono.copy(
                        color = DaexTheme.colors.onSurface,
                        fontSize = 11.sp,
                        letterSpacing = 3.sp
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            BasicText(
                text = "Edge-optimized neural processing engine for on-device inference.",
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Footer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            DaexButton(
                onClick = { 
                    coroutineScope.launch {
                        daexPreferences.completeLandingPage()
                        onContinue()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    text = "START SESSION",
                    style = DaexTheme.typography.mono.copy(
                        color = DaexTheme.colors.onPrimary
                    )
                )
            }
        }
    }
}
