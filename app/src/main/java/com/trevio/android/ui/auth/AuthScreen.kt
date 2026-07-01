package com.trevio.android.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.app.Activity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trevio.android.R
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
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

    fun signInWithGoogleWeb(activity: Activity) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val result = authService.signInWithGoogleWeb(activity)
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
                navController.navigate(TrevioRoute.Main.route) {
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
            } catch (e: NoCredentialException) {
                val activity = context as? Activity
                if (activity != null) {
                    viewModel.signInWithGoogleWeb(activity)
                } else {
                    viewModel.setError("No Google accounts found. Please add a Google account in Settings.")
                }
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
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
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
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_trevio_logo),
                    contentDescription = "Trevio",
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Trevio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Split bills. Simplify life.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(32.dp))
            FeaturePoint("Track expenses with groups")
            Spacer(modifier = Modifier.height(8.dp))
            FeaturePoint("Split bills with friends instantly")
            Spacer(modifier = Modifier.height(8.dp))
            FeaturePoint("Settle up via UPI with one tap")

            Spacer(modifier = Modifier.height(40.dp))
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
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = (state as AuthViewModel.AuthState.Error).message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePoint(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
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
