package com.trevio.android.ui.trip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.TripItineraryItem
import com.trevio.android.domain.model.TripLocation
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.repository.TripService
import com.trevio.android.util.rememberCurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripViewModel @Inject constructor(
    private val tripService: TripService,
    private val settlementService: SettlementService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class TripState(
        val isLoading: Boolean = true,
        val destination: String = "",
        val startDate: Long = 0,
        val endDate: Long = 0,
        val itinerary: List<TripItineraryItem> = emptyList(),
        val locations: List<TripLocation> = emptyList(),
        val members: List<Member> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state

    init { loadData() }

    fun loadData() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val tripResult = tripService.getTripData(groupId)
            val membersResult = settlementService.getGroupBalances(groupId)
            if (tripResult.isFailure && membersResult.isFailure) {
                _state.value = _state.value.copy(isLoading = false, error = tripResult.exceptionOrNull()?.message ?: "Failed to load trip data")
                return@launch
            }
            val tripData = tripResult.getOrNull()
            val members = membersResult.getOrDefault(emptyList())
            _state.value = TripState(
                isLoading = false,
                destination = tripData?.destination ?: "",
                startDate = tripData?.startDate ?: 0,
                endDate = tripData?.endDate ?: 0,
                itinerary = tripData?.itinerary ?: emptyList(),
                locations = tripData?.locations ?: emptyList(),
                members = members
            )
        }
    }

    fun addItineraryItem(item: TripItineraryItem, onSuccess: () -> Unit) {
        viewModelScope.launch {
            tripService.addItineraryItem(groupId, item)
                .onSuccess {
                    _state.value = _state.value.copy(error = null)
                    loadData()
                    onSuccess()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
        }
    }

    fun toggleComplete(item: TripItineraryItem) {
        viewModelScope.launch {
            tripService.updateItineraryItem(groupId, item.itemId, item.copy(completed = !item.completed))
                .onSuccess {
                    _state.value = _state.value.copy(error = null)
                    loadData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
        }
    }

    fun removeItineraryItem(itemId: String) {
        viewModelScope.launch {
            tripService.removeItineraryItem(groupId, itemId)
                .onSuccess {
                    _state.value = _state.value.copy(error = null)
                    loadData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
        }
    }

    fun addLocation(location: TripLocation, onSuccess: () -> Unit) {
        viewModelScope.launch {
            tripService.addLocation(groupId, location)
                .onSuccess {
                    _state.value = _state.value.copy(error = null)
                    loadData()
                    onSuccess()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
        }
    }

    fun removeLocation(locationId: String) {
        viewModelScope.launch {
            tripService.removeLocation(groupId, locationId)
                .onSuccess {
                    _state.value = _state.value.copy(error = null)
                    loadData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTab(
    groupId: String,
    viewModel: TripViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormatter = rememberCurrencyFormatter()
    var showAddItem by remember { mutableStateOf(false) }
    var showAddLocation by remember { mutableStateOf(false) }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.error != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.error!!,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.loadData() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
        // Trip Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = state.destination.ifEmpty { "Set your destination" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (state.startDate > 0 || state.endDate > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val dateFmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy", java.util.Locale.getDefault())
                        val dateText = when {
                            state.startDate > 0 && state.endDate > 0 ->
                                "${dateFmt.format(java.util.Date(state.startDate))} - ${dateFmt.format(java.util.Date(state.endDate))}"
                            state.startDate > 0 ->
                                "Starts ${dateFmt.format(java.util.Date(state.startDate))}"
                            else ->
                                "Ends ${dateFmt.format(java.util.Date(state.endDate))}"
                        }
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalEst = state.itinerary.sumOf { it.estimatedCost }
                    val completed = state.itinerary.count { it.completed }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text("Items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("${state.itinerary.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column {
                            Text("Done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("$completed/${state.itinerary.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column {
                            Text("Est. Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(currencyFormatter.formatBase(totalEst), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }

        // Itinerary Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Itinerary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { showAddItem = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item")
                }
            }
        }

        // Itinerary Items grouped by day
        val grouped = state.itinerary.groupBy { item ->
            if (item.date > 0) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = item.date }
                "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
            } else "none"
        }.toSortedMap(compareBy { key ->
            if (key == "none") Long.MAX_VALUE else {
                val parts = key.split("-")
                parts[0].toLong() * 10000 + parts[1].toLong() * 100 + parts[2].toLong()
            }
        })

        grouped.forEach { (dayKey, items) ->
            item {
                val label = if (dayKey == "none") "Unscheduled" else {
                    val parts = dayKey.split("-")
                    java.text.SimpleDateFormat("EEE, dd MMM", java.util.Locale.getDefault()).format(
                        java.util.GregorianCalendar(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()).time
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            items(items) { item ->
                ItineraryItemCard(
                    item = item,
                    currencyFormatter = currencyFormatter,
                    onToggleComplete = { viewModel.toggleComplete(item) },
                    onRemove = { viewModel.removeItineraryItem(item.itemId) }
                )
            }
        }

        if (state.itinerary.isEmpty()) {
            item {
                EmptySection(
                    icon = Icons.Default.CalendarMonth,
                    message = "No itinerary items yet. Add your first activity!"
                )
            }
        }

        // Locations Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Locations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { showAddLocation = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Location")
                }
            }
        }

        items(state.locations) { loc ->
            LocationCard(
                location = loc,
                onRemove = { viewModel.removeLocation(loc.locationId) }
            )
        }

        if (state.locations.isEmpty()) {
            item {
                EmptySection(
                    icon = Icons.Default.LocationOn,
                    message = "No locations added yet."
                )
            }
        }
    }

    // Add Item Dialog
    if (showAddItem) {
        AddItemSheet(
            onDismiss = { showAddItem = false },
            onAdd = { item ->
                viewModel.addItineraryItem(item) { showAddItem = false }
            }
        )
    }

    if (showAddLocation) {
        AddLocationSheet(
            onDismiss = { showAddLocation = false },
            onAdd = { loc ->
                viewModel.addLocation(loc) { showAddLocation = false }
            }
        )
    }
}

@Composable
private fun ItineraryItemCard(
    item: TripItineraryItem,
    currencyFormatter: com.trevio.android.util.CurrencyFormatter,
    onToggleComplete: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.completed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.location.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (item.estimatedCost > 0) {
                        Text(
                            currencyFormatter.formatBase(item.estimatedCost),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onToggleComplete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = if (item.completed) "Mark incomplete" else "Mark complete",
                        modifier = Modifier.size(16.dp),
                        tint = if (item.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    location: TripLocation,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(location.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (location.address.isNotEmpty()) {
                    Text(location.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptySection(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemSheet(
    onDismiss: () -> Unit,
    onAdd: (TripItineraryItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var estimatedCost by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Itinerary Item", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = estimatedCost,
                    onValueChange = { estimatedCost = it },
                    label = { Text("Est. Cost") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(TripItineraryItem(
                            title = title.trim(),
                            description = description.trim(),
                            location = location.trim(),
                            category = category.trim().ifEmpty { "other" },
                            estimatedCost = estimatedCost.toDoubleOrNull() ?: 0.0,
                            date = System.currentTimeMillis()
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) {
                Text("Add Item")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLocationSheet(
    onDismiss: () -> Unit,
    onAdd: (TripLocation) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Location name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(TripLocation(
                            name = name.trim(),
                            address = address.trim(),
                            latitude = latitude.toDoubleOrNull() ?: 0.0,
                            longitude = longitude.toDoubleOrNull() ?: 0.0,
                            category = category.trim().ifEmpty { "other" }
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Add Location")
            }
        }
    }
}
