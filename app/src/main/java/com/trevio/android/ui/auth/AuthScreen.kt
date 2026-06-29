package com.trevio.android.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trevio.android.R
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.UserService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authService: AuthService,
    private val userService: UserService
) : ViewModel() {

    sealed class AuthState {
        data object Idle : AuthState()
        data object Loading : AuthState()
        data object NeedsTnC : AuthState()
        data object Authenticated : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state

    fun signInWithGoogle(idToken: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val result = authService.signInWithGoogle(idToken)
            result.onSuccess {
                val user = authService.getCurrentUser()
                if (user != null && !user.acceptedTnC) {
                    _state.value = AuthState.NeedsTnC
                } else {
                    _state.value = AuthState.Authenticated
                }
            }.onFailure { e ->
                _state.value = AuthState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun setError(message: String) {
        _state.value = AuthState.Error(message)
    }
}

@Composable
fun AuthScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    LaunchedEffect(state) {
        when (state) {
            is AuthViewModel.AuthState.NeedsTnC -> {
                navController.navigate(TrevioRoute.Terms.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthViewModel.AuthState.Authenticated -> {
                navController.navigate(TrevioRoute.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            else -> {}
        }
    }

    fun launchGoogleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        coroutineScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = context,
                )
                handleCredentialResponse(result, viewModel)
            } catch (e: GetCredentialException) {
                viewModel.setError("Sign-in failed: ${e.message}")
            } catch (e: Exception) {
                viewModel.setError("Sign-in failed: ${e.message}")
            }
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_trevio_logo),
                contentDescription = "Trevio",
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Split bills. Simplify life.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(48.dp))
            if (state is AuthViewModel.AuthState.Loading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                OutlinedButton(
                    onClick = { launchGoogleSignIn() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White
                    )
                ) {
                    Text(
                        text = "Continue with Google",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "By continuing, you agree to our Terms & Conditions",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (state is AuthViewModel.AuthState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (state as AuthViewModel.AuthState.Error).message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun handleCredentialResponse(
    result: GetCredentialResponse,
    viewModel: AuthViewModel
) {
    val credential = result.credential
    when (credential.type) {
        GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                viewModel.signInWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: GoogleIdTokenParsingException) {
                viewModel.setError("Failed to parse sign-in token")
            }
        }
        else -> {
            viewModel.setError("Unexpected credential type: ${credential.type}")
        }
    }
}
