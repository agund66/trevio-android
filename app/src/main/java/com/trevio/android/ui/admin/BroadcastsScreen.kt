package com.trevio.android.ui.admin

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.*
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
    val currentUserId = state.currentUserId

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
                Text(stringResource(R.string.broadcasts_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.broadcasts_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { viewModel.showForm() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.broadcasts_new))
            }
        }

        val active = state.broadcasts.count { it.active }
        val inactive = state.broadcasts.count { !it.active }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BroadcastStatCard(stringResource(R.string.broadcasts_total), state.broadcasts.size.toString(), Modifier.weight(1f))
            BroadcastStatCard(stringResource(R.string.broadcasts_active), active.toString(), Modifier.weight(1f))
            BroadcastStatCard(stringResource(R.string.broadcasts_stopped), inactive.toString(), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.error != null) {
            Text(
                stringResource(state.error!!),
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
                items(state.broadcasts, key = { it.id }) { broadcast ->
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
                                Text(stringResource(R.string.broadcasts_no_broadcasts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val isDark = isSystemInDarkTheme()
    val priorityColor = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> if (isDark) BalanceNegativeDark else BalanceNegative
        BroadcastPriority.MAINTENANCE -> if (isDark) TrevioWarningDarkTheme else TrevioWarning
        BroadcastPriority.INFO -> if (isDark) TrevioSecondaryDarkTheme else TrevioSecondary
    }
    val priorityLabel = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> stringResource(R.string.broadcasts_priority_critical)
        BroadcastPriority.MAINTENANCE -> stringResource(R.string.broadcasts_priority_maintenance)
        BroadcastPriority.INFO -> stringResource(R.string.broadcasts_priority_info)
    }
    val targetLabel = when (broadcast.targetType) {
        BroadcastTargetType.ALL -> stringResource(R.string.broadcasts_target_all)
        BroadcastTargetType.ALL_EXCEPT_BLOCKED -> stringResource(R.string.broadcasts_target_except_blocked)
        BroadcastTargetType.SPECIFIC -> stringResource(R.string.broadcasts_target_specific) + " (${broadcast.targetUids.size})"
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
                    Surface(color = if (isSystemInDarkTheme()) BalancePositiveDark else BalancePositive.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                        Text(stringResource(R.string.broadcasts_active), style = MaterialTheme.typography.labelSmall, color = if (isSystemInDarkTheme()) BalancePositiveDark else BalancePositive, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp)) {
                        Text(stringResource(R.string.broadcasts_stopped), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
                    Text(stringResource(R.string.broadcasts_read_count, readCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.broadcasts_created_by, broadcast.createdByName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (broadcast.active) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onStop,
                    enabled = !actionLoading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isSystemInDarkTheme()) BalanceNegativeDark else BalanceNegative)
                ) {
                    if (actionLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.broadcasts_stop))
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
            title = stringResource(R.string.broadcasts_create_title),
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
                        stringResource(state.formError!!),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = state.formTitle,
                onValueChange = { viewModel.updateFormTitle(it) },
                label = { Text(stringResource(R.string.broadcasts_title_label)) },
                placeholder = { Text(stringResource(R.string.broadcasts_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Text(stringResource(R.string.broadcasts_priority), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BroadcastPriority.entries.forEach { p ->
                    val label = when (p) {
                        BroadcastPriority.CRITICAL -> stringResource(R.string.broadcasts_priority_critical)
                        BroadcastPriority.MAINTENANCE -> stringResource(R.string.broadcasts_priority_maintenance)
                        BroadcastPriority.INFO -> stringResource(R.string.broadcasts_priority_info)
                    }
                    val color = when (p) {
                        BroadcastPriority.CRITICAL -> if (isSystemInDarkTheme()) BalanceNegativeDark else BalanceNegative
                        BroadcastPriority.MAINTENANCE -> if (isSystemInDarkTheme()) TrevioWarningDarkTheme else TrevioWarning
                        BroadcastPriority.INFO -> if (isSystemInDarkTheme()) TrevioSecondaryDarkTheme else TrevioSecondary
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

            Text(stringResource(R.string.broadcasts_target), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Column {
                BroadcastTargetType.entries.forEach { t ->
                    val label = when (t) {
                        BroadcastTargetType.ALL -> stringResource(R.string.broadcasts_target_all)
                        BroadcastTargetType.ALL_EXCEPT_BLOCKED -> stringResource(R.string.broadcasts_target_except_blocked)
                        BroadcastTargetType.SPECIFIC -> stringResource(R.string.broadcasts_target_specific_users)
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
                Text(stringResource(R.string.broadcasts_select_users), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
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
                        stringResource(R.string.broadcasts_selected, state.formTargetUids.size),
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

            // Use interactionSource for date picker fields — .clickable on a
            // disabled OutlinedTextField is unreliable.
            val startPickerInteraction = remember { MutableInteractionSource() }
            val endPickerInteraction = remember { MutableInteractionSource() }
            LaunchedEffect(startPickerInteraction) {
                startPickerInteraction.interactions.collect { i ->
                    if (i is PressInteraction.Press) showStartPicker = true
                }
            }
            LaunchedEffect(endPickerInteraction) {
                endPickerInteraction.interactions.collect { i ->
                    if (i is PressInteraction.Press) showEndPicker = true
                }
            }

            Text(stringResource(R.string.broadcasts_schedule), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.formStartAt?.let { formatDate(it, true) } ?: "",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.broadcasts_start_date_time)) },
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    interactionSource = startPickerInteraction,
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
                    label = { Text(stringResource(R.string.broadcasts_end_optional)) },
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    interactionSource = endPickerInteraction,
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
                stringResource(R.string.broadcasts_end_empty_hint),
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
                        ) { Text(stringResource(R.string.common_done)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.common_cancel)) }
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
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartTimePicker = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                    title = { Text(stringResource(R.string.broadcasts_select_time)) },
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
                        ) { Text(stringResource(R.string.common_next)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.common_cancel)) }
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
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndTimePicker = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                    title = { Text(stringResource(R.string.broadcasts_select_time)) },
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
                label = { Text(stringResource(R.string.broadcasts_content)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(12.dp)
            )

            if (state.formHtmlContent.isNotBlank()) {
                Text(stringResource(R.string.broadcasts_preview), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
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
                TextButton(onClick = { viewModel.hideForm() }) { Text(stringResource(R.string.common_cancel)) }
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
                    Text(stringResource(R.string.broadcasts_send_broadcast))
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

    val isDarkDetail = isSystemInDarkTheme()
    val priorityColor = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> if (isDarkDetail) BalanceNegativeDark else BalanceNegative
        BroadcastPriority.MAINTENANCE -> if (isDarkDetail) TrevioWarningDarkTheme else TrevioWarning
        BroadcastPriority.INFO -> if (isDarkDetail) TrevioSecondaryDarkTheme else TrevioSecondary
    }
    val priorityLabel = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> stringResource(R.string.broadcasts_priority_critical)
        BroadcastPriority.MAINTENANCE -> stringResource(R.string.broadcasts_priority_maintenance)
        BroadcastPriority.INFO -> stringResource(R.string.broadcasts_priority_info)
    }
    val targetLabel = when (broadcast.targetType) {
        BroadcastTargetType.ALL -> stringResource(R.string.broadcasts_target_all)
        BroadcastTargetType.ALL_EXCEPT_BLOCKED -> stringResource(R.string.broadcasts_target_except_blocked)
        BroadcastTargetType.SPECIFIC -> stringResource(R.string.broadcasts_target_specific) + " (${broadcast.targetUids.size})"
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
            title = stringResource(R.string.broadcasts_detail_title),
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
                            val activeColor = if (isDarkDetail) BalancePositiveDark else BalancePositive
                            Surface(color = activeColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                Text(stringResource(R.string.broadcasts_active), style = MaterialTheme.typography.labelSmall, color = activeColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        } else {
                            Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp)) {
                                Text(stringResource(R.string.broadcasts_stopped), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(broadcast.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.broadcasts_start_label, formatDate(broadcast.startAt, true)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (broadcast.endAt != null) {
                        Text(
                            stringResource(R.string.broadcasts_end_label, formatDate(broadcast.endAt, true)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        stringResource(R.string.broadcasts_created_by, broadcast.createdByName),
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
                    BroadcastStatCard(stringResource(R.string.broadcasts_target), targetUsers.size.toString(), Modifier.weight(1f))
                    BroadcastStatCard(stringResource(R.string.broadcasts_read), readCount.toString(), Modifier.weight(1f))
                    BroadcastStatCard(stringResource(R.string.broadcasts_unread), unreadCount.toString(), Modifier.weight(1f))
                }
            }

            // User read status list
            item {
                Text(
                    stringResource(R.string.broadcasts_user_read_status),
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
                            val readColor = if (isDarkDetail) BalancePositiveDark else BalancePositive
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = readColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (readAt != null && readAt > 0) formatDate(readAt, true) else stringResource(R.string.broadcasts_read),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = readColor
                                )
                            }
                        } else {
                            val pendingColor = if (isDarkDetail) TrevioWarningDarkTheme else TrevioWarning
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = pendingColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.broadcasts_pending),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pendingColor
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
                            Text(stringResource(R.string.broadcasts_no_target_users), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        }
    }
}
