package com.trevio.android.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trevio.android.R
import com.trevio.android.util.CountryConstants

/**
 * Full-screen dialog with a search bar for picking a country from the
 * full list of ~200 supported countries.  Replaces the old DropdownMenu
 * which was impractical to scroll through.
 *
 * Countries are sorted alphabetically by name. When the dialog opens,
 * the list auto-scrolls to the currently selected country so the user
 * sees it immediately.
 *
 * @param selectedCode currently selected country code (for highlight)
 * @param onSelect callback invoked with the chosen country code
 * @param onDismiss callback invoked when the dialog is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerDialog(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    // Sort alphabetically by name (case-insensitive).
    val sorted = remember {
        CountryConstants.COUNTRY_CODES.sortedBy { it.name.lowercase() }
    }

    val filtered = remember(query, sorted) {
        if (query.isBlank()) {
            sorted
        } else {
            val lower = query.lowercase()
            sorted.filter { c ->
                c.code.lowercase().contains(lower) ||
                    c.dialCode.contains(query) ||
                    c.name.lowercase().contains(lower)
            }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll so the selected country is at the 2nd position
    // (one item visible above it, so the user can see the list continues).
    LaunchedEffect(sorted) {
        if (query.isBlank()) {
            val selectedIndex = sorted.indexOfFirst { it.code == selectedCode }
            if (selectedIndex > 0) {
                listState.scrollToItem(selectedIndex - 1)
            } else if (selectedIndex == 0) {
                listState.scrollToItem(0)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile_select_country), fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close)
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_country)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.code }) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(country.code)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = country.flag,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(country.nameResId),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (country.code == selectedCode) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (country.code == selectedCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${country.dialCode} · ${country.code}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (country.code == selectedCode) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
