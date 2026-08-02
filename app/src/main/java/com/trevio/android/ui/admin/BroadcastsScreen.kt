package com.trevio.android.ui.admin

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.domain.model.BroadcastMessage
import com.trevio.android.domain.model.BroadcastPriority
import com.trevio.android.domain.model.BroadcastTargetType
import com.trevio.android.util.rememberCurrencyFormatter
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

@Composable
fun BroadcastsScreen(
    viewModel: BroadcastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormatter = rememberCurrencyFormatter()
    val currentUserId = remember { viewModel.currentUserId }

    if (state.showForm) {
        BroadcastCreateScreen(
            state = state,
            viewModel = viewModel,
            currentUserId = currentUserId
        )
        return
    }

    if (state.selectedBroadcast != null) {
        BroadcastDetailScreen(
            state = state,
            viewModel = viewModel
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Broadcast Messages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Send HTML-formatted messages to users", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { viewModel.showForm() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New")
            }
        }

        val active = state.broadcasts.count { it.active }
        val inactive = state.broadcasts.count { !it.active }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BroadcastStatCard("Total", state.broadcasts.size.toString(), Modifier.weight(1f))
            BroadcastStatCard("Active", active.toString(), Modifier.weight(1f))
            BroadcastStatCard("Stopped", inactive.toString(), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.error != null) {
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.broadcasts) { broadcast ->
                    BroadcastRow(
                        broadcast = broadcast,
                        readCount = state.readCounts[broadcast.id] ?: 0,
                        actionLoading = state.actionLoading == broadcast.id,
                        onStop = { viewModel.stopBroadcast(broadcast.id) },
                        onClick = { viewModel.showDetail(broadcast) }
                    )
                }
                if (state.broadcasts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No broadcasts yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BroadcastStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    TrevioCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BroadcastRow(
    broadcast: BroadcastMessage,
    readCount: Int,
    actionLoading: Boolean,
    onStop: () -> Unit,
    onClick: () -> Unit
) {
    val priorityColor = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> Color(0xFFEF4444)
        BroadcastPriority.MAINTENANCE -> Color(0xFFF59E0B)
        BroadcastPriority.INFO -> Color(0xFF3B82F6)
    }
    val priorityLabel = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> "Critical"
        BroadcastPriority.MAINTENANCE -> "Maintenance"
        BroadcastPriority.INFO -> "Info"
    }
    val targetLabel = when (broadcast.targetType) {
        BroadcastTargetType.ALL -> "All Users"
        BroadcastTargetType.ALL_EXCEPT_BLOCKED -> "All (except blocked)"
        BroadcastTargetType.SPECIFIC -> "Specific (${broadcast.targetUids.size})"
    }

    TrevioCard(onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = priorityColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        priorityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        targetLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (broadcast.active) {
                    Surface(color = Color(0xFF22C55E).copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                        Text("Active", style = MaterialTheme.typography.labelSmall, color = Color(0xFF22C55E), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp)) {
                        Text("Stopped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(broadcast.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("$readCount reads", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("by ${broadcast.createdByName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (broadcast.active) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onStop,
                    enabled = !actionLoading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    if (actionLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BroadcastCreateScreen(
    state: BroadcastViewModel.BroadcastState,
    viewModel: BroadcastViewModel,
    currentUserId: String?
) {
    val currencyFormatter = rememberCurrencyFormatter()
    val formatDate = currencyFormatter.formatDate
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TrevioHeader(
            title = "Create Broadcast",
            onBack = { viewModel.hideForm() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.formError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.formError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = state.formTitle,
                onValueChange = { viewModel.updateFormTitle(it) },
                label = { Text("Title") },
                placeholder = { Text("Broadcast title...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Text("Priority", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BroadcastPriority.entries.forEach { p ->
                    val label = when (p) {
                        BroadcastPriority.CRITICAL -> "Critical"
                        BroadcastPriority.MAINTENANCE -> "Maintenance"
                        BroadcastPriority.INFO -> "Info"
                    }
                    val color = when (p) {
                        BroadcastPriority.CRITICAL -> Color(0xFFEF4444)
                        BroadcastPriority.MAINTENANCE -> Color(0xFFF59E0B)
                        BroadcastPriority.INFO -> Color(0xFF3B82F6)
                    }
                    FilterChip(
                        selected = state.formPriority == p,
                        onClick = { viewModel.updateFormPriority(p) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.12f),
                            selectedLabelColor = color
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Target Audience", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Column {
                BroadcastTargetType.entries.forEach { t ->
                    val label = when (t) {
                        BroadcastTargetType.ALL -> "All Users"
                        BroadcastTargetType.ALL_EXCEPT_BLOCKED -> "All (except blocked)"
                        BroadcastTargetType.SPECIFIC -> "Specific Users"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateFormTargetType(t) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = state.formTargetType == t, onClick = { viewModel.updateFormTargetType(t) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (state.formTargetType == BroadcastTargetType.SPECIFIC) {
                Text("Select users:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    state.allUsers.filter { it.uid != currentUserId }.forEach { u ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleTargetUser(u.uid) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = u.uid in state.formTargetUids,
                                onCheckedChange = { viewModel.toggleTargetUser(u.uid) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${u.displayName} (${u.email})",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (state.formTargetUids.isNotEmpty()) {
                    Text(
                        "${state.formTargetUids.size} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Start date/time picker
            var showStartPicker by remember { mutableStateOf(false) }
            var showStartTimePicker by remember { mutableStateOf(false) }
            var showEndPicker by remember { mutableStateOf(false) }
            var showEndTimePicker by remember { mutableStateOf(false) }
            var pendingStartDateMillis by remember { mutableStateOf<Long?>(null) }
            var pendingEndDateMillis by remember { mutableStateOf<Long?>(null) }

            Text("Schedule", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.formStartAt?.let { formatDate(it, true) } ?: "",
                    onValueChange = {},
                    label = { Text("Start Date & Time") },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showStartPicker = true },
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = state.formEndAt?.let { formatDate(it, true) } ?: "",
                    onValueChange = {},
                    label = { Text("End (optional)") },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEndPicker = true },
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Text(
                "Leave end empty to run until manually stopped",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // Start DatePicker dialog
            if (showStartPicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = state.formStartAt ?: System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showStartPicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingStartDateMillis = datePickerState.selectedDateMillis
                                showStartPicker = false
                                showStartTimePicker = true
                            }
                        ) { Text("Next") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Start TimePicker dialog
            if (showStartTimePicker) {
                val timePickerState = rememberTimePickerState(
                    initialHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                    initialMinute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
                )
                AlertDialog(
                    onDismissRequest = { showStartTimePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingStartDateMillis?.let { dateMillis ->
                                    val cal = java.util.Calendar.getInstance()
                                    cal.timeInMillis = dateMillis
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                    viewModel.updateFormStartAt(cal.timeInMillis)
                                }
                                showStartTimePicker = false
                            }
                        ) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
                    },
                    title = { Text("Select Time") },
                    text = {
                        Box(modifier = Modifier.padding(16.dp)) {
                            TimePicker(state = timePickerState)
                        }
                    }
                )
            }

            // End DatePicker dialog
            if (showEndPicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = state.formEndAt ?: state.formStartAt ?: System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showEndPicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingEndDateMillis = datePickerState.selectedDateMillis
                                showEndPicker = false
                                showEndTimePicker = true
                            }
                        ) { Text("Next") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // End TimePicker dialog
            if (showEndTimePicker) {
                val timePickerState = rememberTimePickerState(
                    initialHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                    initialMinute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
                )
                AlertDialog(
                    onDismissRequest = { showEndTimePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingEndDateMillis?.let { dateMillis ->
                                    val cal = java.util.Calendar.getInstance()
                                    cal.timeInMillis = dateMillis
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                    viewModel.updateFormEndAt(cal.timeInMillis)
                                }
                                showEndTimePicker = false
                            }
                        ) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
                    },
                    title = { Text("Select Time") },
                    text = {
                        Box(modifier = Modifier.padding(16.dp)) {
                            TimePicker(state = timePickerState)
                        }
                    }
                )
            }

            OutlinedTextField(
                value = state.formHtmlContent,
                onValueChange = { viewModel.updateFormHtmlContent(it) },
                label = { Text("HTML Content") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(12.dp)
            )

            if (state.formHtmlContent.isNotBlank()) {
                Text("Preview:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                AndroidHtmlPreview(
                    html = state.formHtmlContent,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.hideForm() }) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.submitBroadcast() },
                    enabled = !state.isSubmitting,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send Broadcast")
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun AndroidHtmlPreview(html: String, modifier: Modifier = Modifier) {
    val sanitized = remember(html) {
        Jsoup.clean(html, Safelist.relaxed())
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                loadDataWithBaseURL(null, sanitized, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, sanitized, "text/html", "UTF-8", null)
        },
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BroadcastDetailScreen(
    state: BroadcastViewModel.BroadcastState,
    viewModel: BroadcastViewModel
) {
    val broadcast = state.selectedBroadcast ?: return
    val currencyFormatter = rememberCurrencyFormatter()
    val formatDate = currencyFormatter.formatDate

    val priorityColor = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> Color(0xFFEF4444)
        BroadcastPriority.MAINTENANCE -> Color(0xFFF59E0B)
        BroadcastPriority.INFO -> Color(0xFF3B82F6)
    }
    val priorityLabel = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> "Critical"
        BroadcastPriority.MAINTENANCE -> "Maintenance"
        BroadcastPriority.INFO -> "Info"
    }
    val targetLabel = when (broadcast.targetType) {
        BroadcastTargetType.ALL -> "All Users"
        BroadcastTargetType.ALL_EXCEPT_BLOCKED -> "All (except blocked)"
        BroadcastTargetType.SPECIFIC -> "Specific (${broadcast.targetUids.size})"
    }

    val readUids = remember(state.detailReads) { state.detailReads.map { it.uid }.toSet() }
    val readMap = remember(state.detailReads) { state.detailReads.associate { it.uid to it.readAt } }

    val targetUsers = state.detailAllUsers.filter { u ->
        if (u.uid == broadcast.createdBy) return@filter false
        when (broadcast.targetType) {
            BroadcastTargetType.ALL -> true
            BroadcastTargetType.ALL_EXCEPT_BLOCKED -> !u.blocked
            BroadcastTargetType.SPECIFIC -> broadcast.targetUids.contains(u.uid)
        }
    }
    val readCount = targetUsers.count { readUids.contains(it.uid) }
    val unreadCount = targetUsers.size - readCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TrevioHeader(
            title = "Broadcast Details",
            onBack = { viewModel.hideDetail() }
        )

        if (state.detailLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Broadcast info card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(priorityColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(1.dp, priorityColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(color = priorityColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                priorityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                targetLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (broadcast.active) {
                            Surface(color = Color(0xFF22C55E).copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                Text("Active", style = MaterialTheme.typography.labelSmall, color = Color(0xFF22C55E), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        } else {
                            Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp)) {
                                Text("Stopped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(broadcast.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Start: ${formatDate(broadcast.startAt, true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (broadcast.endAt != null) {
                        Text(
                            "End: ${formatDate(broadcast.endAt, true)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "by ${broadcast.createdByName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (broadcast.htmlContent.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AndroidHtmlPreview(
                            html = broadcast.htmlContent,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                        )
                    }
                }
            }

            // Stats row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BroadcastStatCard("Target", targetUsers.size.toString(), Modifier.weight(1f))
                    BroadcastStatCard("Read", readCount.toString(), Modifier.weight(1f))
                    BroadcastStatCard("Unread", unreadCount.toString(), Modifier.weight(1f))
                }
            }

            // User read status list
            item {
                Text(
                    "User Read Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(targetUsers) { user ->
                val hasRead = readUids.contains(user.uid)
                val readAt = readMap[user.uid]
                TrevioCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    user.displayName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                user.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                user.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (hasRead) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF22C55E)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (readAt != null && readAt > 0) formatDate(readAt, true) else "Read",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF22C55E)
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFF59E0B)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Pending",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }
                }
            }

            if (targetUsers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No target users found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        }
    }
}
