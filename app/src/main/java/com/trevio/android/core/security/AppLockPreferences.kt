package com.trevio.android.core.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock_preferences")

/**
 * Persists the app-lock (Smart Lock) preference and the one-time
 * biometric-setup-prompt flag.  Mirrors the [ThemePreferences] pattern.
 *
 * - [appLockEnabled]: true when the user has turned on Smart Lock.
 * - [biometricPromptShown]: true after the first-login prompt has been
 *   shown (either enabled or dismissed).  Prevents re-prompting users
 *   who said "Maybe later".
 */
class AppLockPreferences(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("app_lock_enabled")
    private val promptShownKey = booleanPreferencesKey("biometric_prompt_shown")

    val appLockEnabled: Flow<Boolean> = context.appLockDataStore.data
        .map { prefs -> prefs[enabledKey] ?: false }

    val biometricPromptShown: Flow<Boolean> = context.appLockDataStore.data
        .map { prefs -> prefs[promptShownKey] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.appLockDataStore.edit { prefs ->
            prefs[enabledKey] = enabled
        }
    }

    suspend fun setBiometricPromptShown(shown: Boolean) {
        context.appLockDataStore.edit { prefs ->
            prefs[promptShownKey] = shown
        }
    }
}
