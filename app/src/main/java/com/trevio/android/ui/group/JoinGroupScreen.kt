package com.trevio.android.ui.group

import androidx.compose.foundation.layout.*
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
import com.trevio.android.domain.repository.GroupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    private val groupService: GroupService
) : ViewModel() {

    data class JoinState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val joined: Boolean = false
    )

    private val _state = MutableStateFlow(JoinState())
    val state: StateFlow<JoinState> = _state

    fun joinGroup(inviteCode: String) {
        _state.value = JoinState(isLoading = true)
        viewModelScope.launch {
            groupService.joinGroupViaCode(inviteCode)
                .onSuccess { _state.value = JoinState(joined = true) }
                .onFailure { e -> _state.value = JoinState(error = e.message) }
        }
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

    LaunchedEffect(state.joined) {
        if (state.joined) {
            navController.navigate(TrevioRoute.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(inviteCode) {
        if (inviteCode.isNotBlank()) {
            viewModel.joinGroup(inviteCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Group") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("Cancel") }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (state.isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Joining group...")
                }
            } else if (state.error != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to join", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { navController.popBackStack() }) { Text("Go Back") }
                }
            }
        }
    }
}
