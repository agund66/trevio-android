package com.trevio.android.core.security

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Central ViewModel for the Smart Lock (app lock) feature.
 *
 * Exposes:
 * - [isReady]: false until DataStore preferences have been loaded.
 *   Used by [MainActivity] to keep the splash screen visible so no app
 *   content flashes before the lock gate decision is made.
 * - [appLockEnabled]: whether the user has turned on Smart Lock.
 * - [biometricPromptShown]: whether the first-login biometric prompt has
 *   been shown (prevents re-prompting users who dismissed it).
 * - [isLocked]: whether the lock screen overlay should be visible.
 *
 * Re-lock logic:
 * - On cold start, if [appLockEnabled] is true, [isLocked] starts as true.
 * - When the app goes to background ([ProcessLifecycleOwner] onStop),
 *   [isLocked] is set to true if [appLockEnabled] is true.
 * - When the user successfully authenticates via [BiometricAuthenticator],
 *   [unlock] sets [isLocked] to false.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val preferences = AppLockPreferences(context)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _appLockEnabled = MutableStateFlow(false)
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled

    private val _biometricPromptShown = MutableStateFlow(false)
    val biometricPromptShown: StateFlow<Boolean> = _biometricPromptShown

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    /** Stored so it can be removed in [onCleared] to avoid leaking the ViewModel. */
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // App went to background — lock if enabled so the lock
            // screen is showing when the user returns.
            if (_appLockEnabled.value) {
                _isLocked.value = true
            }
        }
    }

    init {
        loadInitialPreferences()
        observePreferenceChanges()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onCleared()
    }

    /**
     * Reads the initial values from DataStore (once) to set [isReady]
     * and the cold-start [isLocked] state.  This must complete before
     * the splash screen is dismissed (see [isReady]).
     */
    private fun loadInitialPreferences() {
        viewModelScope.launch {
            val enabled = preferences.appLockEnabled.first()
            val promptShown = preferences.biometricPromptShown.first()
            _appLockEnabled.value = enabled
            _biometricPromptShown.value = promptShown
            // Lock on cold start if app lock is enabled
            _isLocked.value = enabled
            _isReady.value = true
        }
    }

    /**
     * Continuously observes the DataStore preference flows so that
     * changes made by other [AppLockViewModel] instances (e.g. the
     * nav-entry-scoped instances in [MoreScreen] or
     * [BiometricSetupScreen]) are reflected in this activity-scoped
     * instance.  Without this, enabling/disabling Smart Lock from
     * MoreScreen would not be seen by the activity-scoped VM that
     * controls the lock overlay and re-lock logic.
     *
     * When [appLockEnabled] transitions to false, [isLocked] is also
     * set to false to dismiss any active lock screen.  When it
     * transitions to true, we do NOT lock immediately — the user just
     * enabled it, so the lock applies on next background→foreground.
     */
    private fun observePreferenceChanges() {
        viewModelScope.launch {
            preferences.appLockEnabled.collect { newValue ->
                _appLockEnabled.value = newValue
                if (!newValue) {
                    _isLocked.value = false
                }
            }
        }
        viewModelScope.launch {
            preferences.biometricPromptShown.collect { newValue ->
                _biometricPromptShown.value = newValue
            }
        }
    }

    /** Called after successful biometric/device-credential authentication. */
    fun unlock() {
        _isLocked.value = false
    }

    /**
     * Enable Smart Lock — persists to DataStore. Does NOT lock in the
     * current session (the user just authenticated to enable it). The
     * lock will apply on next cold start or background→foreground.
     */
    fun enableAppLock() {
        viewModelScope.launch {
            preferences.setAppLockEnabled(true)
            _appLockEnabled.value = true
        }
    }

    /** Disable Smart Lock — persists to DataStore and unlocks. */
    fun disableAppLock() {
        viewModelScope.launch {
            preferences.setAppLockEnabled(false)
            _appLockEnabled.value = false
            _isLocked.value = false
        }
    }

    /** Mark the first-login biometric prompt as shown (don't ask again). */
    fun markBiometricPromptShown() {
        viewModelScope.launch {
            preferences.setBiometricPromptShown(true)
            _biometricPromptShown.value = true
        }
    }

    /**
     * Suspend version of [enableAppLock] + [markBiometricPromptShown].
     *
     * Used by [BiometricSetupScreen] where navigation happens immediately
     * after.  The fire-and-forget methods launch in [viewModelScope],
     * which gets cancelled when the nav entry is destroyed by
     * `popUpTo(0) { inclusive = true }`.  This suspend method runs in
     * the caller's coroutine scope (the composition scope), ensuring
     * the DataStore writes complete before navigation destroys the VM.
     */
    suspend fun enableAppLockAndMarkPromptShown() {
        preferences.setAppLockEnabled(true)
        preferences.setBiometricPromptShown(true)
        _appLockEnabled.value = true
        _biometricPromptShown.value = true
    }

    /**
     * Suspend version of [markBiometricPromptShown].
     *
     * Used by [BiometricSetupScreen]'s "Maybe later" button for the
     * same reason as [enableAppLockAndMarkPromptShown].
     */
    suspend fun markPromptShown() {
        preferences.setBiometricPromptShown(true)
        _biometricPromptShown.value = true
    }

    /**
     * Suspend version of [enableAppLock].
     *
     * Used by [MoreScreen] where the user might navigate away
     * immediately after toggling.  Runs in the caller's coroutine
     * scope (composition scope) rather than [viewModelScope], so
     * the DataStore write survives nav-entry destruction.
     */
    suspend fun enableAppLockSuspend() {
        preferences.setAppLockEnabled(true)
        _appLockEnabled.value = true
    }

    /**
     * Suspend version of [disableAppLock].
     *
     * Used by [MoreScreen] for the same reason as [enableAppLockSuspend].
     */
    suspend fun disableAppLockSuspend() {
        preferences.setAppLockEnabled(false)
        _appLockEnabled.value = false
        _isLocked.value = false
    }
}
