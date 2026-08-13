package com.trevio.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.trevio.android.R
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.core.security.AppLockViewModel
import com.trevio.android.core.security.BiometricAuthenticator
import kotlinx.coroutines.launch

/**
 * Shown once after the user completes phone setup (first login only).
 * Offers to enable Smart Lock (biometric / device credential).
 *
 * - If the user taps "Enable", a biometric prompt is shown. On success,
 *   app lock is enabled and the user proceeds to Main.
 * - If the user taps "Maybe later", the prompt is marked as shown and
 *   the user proceeds to Main. The prompt will not appear again.
 * - If no authenticator is available on the device, the screen is
 *   skipped entirely (auto-navigates to Main).
 */
@Composable
fun BiometricSetupScreen(
    navController: NavHostController,
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val isReady by viewModel.isReady.collectAsState()
    val biometricPromptShown by viewModel.biometricPromptShown.collectAsState()
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Compute once — device biometric availability doesn't change
    // during this screen's lifetime.
    val canAuthenticate = remember { BiometricAuthenticator.canAuthenticate(context) }

    // Derived skip condition: only valid after preferences are loaded.
    val shouldSkip = isReady && (!canAuthenticate || biometricPromptShown)

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
    )

    // Navigate to Main when the skip condition becomes true.
    // Keyed on [shouldSkip] so it fires exactly once when the
    // decision is ready.
    LaunchedEffect(shouldSkip) {
        if (shouldSkip) {
            navigateToMain(navController)
        }
    }

    // Show a loading indicator while preferences are loading OR while
    // waiting for skip navigation to complete.  This prevents the full
    // setup screen content from flashing before the skip decision or
    // before navigation processes.
    if (!isReady || shouldSkip) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.biometric_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.biometric_setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Feature highlights
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                BiometricFeatureRow(
                    icon = Icons.Default.Fingerprint,
                    text = stringResource(R.string.biometric_setup_feature_biometric)
                )
                Spacer(modifier = Modifier.height(14.dp))
                BiometricFeatureRow(
                    icon = Icons.Default.Lock,
                    text = stringResource(R.string.biometric_setup_feature_pin)
                )
                Spacer(modifier = Modifier.height(14.dp))
                BiometricFeatureRow(
                    icon = Icons.Default.PhoneIphone,
                    text = stringResource(R.string.biometric_setup_feature_privacy)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Buttons
        if (isAuthenticating) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Button(
                onClick = {
                    if (activity != null) {
                        errorMessage = null
                        isAuthenticating = true
                        scope.launch {
                            val result = BiometricAuthenticator.authenticate(
                                activity = activity,
                                title = context.getString(R.string.biometric_setup_prompt_title),
                                subtitle = context.getString(R.string.biometric_setup_prompt_subtitle)
                            )
                            isAuthenticating = false
                            result
                                .onSuccess {
                                    // Use the suspend method to ensure
                                    // DataStore writes complete BEFORE
                                    // navigation destroys this nav
                                    // entry's ViewModelStore (which
                                    // would cancel viewModelScope).
                                    viewModel.enableAppLockAndMarkPromptShown()
                                    navigateToMain(navController)
                                }
                                .onFailure { e ->
                                    if (e !is kotlinx.coroutines.CancellationException) {
                                        errorMessage = e.message
                                    }
                                }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(R.string.biometric_setup_enable),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    // Wrap in scope.launch so the suspend write
                    // completes before navigation destroys the VM.
                    scope.launch {
                        viewModel.markPromptShown()
                        navigateToMain(navController)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(R.string.biometric_setup_later),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun BiometricFeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun navigateToMain(navController: NavHostController) {
    navController.navigate(TrevioRoute.Main.route) {
        popUpTo(0) { inclusive = true }
    }
}
