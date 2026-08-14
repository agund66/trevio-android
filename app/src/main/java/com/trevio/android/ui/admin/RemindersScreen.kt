package com.trevio.android.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val config = state.config
    var defaultTime by remember(config.defaultLocalTime) { mutableStateOf(config.defaultLocalTime) }
    var showAddOverrideDialog by remember { mutableStateOf(false) }
    var newOverrideTimezone by remember { mutableStateOf("") }
    var newOverrideTime by remember { mutableStateOf("20:00") }
    var featuredTitle by remember(config.featuredMessage) { mutableStateOf(config.featuredMessage?.title ?: "") }
    var featuredBody by remember(config.featuredMessage) { mutableStateOf(config.featuredMessage?.body ?: "") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Kill Switch ──────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Daily Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Enable or disable daily reminders for all users",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { viewModel.setEnabled(it) },
                        enabled = !state.isSaving
                    )
                }
            }

            // ─── Default Time ─────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Default Evening Time",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Used when no timezone-specific override exists (HH:mm format)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = defaultTime,
                        onValueChange = { defaultTime = it },
                        label = { Text("Time (HH:mm)") },
                        placeholder = { Text("20:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ─── Timezone Overrides ───────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Timezone Overrides",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Set different evening times for specific timezones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showAddOverrideDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add override")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (config.timezoneOverrides.isEmpty()) {
                        Text(
                            "No overrides set. All users use the default time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        config.timezoneOverrides.forEach { (timezone, time) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(timezone, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        time,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.removeTimezoneOverride(timezone) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Featured Message ─────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Featured Message (Optional)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Overrides all client-side messages for a specific period",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = featuredTitle,
                        onValueChange = { featuredTitle = it },
                        label = { Text("Title (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = featuredBody,
                        onValueChange = { featuredBody = it },
                        label = { Text("Body") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val now = System.currentTimeMillis()
                                val dayMs = 24 * 60 * 60 * 1000L
                                viewModel.setFeaturedMessage(
                                    title = featuredTitle.ifBlank { null },
                                    body = featuredBody,
                                    startAt = now,
                                    endAt = now + dayMs
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Set 24h from now") }
                        OutlinedButton(
                            onClick = {
                                featuredTitle = ""
                                featuredBody = ""
                                viewModel.clearFeaturedMessage()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear") }
                    }
                }
            }

            // ─── Save Button ──────────────────────────────────────
            Button(
                onClick = {
                    viewModel.setDefaultTime(defaultTime)
                    viewModel.saveConfig()
                },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Configuration")
                }
            }
        }
    }

    // ─── Add Override Dialog ─────────────────────────────────────
    if (showAddOverrideDialog) {
        AlertDialog(
            onDismissRequest = { showAddOverrideDialog = false },
            title = { Text("Add Timezone Override") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newOverrideTimezone,
                        onValueChange = { newOverrideTimezone = it },
                        label = { Text("Timezone (e.g. America/New_York)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newOverrideTime,
                        onValueChange = { newOverrideTime = it },
                        label = { Text("Time (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newOverrideTimezone.isNotBlank() && newOverrideTime.isNotBlank()) {
                            viewModel.addTimezoneOverride(newOverrideTimezone, newOverrideTime)
                            newOverrideTimezone = ""
                            newOverrideTime = "20:00"
                        }
                        showAddOverrideDialog = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddOverrideDialog = false }) { Text("Cancel") }
            }
        )
    }
}
