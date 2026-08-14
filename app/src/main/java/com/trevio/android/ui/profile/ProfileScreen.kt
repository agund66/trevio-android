package com.trevio.android.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.UserRefreshNotifier
import com.trevio.android.core.designsystem.components.CountryPickerDialog
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.core.designsystem.theme.ThemeMode
import com.trevio.android.core.designsystem.theme.ThemeViewModel
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.UserService
import com.trevio.android.util.CountryConstants
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class CountryCode(val code: String, val dialCode: String, val flag: String, val phoneLength: Int)

/** Backward-compatible wrapper: maps CountryInfo → CountryCode for existing UI code */
internal val COUNTRY_CODES: List<CountryCode> = CountryConstants.COUNTRY_CODES.map {
    CountryCode(it.code, it.dialCode, it.flag, it.phoneLength)
}

private fun isValidUpiId(upiId: String): Boolean {
    if (upiId.isEmpty()) return true
    val regex = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")
    return regex.matches(upiId)
}

private fun isValidPhoneNumber(phone: String, countryCode: String): Boolean {
    if (phone.isEmpty()) return false
    val country = COUNTRY_CODES.find { it.code == countryCode } ?: COUNTRY_CODES.first()
    return phone.length == country.phoneLength && phone.all { it.isDigit() }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authService: AuthService,
    private val userService: UserService,
    private val userRefreshNotifier: UserRefreshNotifier
) : ViewModel() {

    data class ProfileState(
        val user: User? = null,
        val isLoading: Boolean = true,
        val isEditing: Boolean = false,
        val isSaving: Boolean = false,
        @StringRes val error: Int? = null,
        val signedOut: Boolean = false
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

    fun saveProfile(displayName: String, upiId: String, phoneNumber: String, countryCode: String) {
        val currentUser = _state.value.user ?: return
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val derivedCurrency = CountryConstants.getCurrencyForCountry(countryCode)
            val derivedTimezone = CountryConstants.getTimezoneForCountry(countryCode)
            val updated = currentUser.copy(
                displayName = displayName,
                defaultCurrency = derivedCurrency,
                timezone = derivedTimezone,
                upiId = upiId,
                phoneNumber = phoneNumber,
                countryCode = countryCode
            )
            userService.updateUser(updated)
                .onSuccess {
                    _state.value = ProfileState(user = updated, isLoading = false, isEditing = false)
                    userRefreshNotifier.notifyUserRefreshed()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSaving = false, error = e.toStringResId())
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authService.signOut()
            _state.value = _state.value.copy(signedOut = true)
        }
    }

    fun deleteAccount() {
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            userService.deleteAccount()
                .onSuccess {
                    authService.signOut()
                    _state.value = _state.value.copy(isSaving = false, signedOut = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSaving = false, error = e.toStringResId())
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: androidx.navigation.NavHostController,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val themeMode by themeViewModel.themeMode.collectAsState()

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) {
            onSignOut()
        }
    }

    if (state.isLoading) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val user = state.user ?: return

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isEditing) {
                    IconButton(onClick = { viewModel.cancelEditing() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                    }
                }
                Text(
                    stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (!state.isEditing) {
                    TextButton(onClick = { viewModel.startEditing() }) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.profile_edit), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            MemberAvatar(name = user.displayName, photoURL = user.photoURL, size = 80)
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.isEditing) {
            EditProfileContent(
                user = user,
                isSaving = state.isSaving,
                error = state.error,
                onSave = { name, upi, phone, cc -> viewModel.saveProfile(name, upi, phone, cc) }
            )
        } else {
            ViewProfileContent(
                user = user,
                onEdit = { viewModel.startEditing() },
                onDelete = { viewModel.deleteAccount() },
                themeMode = themeMode,
                onThemeModeChange = { themeViewModel.setThemeMode(it) }
            )
        }
    }
}

@Composable
private fun ViewProfileContent(
    user: User,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hasUpiId = user.upiId.isNotEmpty()
    val hasPhone = user.phoneNumber.isNotEmpty()
    val country = COUNTRY_CODES.find { it.code == user.countryCode } ?: COUNTRY_CODES.first()
    val isIndiaSelected = country.code == "IN"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        val isDark = isSystemInDarkTheme()
        ProfileInfoCard(
            icon = Icons.Default.Person,
            iconColor = if (isDark) CategoryTransportDark else CategoryTransport,
            label = stringResource(R.string.profile_username),
            value = "@${user.username}"
        )
        ProfileInfoCard(
            icon = Icons.Default.Mail,
            iconColor = if (isDark) BalancePositiveDark else BalancePositive,
            label = stringResource(R.string.profile_email),
            value = user.email
        )
        // Always show phone card
        ProfileInfoCard(
            icon = Icons.Default.Phone,
            iconColor = if (isDark) CategoryShoppingDark else CategoryShopping,
            label = stringResource(R.string.profile_phone),
            value = if (hasPhone) "${country.flag} ${country.dialCode} ${user.phoneNumber}" else stringResource(R.string.profile_not_set),
            actionLabel = if (!hasPhone) stringResource(R.string.profile_add) else null,
            onAction = if (!hasPhone) onEdit else null
        )

        // UPI ID card — only shown for India (UPI is India-specific)
        if (isIndiaSelected) {
            ProfileInfoCard(
                icon = Icons.Default.Payments,
                iconColor = if (isDark) CategoryAccommodationDark else CategoryAccommodation,
                label = stringResource(R.string.profile_upi_id),
                value = if (hasUpiId) user.upiId else stringResource(R.string.profile_not_set),
                actionLabel = if (!hasUpiId) stringResource(R.string.profile_add) else null,
                onAction = if (!hasUpiId) onEdit else null
            )
        }

        // Payment info card
        TrevioCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.profile_payment_info),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    isIndiaSelected && hasUpiId -> {
                        Text(
                            user.upiId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.profile_payment_upi_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    hasPhone -> {
                        Text(
                            "${country.dialCode} ${user.phoneNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.profile_payment_phone_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.profile_no_payment_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onEdit,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.profile_set_up_payment), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance section
        TrevioCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.profile_theme),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionButton(
                        label = stringResource(R.string.profile_theme_light),
                        isSelected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionButton(
                        label = stringResource(R.string.profile_theme_dark),
                        isSelected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionButton(
                        label = stringResource(R.string.profile_theme_system),
                        isSelected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.profile_delete_account))
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.profile_delete_account)) },
            text = {
                Text(stringResource(R.string.profile_delete_confirm))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.group_detail_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.profile_cancel))
                }
            }
        )
    }
}

@Composable
fun ProfileInfoCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    TrevioCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
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
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                val notSetText = stringResource(R.string.profile_not_set)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (value == notSetText) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileContent(
    user: User,
    isSaving: Boolean,
    @StringRes error: Int?,
    onSave: (String, String, String, String) -> Unit
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var upiId by remember { mutableStateOf(user.upiId) }
    var phoneNumber by remember { mutableStateOf(user.phoneNumber) }
    var countryCode by remember { mutableStateOf(user.countryCode.ifEmpty { CountryConstants.DEFAULT_COUNTRY_CODE }) }
    var showCountryPicker by remember { mutableStateOf(false) }
    val countryPickerInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(countryPickerInteraction) {
        countryPickerInteraction.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                showCountryPicker = true
            }
        }
    }

    val upiError = if (upiId.isNotEmpty() && !isValidUpiId(upiId)) stringResource(R.string.profile_upi_invalid) else null
    val phoneError = if (!isValidPhoneNumber(phoneNumber, countryCode)) stringResource(R.string.profile_phone_invalid) else null
    val nameError = if (displayName.isBlank()) stringResource(R.string.profile_name_required) else null
    val hasErrors = upiError != null || phoneError != null || nameError != null

    val derivedCurrency = CountryConstants.getCurrencyForCountry(countryCode)
    val derivedTimezone = CountryConstants.getTimezoneForCountry(countryCode)
    val isIndiaSelected = countryCode == "IN"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.profile_display_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = nameError != null,
            supportingText = { if (nameError != null) Text(nameError) }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.profile_phone), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = COUNTRY_CODES.find { it.code == countryCode }?.let { "${it.flag} ${it.dialCode}" } ?: "🇮🇳 +91",
                onValueChange = {},
                readOnly = true,
                interactionSource = countryPickerInteraction,
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.profile_select_country))
                },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter { c -> c.isDigit() }.take((COUNTRY_CODES.find { c -> c.code == countryCode }?.phoneLength ?: 10)) },
                label = { Text(stringResource(R.string.profile_phone)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = phoneError != null,
                supportingText = { if (phoneError != null) Text(phoneError) }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.profile_country_hint, derivedCurrency, derivedTimezone),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        if (isIndiaSelected) {
            OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it },
                label = { Text(stringResource(R.string.profile_upi_id)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = upiError != null,
                supportingText = { if (upiError != null) Text(upiError) else Text(stringResource(R.string.profile_upi_hint)) }
            )
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onSave(displayName, upiId, phoneNumber, countryCode) },
            enabled = !isSaving && !hasErrors,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                Text(stringResource(R.string.profile_save))
            }
        }
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedCode = countryCode,
            onSelect = { code ->
                countryCode = code
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsConditionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_terms_conditions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TermsSection(stringResource(R.string.terms_acceptance_title), stringResource(R.string.terms_acceptance_body))
            Spacer(modifier = Modifier.height(10.dp))
            TermsSection(stringResource(R.string.terms_privacy_title), stringResource(R.string.terms_privacy_body))
            Spacer(modifier = Modifier.height(10.dp))
            TermsSection(stringResource(R.string.terms_financial_title), stringResource(R.string.terms_financial_body))
            Spacer(modifier = Modifier.height(10.dp))
            TermsSection(stringResource(R.string.terms_conduct_title), stringResource(R.string.terms_conduct_body))
            Spacer(modifier = Modifier.height(10.dp))
            TermsSection(stringResource(R.string.terms_termination_title), stringResource(R.string.terms_termination_body))
        }
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
private fun TermsSection(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
