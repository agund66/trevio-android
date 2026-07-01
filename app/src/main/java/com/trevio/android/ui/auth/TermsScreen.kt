package com.trevio.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
            navController.navigate(TrevioRoute.Main.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column {
                Text(
                    text = "Welcome to Trevio",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Before you get started, please review and accept our terms.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TermsSectionCard(
                icon = Icons.Default.VerifiedUser,
                iconColor = Color(0xFF6366F1),
                title = "1. Acceptance of Terms",
                body = "By using Trevio, you agree to these terms and conditions. If you do not agree, please do not use the app."
            )
            TermsSectionCard(
                icon = Icons.Default.Lock,
                iconColor = Color(0xFF22C55E),
                title = "2. Privacy & Data",
                body = "Trevio stores your name, email, and profile photo from your Google account. We use this to identify you and facilitate group expense splitting. Your data is stored securely in Firebase."
            )
            TermsSectionCard(
                icon = Icons.Default.Payments,
                iconColor = Color(0xFFF59E0B),
                title = "3. Financial Data",
                body = "Trevio helps track expenses and settlements between users. We do not process actual payments. All settlements are tracked in-app. UPI deep links redirect you to your preferred payment app."
            )
            TermsSectionCard(
                icon = Icons.Default.Gavel,
                iconColor = Color(0xFFEC4899),
                title = "4. User Conduct",
                body = "You are responsible for the expenses and settlements you add. Do not create fraudulent or misleading expense entries."
            )
            TermsSectionCard(
                icon = Icons.Default.PersonOff,
                iconColor = Color(0xFFEF4444),
                title = "5. Account Termination",
                body = "You can delete your account at any time. Upon deletion, your data will be removed from our servers."
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "I have read and agree to the Terms & Conditions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.acceptTnC() },
                enabled = checked && state !is TermsViewModel.TermsState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state is TermsViewModel.TermsState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accept & Continue", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (state is TermsViewModel.TermsState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (state as TermsViewModel.TermsState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TermsSectionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
