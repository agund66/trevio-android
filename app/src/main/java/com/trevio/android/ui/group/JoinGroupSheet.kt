package com.trevio.android.ui.group

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.trevio.android.util.QrCodeGenerator
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.GroupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JoinGroupSheetViewModel @Inject constructor(
    private val groupService: GroupService,
    private val authService: AuthService
) : ViewModel() {

    data class SheetState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val joined: Boolean = false,
        val joinedGroupName: String? = null,
        val needsAuth: Boolean = false,
        val needsTnC: Boolean = false
    )

    private val _state = MutableStateFlow(SheetState())
    val state: StateFlow<SheetState> = _state

    fun join(inviteCode: String) {
        val code = inviteCode.trim()
        if (code.isBlank()) {
            _state.value = SheetState(error = "Please enter an invite code")
            return
        }
        _state.value = SheetState(isLoading = true)
        viewModelScope.launch {
            if (!authService.isUserAuthenticated()) {
                _state.value = SheetState(needsAuth = true)
                return@launch
            }
            val user = authService.getCurrentUser()
            if (user == null) {
                _state.value = SheetState(needsAuth = true)
                return@launch
            }
            if (!user.acceptedTnC) {
                _state.value = SheetState(needsTnC = true)
                return@launch
            }
            groupService.joinGroupViaCode(code)
                .onSuccess { (_, groupName) ->
                    _state.value = SheetState(joined = true, joinedGroupName = groupName)
                }
                .onFailure { e ->
                    _state.value = SheetState(error = e.message ?: "Failed to join group")
                }
        }
    }

    fun reset() {
        _state.value = SheetState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupSheet(
    onDismiss: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onJoined: () -> Unit,
    viewModel: JoinGroupSheetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var inviteCode by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.needsAuth) {
        if (state.needsAuth) {
            onDismiss()
            onNavigateToLogin()
        }
    }

    LaunchedEffect(state.needsTnC) {
        if (state.needsTnC) {
            onDismiss()
            onNavigateToTerms()
        }
    }

    LaunchedEffect(state.joined) {
        if (state.joined) {
            val name = state.joinedGroupName
            Toast.makeText(context, if (name != null) "Joined \"$name\"" else "Joined group", Toast.LENGTH_SHORT).show()
            viewModel.reset()
            onDismiss()
            onJoined()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Join Group",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter an invite code or scan a QR code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it.trim() },
                label = { Text("Invite Code") },
                placeholder = { Text("e.g., ABC123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.join(inviteCode) },
                enabled = !state.isLoading && inviteCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Join Group", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "OR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    val scanner = GmsBarcodeScanning.getClient(context)
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                val code = QrCodeGenerator.extractInviteCode(rawValue)
                                inviteCode = code
                                viewModel.join(code)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Scan failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR Code", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
