package com.trevio.android.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    viewModel.signInWithGoogle(idToken)
                } else {
                    viewModel.setError("Failed to get authentication token")
                }
            } catch (e: ApiException) {
                viewModel.setError("Sign-in failed: ${e.message}")
            }
        } else {
            val statusCode = result.resultCode
            viewModel.setError("Sign-in failed (code: $statusCode)")
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
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(context.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        val signInClient = GoogleSignIn.getClient(context, gso)
                        signInLauncher.launch(signInClient.signInIntent)
                    },
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
