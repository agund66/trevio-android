package com.trevio.android.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.navigation.TrevioRoute
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
class JoinGroupViewModel @Inject constructor(
    private val groupService: GroupService,
    private val authService: AuthService,
    private val settlementService: SettlementService
) : ViewModel() {

    data class JoinState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val joined: Boolean = false,
        val needsAuth: Boolean = false,
        val needsTnC: Boolean = false,
        val claimableMembers: List<Member> = emptyList(),
        val groupId: String? = null,
        val claiming: Boolean = false,
        val claimError: String? = null,
        val claimed: Boolean = false
    )

    private val _state = MutableStateFlow(JoinState())
    val state: StateFlow<JoinState> = _state

    fun tryJoin(inviteCode: String) {
        _state.value = JoinState(isLoading = true)
        viewModelScope.launch {
            if (!authService.isUserAuthenticated()) {
                _state.value = JoinState(needsAuth = true)
                return@launch
            }
            val user = authService.getCurrentUser()
            if (user == null) {
                _state.value = JoinState(needsAuth = true)
                return@launch
            }
            if (!user.acceptedTnC) {
                _state.value = JoinState(needsTnC = true)
                return@launch
            }
            groupService.joinGroupViaCode(inviteCode)
                .onSuccess { (groupId, _) ->
                    val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
                    val claimable = members.filter { it.isOffline }
                    _state.value = JoinState(joined = true, groupId = groupId, claimableMembers = claimable)
                }
                .onFailure { e -> _state.value = JoinState(error = e.message) }
        }
    }

    fun claimOfflineMember(memberDocId: String) {
        val groupId = _state.value.groupId ?: return
        _state.value = _state.value.copy(claiming = true, claimError = null)
        viewModelScope.launch {
            groupService.claimOfflineMember(groupId, memberDocId)
                .onSuccess {
                    _state.value = _state.value.copy(claiming = false, claimed = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(claiming = false, claimError = e.message)
                }
        }
    }

    fun skipClaim() {
        _state.value = _state.value.copy(claimableMembers = emptyList())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: JoinGroupViewModel = hiltViewModel()
) {
    val inviteCode = navController.currentBackStackEntry?.arguments?.getString("inviteCode") ?: ""
    val state by viewModel.state.collectAsState()

    LaunchedEffect(inviteCode) {
        if (inviteCode.isNotBlank()) {
            viewModel.tryJoin(inviteCode)
        }
    }

    LaunchedEffect(state.joined, state.claimableMembers, state.claimed) {
        if (state.joined && state.claimableMembers.isEmpty() && !state.claiming) {
            navController.navigate(TrevioRoute.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(state.needsAuth) {
        if (state.needsAuth) {
            navController.navigate(TrevioRoute.Login.route) {
                popUpTo(TrevioRoute.JoinGroup.route) { inclusive = true }
            }
        }
    }

    LaunchedEffect(state.needsTnC) {
        if (state.needsTnC) {
            navController.navigate(TrevioRoute.Terms.route) {
                popUpTo(TrevioRoute.JoinGroup.route) { inclusive = true }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = "Join Group",
            onBack = { navController.popBackStack() }
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Joining group...")
                    }
                }
                state.joined && state.claimableMembers.isNotEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Joined successfully!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Are any of these offline profiles you? Claim one to link your account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
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
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Spacer(modifier = Modifier.height(24.dp))
                        TextButton(onClick = { viewModel.skipClaim() }) {
                            Text("Skip for now")
                        }
                    }
                }
                state.joined && state.claimed -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Profile claimed!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("All transactions have been linked to your account.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                state.needsAuth -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "You've been invited to join a group!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Sign in with Google to accept the invitation and start splitting bills.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Redirecting to sign in...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                state.needsTnC -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "Almost there!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Please accept the Terms & Conditions to join the group.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                state.error != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Failed to join",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { navController.popBackStack() }) { Text("Go Back") }
                    }
                }
            }
        }
    }
}
