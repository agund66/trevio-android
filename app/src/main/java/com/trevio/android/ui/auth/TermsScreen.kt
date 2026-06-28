package com.trevio.android.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.repository.UserService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TermsViewModel @Inject constructor(
    private val userService: UserService
) : ViewModel() {

    sealed class TermsState {
        data object Idle : TermsState()
        data object Loading : TermsState()
        data object Accepted : TermsState()
        data class Error(val message: String) : TermsState()
    }

    private val _state = MutableStateFlow<TermsState>(TermsState.Idle)
    val state: StateFlow<TermsState> = _state

    fun acceptTnC() {
        _state.value = TermsState.Loading
        viewModelScope.launch {
            userService.acceptTnC()
                .onSuccess { _state.value = TermsState.Accepted }
                .onFailure { _state.value = TermsState.Error(it.message ?: "Failed to accept") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: TermsViewModel = hiltViewModel()
) {
    var checked by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is TermsViewModel.TermsState.Accepted) {
            navController.navigate(TrevioRoute.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Welcome to Trevio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Before you get started, please read and accept our terms:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            TermsSection(
                title = "1. Acceptance of Terms",
                body = "By using Trevio, you agree to these terms and conditions. If you do not agree, please do not use the app."
            )
            Spacer(modifier = Modifier.height(16.dp))
            TermsSection(
                title = "2. Privacy & Data",
                body = "Trevio stores your name, email, and profile photo from your Google account. We use this to identify you and facilitate group expense splitting. Your data is stored securely in Firebase."
            )
            Spacer(modifier = Modifier.height(16.dp))
            TermsSection(
                title = "3. Financial Data",
                body = "Trevio helps track expenses and settlements between users. We do not process actual payments. All settlements are tracked in-app. UPI deep links redirect you to your preferred payment app."
            )
            Spacer(modifier = Modifier.height(16.dp))
            TermsSection(
                title = "4. User Conduct",
                body = "You are responsible for the expenses and settlements you add. Do not create fraudulent or misleading expense entries."
            )
            Spacer(modifier = Modifier.height(16.dp))
            TermsSection(
                title = "5. Account Termination",
                body = "You can delete your account at any time. Upon deletion, your data will be removed from our servers."
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it }
                )
                Text(
                    text = "I have read and agree to the Terms & Conditions",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.acceptTnC() },
                enabled = checked && state !is TermsViewModel.TermsState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (state is TermsViewModel.TermsState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("Accept & Continue", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (state is TermsViewModel.TermsState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = (state as TermsViewModel.TermsState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TermsSection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
