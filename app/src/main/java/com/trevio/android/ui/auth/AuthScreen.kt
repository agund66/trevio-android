package com.trevio.android.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.PressableScale
import com.trevio.android.core.designsystem.theme.*
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
        data object NeedsPhone : AuthState()
        data object Authenticated : AuthState()
        data object Blocked : AuthState()
        data class Error(@androidx.annotation.StringRes val messageResId: Int, val detail: String? = null) : AuthState()
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state

    fun signInWithGoogle(idToken: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val result = authService.signInWithGoogle(idToken)
            result.onSuccess {
                val user = authService.getCurrentUser()
                if (user != null && user.blocked) {
                    authService.signOut()
                    _state.value = AuthState.Blocked
                } else if (user != null && !user.acceptedTnC) {
                    _state.value = AuthState.NeedsTnC
                } else if (user != null && user.phoneNumber.isBlank()) {
                    _state.value = AuthState.NeedsPhone
                } else {
                    _state.value = AuthState.Authenticated
                }
            }.onFailure { e ->
                _state.value = AuthState.Error(R.string.auth_error, e.message)
            }
        }
    }

    fun setError(@androidx.annotation.StringRes messageResId: Int, detail: String? = null) {
        _state.value = AuthState.Error(messageResId, detail)
    }

    fun signInWithGoogleWeb(activity: Activity) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val result = authService.signInWithGoogleWeb(activity)
            result.onSuccess {
                val user = authService.getCurrentUser()
                if (user != null && user.blocked) {
                    authService.signOut()
                    _state.value = AuthState.Blocked
                } else if (user != null && !user.acceptedTnC) {
                    _state.value = AuthState.NeedsTnC
                } else if (user != null && user.phoneNumber.isBlank()) {
                    _state.value = AuthState.NeedsPhone
                } else {
                    _state.value = AuthState.Authenticated
                }
            }.onFailure { e ->
                _state.value = AuthState.Error(R.string.auth_error, e.message)
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
    val webClientId = stringResource(R.string.default_web_client_id)
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    LaunchedEffect(state) {
        when (state) {
            is AuthViewModel.AuthState.NeedsTnC -> {
                navController.navigate(TrevioRoute.Terms.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthViewModel.AuthState.NeedsPhone -> {
                navController.navigate(TrevioRoute.PhoneSetup.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthViewModel.AuthState.Authenticated -> {
                navController.navigate(TrevioRoute.Main.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthViewModel.AuthState.Blocked -> {
                viewModel.setError(R.string.auth_blocked)
            }
            else -> {}
        }
    }

    fun launchGoogleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
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
                    viewModel.setError(R.string.auth_no_account)
                }
            } catch (e: GetCredentialException) {
                viewModel.setError(R.string.auth_error, e.message)
            } catch (e: Exception) {
                viewModel.setError(R.string.auth_error, e.message)
            }
        }
    }

    // Story chapters data
    val isDark = isSystemInDarkTheme()
    val chapters = remember {
        listOf(
            StoryChapterData(
                title = context.getString(R.string.story_chapter1_title),
                description = context.getString(R.string.story_chapter1_desc)
            ) { SplitReceiptMockup() },
            StoryChapterData(
                title = context.getString(R.string.story_chapter2_title),
                description = context.getString(R.string.story_chapter2_desc)
            ) { SplitMethodsMockup() },
            StoryChapterData(
                title = context.getString(R.string.story_chapter3_title),
                description = context.getString(R.string.story_chapter3_desc)
            ) { SettlementMockup() },
            StoryChapterData(
                title = context.getString(R.string.story_chapter4_title),
                description = context.getString(R.string.story_chapter4_desc)
            ) { BudgetInsightsMockup() }
        )
    }

    // Theme-aware text colors
    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryTextColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF475569)
    val tertiaryTextColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B)
    val logoBgColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.6f)

    Box(modifier = Modifier.fillMaxSize()) {
        // Aurora animated background
        AuroraBackground()

        // Main content — scrollable immersive experience
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Hero Section ───
            // Logo + branding
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(logoBgColor),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_trevio_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Hero headline
            Text(
                text = stringResource(R.string.hero_headline),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hero_subheadline),
                fontSize = 14.sp,
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Story carousel
            StoryCarousel(chapters = chapters)

            Spacer(modifier = Modifier.height(24.dp))

            // Google sign-in button
            if (state is AuthViewModel.AuthState.Loading) {
                CircularProgressIndicator(color = primaryTextColor)
            } else {
                PressableScale(
                    onClick = { launchGoogleSignIn() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.auth_sign_in),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Terms notice
            Text(
                text = stringResource(R.string.auth_terms_notice),
                style = MaterialTheme.typography.bodySmall,
                color = tertiaryTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            // Error display
            if (state is AuthViewModel.AuthState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = if (isDark) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = buildString {
                            append(stringResource((state as AuthViewModel.AuthState.Error).messageResId))
                            (state as AuthViewModel.AuthState.Error).detail?.takeIf { it.isNotBlank() }?.let {
                                append(": ")
                                append(it)
                            }
                        },
                        color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // ─── Use Cases Section ───
            Spacer(modifier = Modifier.height(16.dp))
            UseCasesSection()

            // ─── Stats Banner ───
            StatsBanner()

            // ─── How It Works ───
            HowItWorksSection()

            // ─── Final CTA ───
            CTASection(onSignIn = { launchGoogleSignIn() })
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
                viewModel.setError(R.string.auth_error_parse)
            }
        }
        else -> {
            viewModel.setError(R.string.auth_error, credential.type)
        }
    }
}
