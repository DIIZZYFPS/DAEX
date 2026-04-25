package com.daex.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.services.Message
import com.daex.android.ui.theme.DaexTheme
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MessageLine(
    message: Message,
    isLastModel: Boolean = false,
    isGenerating: Boolean = false,
    tokenSpeed: Double = 0.0
) {
    val isUser = message.role == "user"

    if (isUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 0.dp))
                    .background(Color(0xFF00FFFF).copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF00FFFF).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 0.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicText(
                    text = message.content,
                    style = DaexTheme.typography.body1.copy(
                        color = Color(0xFF00FFFF),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Label
            BasicText(
                text = "ICARUS",
                style = DaexTheme.typography.mono.copy(
                    color = Color(0xFF00FFFF).copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (message.content.isEmpty()) {
                BasicText(
                    text = "▊",
                    style = DaexTheme.typography.body1.copy(
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 14.sp
                    )
                )
            } else {
                Markdown(
                    content = message.content,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isLastModel && tokenSpeed > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.06f)))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BasicText(
                        text = "⚡ $tokenSpeed tok/s",
                        style = DaexTheme.typography.mono.copy(
                            color = if (isGenerating) Color(0xFFA855F7) else Color.White.copy(alpha = 0.35f),
                            fontSize = 10.sp
                        )
                    )
                    if (isGenerating) {
                        BasicText(
                            text = "generating...",
                            style = DaexTheme.typography.mono.copy(
                                color = Color(0xFFA855F7).copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
