package com.trevio.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.UserService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class CountryCode(val code: String, val dialCode: String, val flag: String, val phoneLength: Int)

private val COUNTRY_CODES = listOf(
    CountryCode("IN", "+91", "🇮🇳", 10),
    CountryCode("US", "+1", "🇺🇸", 10),
    CountryCode("GB", "+44", "🇬🇧", 10),
    CountryCode("AE", "+971", "🇦🇪", 9),
    CountryCode("SG", "+65", "🇸🇬", 8),
    CountryCode("AU", "+61", "🇦🇺", 9),
    CountryCode("CA", "+1", "🇨🇦", 10),
    CountryCode("DE", "+49", "🇩🇪", 10),
    CountryCode("FR", "+33", "🇫🇷", 9),
    CountryCode("JP", "+81", "🇯🇵", 10)
)

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

private fun getPaymentAddress(user: User): String {
    return if (user.upiId.isNotEmpty()) {
        user.upiId
    } else if (user.phoneNumber.isNotEmpty() && (user.countryCode.isEmpty() || user.countryCode == "IN")) {
        "${user.phoneNumber}@paytm"
    } else {
        "Not set"
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authService: AuthService,
    private val userService: UserService
) : ViewModel() {

    data class ProfileState(
        val user: User? = null,
        val isLoading: Boolean = true,
        val isEditing: Boolean = false,
        val isSaving: Boolean = false,
        val error: String? = null,
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

    fun saveProfile(displayName: String, currency: String, upiId: String, phoneNumber: String, countryCode: String) {
        val currentUser = _state.value.user ?: return
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val updated = currentUser.copy(
                displayName = displayName,
                defaultCurrency = currency,
                upiId = upiId,
                phoneNumber = phoneNumber,
                countryCode = countryCode
            )
            userService.updateUser(updated)
                .onSuccess {
                    _state.value = ProfileState(user = updated, isLoading = false, isEditing = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSaving = false, error = e.message)
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
                    _state.value = _state.value.copy(isSaving = false, error = e.message)
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: androidx.navigation.NavHostController,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

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
                        text = "Profile",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
                Text(
                    "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!state.isEditing) {
                        TextButton(onClick = { viewModel.startEditing() }) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out", tint = Color.White)
                    }
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
                onSave = { name, currency, upi, phone, cc -> viewModel.saveProfile(name, currency, upi, phone, cc) }
            )
        } else {
            ViewProfileContent(user = user, onEdit = { viewModel.startEditing() }, onDelete = { viewModel.deleteAccount() })
        }
    }
}

@Composable
private fun ViewProfileContent(user: User, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        ProfileInfoCard(
            icon = Icons.Default.Person,
            iconColor = Color(0xFF6366F1),
            label = "Username",
            value = "@${user.username}"
        )
        ProfileInfoCard(
            icon = Icons.Default.Mail,
            iconColor = Color(0xFF22C55E),
            label = "Email",
            value = user.email
        )
        val currencySymbol = when (user.defaultCurrency) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "AED" -> "د.إ"
            "SGD" -> "S$"
            "AUD" -> "A$"
            "CAD" -> "C$"
            "JPY" -> "¥"
            else -> user.defaultCurrency
        }
        ProfileInfoCard(
            icon = Icons.Default.Payments,
            iconColor = Color(0xFFF59E0B),
            label = "Currency",
            value = "$currencySymbol ${user.defaultCurrency}"
        )
        if (user.phoneNumber.isNotEmpty()) {
            val country = COUNTRY_CODES.find { it.code == user.countryCode } ?: COUNTRY_CODES.first()
            ProfileInfoCard(
                icon = Icons.Default.Phone,
                iconColor = Color(0xFFEC4899),
                label = "Phone",
                value = "${country.flag} ${country.dialCode} ${user.phoneNumber}"
            )
        }
        ProfileInfoCard(
            icon = Icons.Default.Payments,
            iconColor = Color(0xFF0D9488),
            label = "Payment Address",
            value = getPaymentAddress(user)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showTermsDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Terms & Conditions")
        }

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
            Text("Delete Account")
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showTermsDialog) {
        TermsConditionsDialog(onDismiss = { showTermsDialog = false })
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Account?") },
            text = {
                Text("This will permanently delete your account, remove you from all groups, and erase your data. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
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
    value: String
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
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileContent(
    user: User,
    isSaving: Boolean,
    error: String?,
    onSave: (String, String, String, String, String) -> Unit
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var currency by remember { mutableStateOf(user.defaultCurrency) }
    var upiId by remember { mutableStateOf(user.upiId) }
    var phoneNumber by remember { mutableStateOf(user.phoneNumber) }
    var countryCode by remember { mutableStateOf(user.countryCode.ifEmpty { "IN" }) }
    var countryMenuExpanded by remember { mutableStateOf(false) }

    val currencies = listOf(
        Triple("INR", "₹", "Indian Rupee"),
        Triple("USD", "$", "US Dollar"),
        Triple("EUR", "€", "Euro"),
        Triple("GBP", "£", "British Pound"),
        Triple("AED", "د.إ", "UAE Dirham"),
        Triple("SGD", "S$", "Singapore Dollar"),
        Triple("AUD", "A$", "Australian Dollar"),
        Triple("CAD", "C$", "Canadian Dollar"),
        Triple("JPY", "¥", "Japanese Yen")
    )
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    val selectedCurrency = currencies.find { it.first == currency }

    val upiError = if (upiId.isNotEmpty() && !isValidUpiId(upiId)) "Invalid UPI ID format (e.g. name@bank)" else null
    val phoneError = if (!isValidPhoneNumber(phoneNumber, countryCode)) "Invalid phone number for selected country" else null
    val hasErrors = upiError != null || phoneError != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text("Currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedTextField(
                value = "${selectedCurrency?.second ?: ""} ${currency} - ${selectedCurrency?.third ?: ""}",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select currency")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            DropdownMenu(
                expanded = currencyMenuExpanded,
                onDismissRequest = { currencyMenuExpanded = false }
            ) {
                currencies.forEach { (code, symbol, name) ->
                    DropdownMenuItem(
                        text = { Text("$symbol $code - $name") },
                        onClick = {
                            currency = code
                            currencyMenuExpanded = false
                        }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { currencyMenuExpanded = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Phone Number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedTextField(
                    value = COUNTRY_CODES.find { it.code == countryCode }?.let { "${it.flag} ${it.dialCode}" } ?: "🇮🇳 +91",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select country")
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
                DropdownMenu(expanded = countryMenuExpanded, onDismissRequest = { countryMenuExpanded = false }) {
                    COUNTRY_CODES.forEach { country ->
                        DropdownMenuItem(
                            text = { Text("${country.flag} ${country.dialCode} (${country.code})") },
                            onClick = {
                                countryCode = country.code
                                countryMenuExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter { c -> c.isDigit() }.take((COUNTRY_CODES.find { c -> c.code == countryCode }?.phoneLength ?: 10)) },
                label = { Text("Phone number") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = phoneError != null,
                supportingText = { if (phoneError != null) Text(phoneError) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = upiId,
            onValueChange = { upiId = it },
            label = { Text("UPI ID (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = upiError != null,
            supportingText = { if (upiError != null) Text(upiError) else Text("Used for receiving payments. Phone number used as fallback.") }
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onSave(displayName, currency, upiId, phoneNumber, countryCode) },
            enabled = !isSaving && !hasErrors,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermsConditionsDialog(onDismiss: () -> Unit) {
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
                    Text("Terms & Conditions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TermsSection("1. Acceptance of Terms", "By using Trevio, you agree to these terms and conditions. If you do not agree, please do not use the app.")
            Spacer(modifier = Modifier.height(10.dp))
            TermsSection("2. Privacy & Data", "Trevio stores your name, email, and profile photo from your Google account. We use this to identify you and facilitate group expense splitting. Your data is stored securely in Firebase.")
            Spacer(modifier = Modifier.height(10.dp))
            TermsSection("3. Financial Data", "Trevio helps track expenses and settlements between users. We do not process actual payments. All settlements are tracked in-app. UPI deep links redirect you to your preferred payment app.")
            Spacer(modifier = Modifier.height(10.dp))
            TermsSection("4. User Conduct", "You are responsible for the expenses and settlements you add. Do not create fraudulent or misleading expense entries.")
            Spacer(modifier = Modifier.height(10.dp))
            TermsSection("5. Account Termination", "You can delete your account at any time. Upon deletion, your data will be removed from our servers.")
        }
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
