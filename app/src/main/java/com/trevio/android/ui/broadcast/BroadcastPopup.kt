package com.trevio.android.ui.broadcast

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.trevio.android.domain.model.BroadcastPriority
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

@Composable
fun BroadcastPopup(
    viewModel: BroadcastPopupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadUnreadBroadcasts()
    }

    val broadcast = remember(state) {
        val s = state
        if (s.currentIndex >= s.unreadBroadcasts.size) null
        else {
            val b = s.unreadBroadcasts[s.currentIndex]
            if (b.priority == BroadcastPriority.INFO && b.id in s.dismissedInfoIds) {
                s.unreadBroadcasts.drop(s.currentIndex + 1).firstOrNull { it.id !in s.dismissedInfoIds }
            } else {
                b
            }
        }
    }

    if (broadcast != null) {
        BroadcastDialog(
            broadcast = broadcast,
            isAcknowledging = state.isAcknowledging,
            onAcknowledge = { viewModel.acknowledge(broadcast.id) },
            onDismiss = {
                if (broadcast.priority != BroadcastPriority.CRITICAL) {
                    viewModel.dismissInfo(broadcast.id)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BroadcastDialog(
    broadcast: com.trevio.android.domain.model.BroadcastMessage,
    isAcknowledging: Boolean,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val priorityColor = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> if (isDark) Color(0xFFF87171) else Color(0xFFEF4444)
        BroadcastPriority.MAINTENANCE -> if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
        BroadcastPriority.INFO -> if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6)
    }
    val priorityLabel = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> "Critical Alert"
        BroadcastPriority.MAINTENANCE -> "Maintenance Notice"
        BroadcastPriority.INFO -> "Information"
    }
    val buttonText = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> "Understood"
        else -> "OK, Got it"
    }
    val isCritical = broadcast.priority == BroadcastPriority.CRITICAL

    val sanitizedHtml = remember(broadcast.htmlContent) {
        Jsoup.clean(broadcast.htmlContent, Safelist.relaxed())
    }

    AlertDialog(
        onDismissRequest = {
            if (!isCritical) onDismiss()
        },
        confirmButton = {
            Button(
                onClick = onAcknowledge,
                enabled = !isAcknowledging,
                colors = ButtonDefaults.buttonColors(containerColor = priorityColor)
            ) {
                if (isAcknowledging) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(buttonText)
                }
            }
        },
        dismissButton = {
            if (!isCritical) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = priorityColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        priorityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    broadcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = false
                            loadDataWithBaseURL(null, sanitizedHtml, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, sanitizedHtml, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                )
            }
        },
        modifier = Modifier.border(2.dp, priorityColor, RoundedCornerShape(16.dp))
    )
}
