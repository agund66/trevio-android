package com.trevio.android.util

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Inspects a thrown [Throwable] and, if it represents a network/connectivity failure,
 * returns the user-friendly [ErrorMessages.NETWORK_ERROR] message instead of the
 * raw Firebase exception text (which is technical and confusing for end users).
 *
 * Returns `null` if the exception is NOT network-related, so callers can keep the
 * original error message for non-network failures (e.g. permission-denied, not-found).
 */
fun friendlyNetworkMessage(throwable: Throwable): String? {
    // Firebase network exception — directly a connectivity issue
    if (throwable is FirebaseNetworkException) return ErrorMessages.NETWORK_ERROR

    // Firestore exception with a network-related code
    if (throwable is FirebaseFirestoreException) {
        return when (throwable.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED -> ErrorMessages.NETWORK_ERROR
            else -> null
        }
    }

    // Firebase Auth exception with a network-related error code
    if (throwable is FirebaseAuthException) {
        val errorCode = throwable.errorCode
        if (errorCode == "ERROR_NETWORK_REQUEST_FAILED" ||
            errorCode == "ERROR_INTERNAL_ERROR" ||
            errorCode == "ERROR_TOO_MANY_REQUESTS"
        ) {
            return ErrorMessages.NETWORK_ERROR
        }
        return null
    }

    // Generic I/O failures (timeouts, interrupted streams)
    if (throwable is SocketTimeoutException) return ErrorMessages.NETWORK_ERROR
    if (throwable is IOException && throwable !is com.google.firebase.FirebaseException) {
        return ErrorMessages.NETWORK_ERROR
    }

    // Play Services API exception with a network status
    if (throwable is ApiException) {
        return when (throwable.statusCode) {
            CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.TIMEOUT,
            CommonStatusCodes.CANCELED -> ErrorMessages.NETWORK_ERROR
            else -> null
        }
    }

    return null
}

/**
 * Extension on [Result] that replaces network-related failures with the friendly
 * [ErrorMessages.NETWORK_ERROR] message while preserving non-network errors unchanged.
 *
 * Usage in Firebase service implementations:
 * ```
 * } catch (e: Exception) {
 *     Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message))
 * }
 * ```
 * or directly on an existing Result:
 * ```
 * result.withFriendlyNetworkError()
 * ```
 */
fun <T> Result<T>.withFriendlyNetworkError(): Result<T> {
    val exception = exceptionOrNull() ?: return this
    val friendly = friendlyNetworkMessage(exception) ?: return this
    return Result.failure(Exception(friendly, exception))
}
