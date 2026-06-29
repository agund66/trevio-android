package com.trevio.android.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.UserService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
        data object Authenticated : SplashState()
    }

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            delay(800)
            if (!authService.isUserAuthenticated()) {
                _state.value = SplashState.NotAuthenticated
                return@launch
            }
            val user = authService.getCurrentUser()
            if (user == null) {
                _state.value = SplashState.NotAuthenticated
            } else if (!user.acceptedTnC) {
                _state.value = SplashState.NeedsTnC
            } else {
                _state.value = SplashState.Authenticated
            }
        }
    }
}

@Composable
fun SplashScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: SplashViewModel = hiltViewModel(),
    pendingInviteCode: String? = null
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            is SplashViewModel.SplashState.NotAuthenticated -> {
                if (pendingInviteCode != null) {
                    navController.navigate(TrevioRoute.JoinGroup.createRoute(pendingInviteCode)) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(TrevioRoute.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is SplashViewModel.SplashState.NeedsTnC -> {
                if (pendingInviteCode != null) {
                    navController.navigate(TrevioRoute.JoinGroup.createRoute(pendingInviteCode)) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(TrevioRoute.Terms.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is SplashViewModel.SplashState.Authenticated -> {
                if (pendingInviteCode != null) {
                    navController.navigate(TrevioRoute.JoinGroup.createRoute(pendingInviteCode)) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(TrevioRoute.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_trevio_logo),
                contentDescription = "Trevio",
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Split bills. Simplify life.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (state is SplashViewModel.SplashState.Loading) {
                CircularProgressIndicator()
            }
        }
    }
}
