package com.trevio.android.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.trevio.android.R
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.KarmaService
import com.trevio.android.domain.repository.UserService
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val userService: UserService,
    private val karmaService: KarmaService
) : ViewModel() {

    data class State(
        val user: User? = null,
        val karmaBreakdown: com.trevio.android.domain.model.KarmaBreakdown? = null,
        val isLoading: Boolean = true,
        @StringRes val error: Int? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun loadUser(uid: String) {
        _state.value = State(isLoading = true)
        viewModelScope.launch {
            userService.getUser(uid)
                .onSuccess { user ->
                    // If user has karma public, fetch their breakdown
                    var karmaBreakdown: com.trevio.android.domain.model.KarmaBreakdown? = null
                    if (user.karmaPublic) {
                        karmaService.getPublicKarma(uid)
                            .onSuccess { karmaBreakdown = it }
                    }
                    _state.value = State(user = user, karmaBreakdown = karmaBreakdown, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = State(isLoading = false, error = e.toStringResId())
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    navController: androidx.navigation.NavHostController,
    uid: String,
    viewModel: PublicProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(uid) {
        viewModel.loadUser(uid)
    }

    if (state.isLoading) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                    }
                    Text(stringResource(R.string.public_profile_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val user = state.user
    if (user == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.public_profile_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                }
                Text(
                    stringResource(R.string.public_profile_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            if (user.photoURL.isNotEmpty()) {
                coil.compose.AsyncImage(
                    model = user.photoURL,
                    contentDescription = user.displayName,
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                user.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "@${user.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Karma badge (only if user has made it public and breakdown is available)
            state.karmaBreakdown?.let { breakdown ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(tierColor(breakdown.tier).copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = tierColor(breakdown.tier),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${stringResource(R.string.karma_public_badge)}: ${breakdown.score}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tierColor(breakdown.tier)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(tierLabelResId(breakdown.tier)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isDark = isSystemInDarkTheme()
            ProfileInfoCard(
                icon = Icons.Default.Person,
                iconColor = if (isDark) CategoryTransportDark else CategoryTransport,
                label = stringResource(R.string.public_profile_username),
                value = "@${user.username}"
            )
            ProfileInfoCard(
                icon = Icons.Default.Mail,
                iconColor = if (isDark) BalancePositiveDark else BalancePositive,
                label = stringResource(R.string.public_profile_email),
                value = user.email
            )

            // Payment info — UPI only shown for India users
            if (user.upiId.isNotEmpty() && user.countryCode == "IN") {
                ProfileInfoCard(
                    icon = Icons.Default.Wallet,
                    iconColor = if (isDark) CategoryAccommodationDark else CategoryAccommodation,
                    label = stringResource(R.string.public_profile_pay_upi),
                    value = user.upiId
                )
            } else if (user.phoneNumber.isNotEmpty()) {
                val country = COUNTRY_CODES.find { it.code == user.countryCode } ?: COUNTRY_CODES.first()
                ProfileInfoCard(
                    icon = Icons.Default.Phone,
                    iconColor = if (isDark) CategoryAccommodationDark else CategoryAccommodation,
                    label = stringResource(R.string.public_profile_pay_phone),
                    value = "${country.dialCode} ${user.phoneNumber}"
                )
            }
        }
    }
}

private fun tierColor(tier: String): Color = when (tier) {
    "platinum" -> Color(0xFFE5E4E2)
    "gold" -> Color(0xFFFFD700)
    "silver" -> Color(0xFFC0C0C0)
    else -> Color(0xFFCD7F32)
}

private fun tierLabelResId(tier: String): Int = when (tier) {
    "platinum" -> R.string.karma_tier_platinum
    "gold" -> R.string.karma_tier_gold
    "silver" -> R.string.karma_tier_silver
    else -> R.string.karma_tier_bronze
}
