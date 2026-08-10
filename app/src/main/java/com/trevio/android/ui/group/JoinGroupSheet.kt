package com.trevio.android.ui.group

import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.trevio.android.util.QrCodeGenerator
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.SettlementService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JoinGroupSheetViewModel @Inject constructor(
    private val groupService: GroupService,
    private val authService: AuthService,
    private val settlementService: SettlementService
) : ViewModel() {

    data class SheetState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val joined: Boolean = false,
        val joinedGroupName: String? = null,
        val needsAuth: Boolean = false,
        val needsTnC: Boolean = false,
        val claimableMembers: List<Member> = emptyList(),
        val groupId: String? = null,
        val claiming: Boolean = false,
        val claimError: String? = null,
        val claimed: Boolean = false
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
                .onSuccess { (groupId, groupName) ->
                    val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
                    val claimable = members.filter { it.isOffline }
                    _state.value = SheetState(joined = true, joinedGroupName = groupName, groupId = groupId, claimableMembers = claimable)
                }
                .onFailure { e ->
                    _state.value = SheetState(error = e.message ?: "Failed to join group")
                }
        }
    }

    fun claimOfflineMember(memberDocId: String) {
        val groupId = _state.value.groupId ?: return
        _state.value = _state.value.copy(claiming = true, claimError = null)
        viewModelScope.launch {
            groupService.claimOfflineMember(groupId, memberDocId)
                .onSuccess {
                    _state.value = _state.value.copy(claiming = false, claimed = true, claimableMembers = emptyList())
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(claiming = false, claimError = e.message)
                }
        }
    }

    fun skipClaim() {
        _state.value = _state.value.copy(claimableMembers = emptyList())
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

    LaunchedEffect(state.joined, state.claimableMembers, state.claimed) {
        if (state.joined && state.claimableMembers.isEmpty() && !state.claiming) {
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
            if (state.claimableMembers.isNotEmpty()) {
                Text(
                    text = "Claim a Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Are any of these offline profiles you?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))

                state.claimableMembers.forEach { member ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                member.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.claimOfflineMember(member.uid) },
                                enabled = !state.claiming
                            ) { Text("Claim") }
                        }
                    }
                }

                if (state.claimError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(state.claimError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = { viewModel.skipClaim() }) {
                    Text("Skip for now")
                }
            } else {
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
                        // Check Google Play Services availability before launching the scanner.
                        val playServicesStatus = GoogleApiAvailability.getInstance()
                            .isGooglePlayServicesAvailable(context)
                        if (playServicesStatus != ConnectionResult.SUCCESS) {
                            Toast.makeText(
                                context,
                                "QR scanning requires Google Play Services. Please update it and try again.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@OutlinedButton
                        }

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
                            .addOnFailureListener { exception ->
                                // User cancellation is not an error — only show real failures.
                                if (exception is com.google.android.gms.common.api.ApiException &&
                                    exception.statusCode == com.google.android.gms.common.api.CommonStatusCodes.CANCELED
                                ) return@addOnFailureListener
                                Toast.makeText(
                                    context,
                                    "Scan failed. Try entering the code manually.",
                                    Toast.LENGTH_SHORT
                                ).show()
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
}
