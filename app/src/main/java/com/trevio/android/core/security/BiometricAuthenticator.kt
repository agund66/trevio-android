package com.trevio.android.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around [BiometricPrompt] that exposes a coroutine-friendly
 * [authenticate] function and a [canAuthenticate] availability check.
 *
 * Uses `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` so the system automatically
 * shows whichever authenticator the user has enrolled (fingerprint, face,
 * or device PIN/pattern/password).  No secrets are stored by the app —
 * the OS performs all verification.
 */
object BiometricAuthenticator {

    /** Returns `true` if at least one authenticator (biometric or device credential) is available. */
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the system biometric / device-credential prompt and suspends
     * until the user succeeds, cancels, or an error occurs.
     *
     * On success returns `Result.success(Unit)`.
     * On cancel returns `Result.failure(CancellationException)`.
     * On error returns `Result.failure` with the system error message.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(Result.success(Unit))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        cont.resume(Result.failure(kotlinx.coroutines.CancellationException(errString.toString())))
                    } else {
                        cont.resume(Result.failure(Exception(errString.toString())))
                    }
                }
            }

            // onAuthenticationFailed is called on each individual failed
            // attempt (e.g. wrong finger).  We do NOT resume here — let
            // BiometricPrompt handle retries internally.
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }
}
