package com.trevio.android.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.UserService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authService: AuthService,
    private val userService: UserService
) : ViewModel() {

    data class ProfileState(
        val user: User? = null,
        val isLoading: Boolean = true,
        val isEditing: Boolean = false,
        val isSaving: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state

    init { loadUser() }

    private fun loadUser() {
        viewModelScope.launch {
            val user = authService.getCurrentUser()
            _state.value = ProfileState(user = user, isLoading = false)
        }
    }

    fun startEditing() {
        _state.value = _state.value.copy(isEditing = true, error = null)
    }

    fun cancelEditing() {
        _state.value = _state.value.copy(isEditing = false, error = null)
    }

    fun saveProfile(displayName: String, currency: String, upiId: String) {
        val currentUser = _state.value.user ?: return
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val updated = currentUser.copy(
                displayName = displayName,
                defaultCurrency = currency,
                upiId = upiId
            )
            userService.updateUser(updated)
                .onSuccess {
                    _state.value = ProfileState(user = updated, isLoading = false, isEditing = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSaving = false, error = e.message)
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isEditing && state.user != null) {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            com.trevio.android.core.designsystem.components.LoadingIndicator(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val user = state.user ?: return@Scaffold

        if (state.isEditing) {
            EditProfileContent(
                user = user,
                isSaving = state.isSaving,
                error = state.error,
                onSave = { name, currency, upi -> viewModel.saveProfile(name, currency, upi) },
                onCancel = { viewModel.cancelEditing() },
                modifier = Modifier.padding(padding)
            )
        } else {
            ViewProfileContent(
                user = user,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ViewProfileContent(user: User, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MemberAvatar(name = user.displayName, photoURL = user.photoURL, size = 96)
        Spacer(modifier = Modifier.height(16.dp))
        Text(user.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileInfoRow("Username", "@${user.username}")
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                ProfileInfoRow("Email", user.email)
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                ProfileInfoRow("Currency", user.defaultCurrency)
                if (user.upiId.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    ProfileInfoRow("UPI ID", user.upiId)
                }
            }
        }
    }
}

@Composable
private fun EditProfileContent(
    user: User,
    isSaving: Boolean,
    error: String?,
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var currency by remember { mutableStateOf(user.defaultCurrency) }
    var upiId by remember { mutableStateOf(user.upiId) }

    val currencies = listOf("INR" to "₹ INR", "USD" to "\$ USD", "EUR" to "€ EUR", "GBP" to "£ GBP", "AED" to "AED", "SGD" to "S\$ SGD", "AUD" to "A\$ AUD", "CAD" to "C\$ CAD", "JPY" to "¥ JPY")

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text("Currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            currencies.take(5).forEach { (code, label) ->
                FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(label) })
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            currencies.drop(5).forEach { (code, label) ->
                FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(label) })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = upiId,
            onValueChange = { upiId = it },
            label = { Text("UPI ID (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Cancel") }
            Button(
                onClick = { onSave(displayName, currency, upiId) },
                enabled = !isSaving,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                } else {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
