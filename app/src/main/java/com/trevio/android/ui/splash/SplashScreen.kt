package com.trevio.android.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.trevio.android.R
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
class SplashViewModel @Inject constructor(
    private val authService: AuthService,
    private val userService: UserService
) : ViewModel() {

    sealed class SplashState {
        data object Loading : SplashState()
        data object NotAuthenticated : SplashState()
        data object NeedsTnC : SplashState()
        data object NeedsPhone : SplashState()
        data object Authenticated : SplashState()
        data object Blocked : SplashState()
    }

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            if (!authService.isUserAuthenticated()) {
                _state.value = SplashState.NotAuthenticated
                return@launch
            }
            val user = authService.getCurrentUser()
            if (user == null) {
                _state.value = SplashState.NotAuthenticated
            } else if (user.blocked) {
                authService.signOut()
                _state.value = SplashState.Blocked
            } else if (!user.acceptedTnC) {
                _state.value = SplashState.NeedsTnC
            } else if (user.phoneNumber.isBlank()) {
                _state.value = SplashState.NeedsPhone
            } else if (user.username.isBlank()) {
                // Auto-repair: generate missing username for existing users
                userService.acceptTnC()
                _state.value = SplashState.Authenticated
            } else {
                _state.value = SplashState.Authenticated
            }
        }
    }
}

@Composable
fun SplashScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Entrance animation triggers.
    var startAnimation by remember { mutableStateOf(false) }
    var fadeOut by remember { mutableStateOf(false) }

    // Kick off the entrance animation on first composition.
    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // When the auth state resolves to anything other than Loading, fade the
    // splash content out before navigating for a smooth transition.
    LaunchedEffect(state) {
        if (state !is SplashViewModel.SplashState.Loading) {
            fadeOut = true
            delay(200)
            when (state) {
                is SplashViewModel.SplashState.NotAuthenticated -> {
                    navController.navigate(TrevioRoute.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is SplashViewModel.SplashState.NeedsTnC -> {
                    navController.navigate(TrevioRoute.Terms.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is SplashViewModel.SplashState.NeedsPhone -> {
                    navController.navigate(TrevioRoute.PhoneSetup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is SplashViewModel.SplashState.Authenticated -> {
                    navController.navigate(TrevioRoute.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is SplashViewModel.SplashState.Blocked -> {
                    navController.navigate(TrevioRoute.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                else -> {}
            }
        }
    }

    // Logo scale-in + fade-in.
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    // Tagline fades in shortly after the logo, with a delay.
    val taglineAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "taglineAlpha"
    )
    // Whole-content fade-out when navigating away.
    val contentAlpha by animateFloatAsState(
        targetValue = if (fadeOut) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.primaryContainer
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(contentAlpha)
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_trevio_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(96.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(taglineAlpha)
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (state is SplashViewModel.SplashState.Loading) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
