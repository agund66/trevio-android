package com.trevio.android.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.core.navigation.TrevioRouteSupport
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.ui.profile.TermsConditionsDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val authService: AuthService
) : ViewModel() {

    data class MoreState(
        val user: User? = null,
        val isSuperadmin: Boolean = false,
        val signedOut: Boolean = false
    )

    private val _state = MutableStateFlow(MoreState())
    val state: StateFlow<MoreState> = _state

    init {
        viewModelScope.launch {
            val user = authService.getCurrentUser()
            _state.value = _state.value.copy(
                user = user,
                isSuperadmin = user?.role == "superadmin"
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authService.signOut()
            _state.value = _state.value.copy(signedOut = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    navController: androidx.navigation.NavHostController,
    onSignOut: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showTermsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignOut()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Text(
                text = stringResource(R.string.more_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // User card — shows avatar, name, @username. Tapping navigates to Profile.
        val user = state.user
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(TrevioRoute.Profile.route) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (user != null) {
                    MemberAvatar(
                        name = user.displayName,
                        photoURL = user.photoURL,
                        size = 48
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.displayName ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!user?.username.isNullOrEmpty()) {
                        Text(
                            text = "@${user?.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Menu items
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isSuperadmin) {
                MoreMenuItem(
                    icon = Icons.Default.AdminPanelSettings,
                    label = stringResource(R.string.more_admin_dashboard),
                    onClick = { navController.navigate(TrevioRoute.Admin.route) }
                )
            }

            MoreMenuItem(
                icon = Icons.Default.Description,
                label = stringResource(R.string.profile_terms_conditions),
                onClick = { showTermsDialog = true }
            )

            MoreMenuItem(
                icon = Icons.Default.Info,
                label = stringResource(R.string.profile_help_support),
                onClick = { navController.navigate(TrevioRouteSupport.Support.route) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            MoreMenuItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.more_sign_out),
                onClick = { viewModel.signOut() },
                isDestructive = true
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showTermsDialog) {
        TermsConditionsDialog(onDismiss = { showTermsDialog = false })
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val labelColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
