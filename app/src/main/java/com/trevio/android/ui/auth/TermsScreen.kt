package com.trevio.android.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.designsystem.theme.*
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
        data class Error(@StringRes val resId: Int) : TermsState()
    }

    private val _state = MutableStateFlow<TermsState>(TermsState.Idle)
    val state: StateFlow<TermsState> = _state

    fun acceptTnC() {
        _state.value = TermsState.Loading
        viewModelScope.launch {
            userService.acceptTnC()
                .onSuccess { _state.value = TermsState.Accepted }
                .onFailure { _state.value = TermsState.Error(R.string.terms_failed_accept) }
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
            navController.navigate(TrevioRoute.PhoneSetup.route) {
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
    ) {
        // ── Compact header (fixed, does not scroll) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.terms_welcome),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.terms_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        // ── Scrollable terms cards (takes remaining space) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isDark = isSystemInDarkTheme()
            TermsSectionCard(
                icon = Icons.Default.VerifiedUser,
                iconColor = if (isDark) TrevioSecondaryDarkTheme else TrevioSecondary,
                title = stringResource(R.string.terms_acceptance_title),
                body = stringResource(R.string.terms_acceptance_body)
            )
            TermsSectionCard(
                icon = Icons.Default.Lock,
                iconColor = if (isDark) BalancePositiveDark else BalancePositive,
                title = stringResource(R.string.terms_privacy_title),
                body = stringResource(R.string.terms_privacy_body)
            )
            TermsSectionCard(
                icon = Icons.Default.Payments,
                iconColor = if (isDark) TrevioWarningDarkTheme else TrevioWarning,
                title = stringResource(R.string.terms_financial_title),
                body = stringResource(R.string.terms_financial_body)
            )
            TermsSectionCard(
                icon = Icons.Default.Gavel,
                iconColor = if (isDark) CategoryShoppingDark else CategoryShopping,
                title = stringResource(R.string.terms_conduct_title),
                body = stringResource(R.string.terms_conduct_body)
            )
            TermsSectionCard(
                icon = Icons.Default.PersonOff,
                iconColor = if (isDark) BalanceNegativeDark else BalanceNegative,
                title = stringResource(R.string.terms_termination_title),
                body = stringResource(R.string.terms_termination_body)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Pinned bottom section: checkbox + accept button + error ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        text = stringResource(R.string.terms_agree),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (state is TermsViewModel.TermsState.Error) {
                    Text(
                        text = stringResource((state as TermsViewModel.TermsState.Error).resId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = { viewModel.acceptTnC() },
                    enabled = checked && state !is TermsViewModel.TermsState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state is TermsViewModel.TermsState.Loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.terms_accept), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
