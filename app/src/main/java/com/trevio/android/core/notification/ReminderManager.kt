package com.trevio.android.core.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.firestore.FirebaseFirestore
import com.trevio.android.data.remote.FirestoreObservers
import com.trevio.android.util.AppConstants
import com.trevio.android.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the daily reminder scheduling by observing:
 * 1. Firebase auth state (is the user logged in?)
 * 2. The user's groups (do they have at least one group?)
 * 3. The reminder config (is it enabled? what time? what overrides?)
 *
 * When all three conditions are met (logged in, has groups, config enabled),
 * the reminder is scheduled.  When any condition changes, the reminder is
 * rescheduled or cancelled accordingly.
 *
 * Started from [com.trevio.android.TrevioApp.onCreate] and runs for the
 * lifetime of the application process.
 */
@Singleton
class ReminderManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val observers: FirestoreObservers,
    private val scheduler: ReminderScheduler
) {

    companion object {
        private const val TAG = "ReminderManager"
    }

    private val scope = CoroutineScope(SupervisorJob())
    private var configJob: Job? = null
    private var authListener: AuthStateListener? = null

    /**
     * Starts observing auth state, user groups, and reminder config.
     * Safe to call multiple times — removes any previous auth listener first.
     */
    fun start() {
        // Remove any existing listener to prevent duplicates on re-call.
        authListener?.let { auth.removeAuthStateListener(it) }
        authListener = AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                configJob?.cancel()
                configJob = null
                scheduler.cancel()
                Logger.i(TAG, "User logged out — cancelled reminders")
            } else {
                startObservingConfigAndGroups(uid)
            }
        }
        auth.addAuthStateListener(authListener!!)
    }

    /**
     * Combines the user's groups and the reminder config to schedule or
     * cancel the daily reminder.  Cancels any previous observation job first.
     */
    private fun startObservingConfigAndGroups(uid: String) {
        configJob?.cancel()
        configJob = scope.launch {
            // Fetch user's timezone (one-time, then use it for scheduling).
            // If the user changes their timezone, it will be picked up on the
            // next reminder fire (ReminderWorker fetches it fresh each time).
            val timezone = try {
                firestore.collection("users").document(uid).get().await()
                    .getString("timezone") ?: AppConstants.DEFAULT_TIMEZONE
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to fetch timezone, using default", e)
                AppConstants.DEFAULT_TIMEZONE
            }

            // Combine groups + config — only schedule when user has groups
            // and config is present and enabled.  retryWhen() keeps the flow
            // alive if a source errors (e.g. transient Firestore offline),
            // re-subscribing with a backoff delay so reminders resume once
            // the error condition clears.
            combine(
                observers.observeUserGroups(),
                observers.observeReminderConfig()
            ) { groups, config ->
                ReminderScheduleState(timezone, groups.isNotEmpty(), config)
            }
                .distinctUntilChanged()
                .retryWhen { e, attempt ->
                    Logger.w(TAG, "Observation error (attempt ${attempt + 1}): ${e.message}", e)
                    kotlinx.coroutines.delay(minOf(30_000L * (attempt + 1), 300_000L))
                    true
                }
                .collect { state ->
                    val config = state.config
                    if (state.hasGroups && config != null && config.enabled) {
                        scheduler.schedule(state.timezone, config)
                        Logger.i(TAG, "Reminder scheduled for user $uid")
                    } else {
                        scheduler.cancel()
                        val reason = when {
                            !state.hasGroups -> "no groups"
                            config == null -> "no config"
                            !config.enabled -> "disabled by admin"
                            else -> "unknown"
                        }
                        Logger.i(TAG, "Reminder cancelled: $reason")
                    }
                }
        }
    }

    private data class ReminderScheduleState(
        val timezone: String,
        val hasGroups: Boolean,
        val config: com.trevio.android.domain.model.ReminderConfig?
    )
}
