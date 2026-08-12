package com.trevio.android.util

import androidx.annotation.StringRes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.trevio.android.R
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Maps a [Throwable]'s message to a localized string resource ID.
 *
 * Returns a specific string resource when the exception message matches a known
 * data-layer error (e.g. "You are not a member of this group"), a network error
 * string for connectivity issues, or [R.string.common_failed] as a fallback.
 */
@StringRes
fun Throwable.toStringResId(): Int {
    val message = this.message ?: return R.string.common_failed

    // Check network errors first (these throw specific Firebase types, not messages)
    friendlyNetworkMessage(this)?.let { return R.string.error_network }

    return ERROR_MESSAGE_MAP[message] ?: R.string.common_failed
}

/**
 * Extension on [Result] that maps failures to a string resource ID.
 * Usage: `result.toErrorResId()`
 */
@StringRes
fun <T> Result<T>.toErrorResId(): Int {
    val exception = exceptionOrNull() ?: return R.string.common_failed
    return exception.toStringResId()
}

/**
 * Inspects a thrown [Throwable] and, if it represents a network/connectivity failure,
 * returns the user-friendly network error message. Returns `null` for non-network errors.
 */
fun friendlyNetworkMessage(throwable: Throwable): String? {
    if (throwable is FirebaseNetworkException) return ErrorMessages.NETWORK_ERROR

    if (throwable is FirebaseFirestoreException) {
        return when (throwable.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED -> ErrorMessages.NETWORK_ERROR
            else -> null
        }
    }

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

    if (throwable is SocketTimeoutException) return ErrorMessages.NETWORK_ERROR
    if (throwable is IOException && throwable !is com.google.firebase.FirebaseException) {
        return ErrorMessages.NETWORK_ERROR
    }

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
 */
fun <T> Result<T>.withFriendlyNetworkError(): Result<T> {
    val exception = exceptionOrNull() ?: return this
    val friendly = friendlyNetworkMessage(exception) ?: return this
    return Result.failure(Exception(friendly, exception))
}

/**
 * Maps known data-layer exception messages to string resource IDs.
 * This preserves specific error context (e.g. "Only group admin can delete the group")
 * while keeping the UI fully localized.
 */
private val ERROR_MESSAGE_MAP: Map<String, Int> = mapOf(
    // Auth errors
    "User not authenticated" to R.string.error_user_not_authenticated,
    "Not authenticated" to R.string.error_user_not_authenticated,
    "Authentication failed: no user returned" to R.string.error_auth_failed,

    // Group membership errors
    "You are not a member of this group" to R.string.error_not_member,
    "You are already a member of this group" to R.string.error_already_member,
    "User is already a member of this group" to R.string.error_user_already_member,
    "Both parties must be group members" to R.string.error_both_parties_must_be_members,
    "Target user is not a member of this group" to R.string.error_target_not_member,
    "Target user is not an active member" to R.string.error_target_not_active,

    // Group/expense not found errors
    "Group not found" to R.string.error_group_not_found,
    "Expense not found" to R.string.error_expense_not_found,
    "Access denied" to R.string.error_access_denied,

    // Group admin errors
    "Only group admin can archive the group" to R.string.error_admin_archive,
    "Only group admin can unarchive the group" to R.string.error_admin_unarchive,
    "Only group admin can delete the group" to R.string.error_admin_delete,
    "Only group admin can update group settings" to R.string.error_admin_update_settings,
    "Only group admin can update budget settings" to R.string.error_admin_update_budget,
    "Only group admin can transfer admin role" to R.string.error_admin_transfer,
    "Only admins can link members" to R.string.error_admins_link_only,
    "Only admins can remove members" to R.string.error_admins_remove_only,
    "You are already the admin" to R.string.error_already_admin,
    "Admin cannot leave. Transfer admin role or delete the group." to R.string.error_admin_cannot_leave,
    "Admin cannot leave" to R.string.error_admin_cannot_leave,
    "Cannot remove another admin" to R.string.error_cannot_remove_admin,
    "Use leave group to remove yourself" to R.string.error_use_leave_group,

    // Expense errors
    "Only the expense creator or group admin can edit this expense" to R.string.error_expense_edit_permission,
    "Only the expense creator or group admin can delete this expense" to R.string.error_expense_delete_permission,
    "Invalid expense data" to R.string.error_invalid_expense,
    "Group ID and Expense ID are required" to R.string.error_group_expense_id_required,
    "Group ID and Item ID are required" to R.string.error_group_item_id_required,
    "Group ID and Location ID are required" to R.string.error_group_location_id_required,

    // Settlement errors
    "Cannot settle with yourself" to R.string.error_cannot_settle_self,
    "You can only record settlements involving yourself" to R.string.error_settle_self_only,

    // Invite errors
    "Invite code is required" to R.string.error_invite_code_required,
    "Invalid invite code" to R.string.error_invalid_invite_code,
    "You cannot invite yourself" to R.string.error_cannot_invite_self,
    "Invitation ID is required" to R.string.error_invitation_id_required,
    "Invitation not found" to R.string.error_invitation_not_found,
    "Invalid invitation" to R.string.error_invalid_invitation,
    "This invitation is not for you" to R.string.error_invitation_not_for_you,
    "Invitation is no longer pending" to R.string.error_invitation_not_pending,

    // Group data errors
    "Invalid group data" to R.string.error_invalid_group_data,
    "Invalid group ID" to R.string.error_invalid_group_id,
    "Group ID is required" to R.string.error_group_id_required,
    "Group ID and username are required" to R.string.error_group_username_required,
    "Cannot delete group with other active members. Remove all members first." to R.string.error_cannot_delete_with_members,

    // User/profile errors
    "User not found" to R.string.error_user_not_found,
    "User document not found" to R.string.error_user_not_found,
    "User profile not found" to R.string.error_user_not_found,
    "Invalid user" to R.string.error_invalid_user,
    "Cannot update another user's profile" to R.string.error_cannot_update_other_profile,
    "Username is already taken" to R.string.error_username_taken,
    "Username must be at least 3 characters" to R.string.error_username_too_short,
    "Username too short after normalization" to R.string.error_username_too_short,
    "Name is required" to R.string.error_name_required,

    // Offline member errors
    "This member is not an offline profile" to R.string.error_not_offline_profile,

    // Admin errors
    "Cannot block yourself" to R.string.error_cannot_block_self,
    "Cannot demote yourself" to R.string.error_cannot_demote_self,

    // Support/ticket errors
    "Ticket not found" to R.string.error_ticket_not_found,
    "Ticket data not found" to R.string.error_ticket_not_found,
    "Access denied: you can only reply to your own tickets" to R.string.error_ticket_reply_denied,

    // Trip errors
    "Trip data not found" to R.string.error_trip_not_found,

    // Notification errors
    "Notification ID is required" to R.string.error_notification_id_required,
    "Notification not found" to R.string.error_notification_not_found,

    // Generic errors
    "Missing required fields" to R.string.error_missing_fields,
    "Failed to get exchange rates" to R.string.error_exchange_rates
)
