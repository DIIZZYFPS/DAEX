package com.daex.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daex.android.ui.theme.DaexTheme

/** Generic confirmation modal for destructive actions, styled consistently with the app's other Dialog-based modals. */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String = "CONFIRM",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DaexTheme.colors.surface)
                    .border(0.5.dp, DaexTheme.colors.error.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = title,
                        style = DaexTheme.typography.h2.copy(
                            color = DaexTheme.colors.error,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = message,
                        style = DaexTheme.typography.body2.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.8f)
                        )
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "CANCEL",
                            style = DaexTheme.typography.body2.copy(
                                color = DaexTheme.colors.onSurface.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .clickable { onDismiss() }
                                .padding(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = confirmLabel,
                            style = DaexTheme.typography.body2.copy(color = DaexTheme.colors.error),
                            modifier = Modifier
                                .clickable {
                                    onConfirm()
                                    onDismiss()
                                }
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
