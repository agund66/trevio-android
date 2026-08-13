package com.trevio.android.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.trevio.android.R
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.UserService
import com.trevio.android.util.CountryConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhoneSetupViewModel @Inject constructor(
    private val authService: AuthService,
    private val userService: UserService
) : ViewModel() {
    data class State(
        val user: User? = null,
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val saved: Boolean = false,
        @StringRes val error: Int? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = authService.getCurrentUser()
            _state.value = if (user == null) {
                State(isLoading = false, error = R.string.error_authentication_required)
            } else {
                State(user = user, isLoading = false)
            }
        }
    }

    fun retry() {
        _state.value = State()
        loadUser()
    }

    fun savePhone(phoneNumber: String, countryCode: String) {
        val user = _state.value.user ?: return
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            userService.updateUser(user.copy(phoneNumber = phoneNumber, countryCode = countryCode))
                .onSuccess { _state.value = _state.value.copy(isSaving = false, saved = true) }
                .onFailure { _state.value = _state.value.copy(isSaving = false, error = R.string.phone_setup_failed) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSetupScreen(
    navController: NavHostController,
    viewModel: PhoneSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val user = state.user
    var phoneNumber by rememberSaveable(user?.uid) { mutableStateOf(user?.phoneNumber.orEmpty()) }
    var countryCode by rememberSaveable(user?.uid) {
        mutableStateOf(user?.countryCode?.ifEmpty { CountryConstants.DEFAULT_COUNTRY_CODE } ?: CountryConstants.DEFAULT_COUNTRY_CODE)
    }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    val country = CountryConstants.getCountryByCode(countryCode)
    val isValid = phoneNumber.length == country.phoneLength && phoneNumber.all { it.isDigit() }

    LaunchedEffect(state.saved, user?.phoneNumber) {
        if (state.saved) {
            // New user just saved their phone → offer biometric lock setup
            // (first-login only). The BiometricSetupScreen auto-skips to
            // Main if no authenticator is available or the prompt was
            // already shown.
            navController.navigate(TrevioRoute.BiometricSetup.route) {
                popUpTo(0) { inclusive = true }
            }
        } else if (!user?.phoneNumber.isNullOrBlank()) {
            // User already had a phone number → go straight to Main
            navController.navigate(TrevioRoute.Main.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (user == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(state.error ?: R.string.error_authentication_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = viewModel::retry) {
                Text(stringResource(R.string.common_retry))
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.phone_setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.phone_setup_subtitle),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.phone_setup_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.profile_phone),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Country code selector — the OutlinedTextField itself opens
                    // the dropdown. The wrapping Box has NO clickable modifier
                    // (previously it intercepted taps and re-opened the menu
                    // immediately after selecting an item, making only the first
                    // item selectable). The DropdownMenu is constrained to a
                    // max height so the ~200 entries are scrollable.
                    Box {
                        OutlinedTextField(
                            value = "${country.flag} ${country.dialCode}",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.profile_select_country))
                            },
                            modifier = Modifier
                                .width(120.dp)
                                .clickable { countryMenuExpanded = true },
                            singleLine = true
                        )
                        DropdownMenu(
                            expanded = countryMenuExpanded,
                            onDismissRequest = { countryMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            CountryConstants.COUNTRY_CODES.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${option.flag} ${stringResource(option.nameResId)} ${option.dialCode}") },
                                    onClick = {
                                        countryCode = option.code
                                        phoneNumber = phoneNumber.take(option.phoneLength)
                                        countryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it.filter(Char::isDigit).take(country.phoneLength) },
                        label = { Text(stringResource(R.string.profile_phone)) },
                        placeholder = { Text(stringResource(R.string.phone_setup_placeholder, country.phoneLength)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = phoneNumber.isNotEmpty() && !isValid
                    )
                }
                if (phoneNumber.isNotEmpty() && !isValid) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.profile_phone_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                state.error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.savePhone(phoneNumber, countryCode) },
                    enabled = isValid && !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.width(24.dp)
                        )
                    } else {
                        Text(stringResource(R.string.phone_setup_continue))
                    }
                }
            }
        }
    }
}
