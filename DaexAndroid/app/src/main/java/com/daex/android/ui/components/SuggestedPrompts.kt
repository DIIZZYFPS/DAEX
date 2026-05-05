package com.daex.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.ui.theme.DaexTheme

@Composable
fun SuggestedPrompts(
    onSelectPrompt: (String) -> Unit
) {
    val prompts = listOf(
        "Explain quantum entanglement simply",
        "Write a haiku about midnight code",
        "Plan a 3-day trip to Lisbon"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // DAEX Logo
        DaexLogo(size = 80.dp, ambient = true)

        Spacer(modifier = Modifier.height(16.dp))

        // Welcome Text
        BasicText(
            text = "Welcome to D A E X",
            style = DaexTheme.typography.h2.copy(
                color = DaexTheme.colors.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        BasicText(
            text = "Icarus is ready. Execute with precision.",
            style = DaexTheme.typography.body1.copy(
                color = DaexTheme.colors.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Prompt Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DaexTheme.colors.onSurface.copy(alpha = 0.03f))
                        .border(
                            width = 0.5.dp,
                            color = DaexTheme.colors.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectPrompt(prompt) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    BasicText(
                        text = prompt,
                        style = DaexTheme.typography.body1.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}
