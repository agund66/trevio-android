package com.trevio.android.util

import androidx.annotation.StringRes
import com.trevio.android.R
import com.trevio.android.domain.model.SupportCategory
import com.trevio.android.domain.model.SupportPriority
import com.trevio.android.domain.model.SupportStatus

/** Maps a [SupportStatus] enum value to its display string resource ID. */
@StringRes
fun SupportStatus.toStringResId(): Int = when (this) {
    SupportStatus.OPEN -> R.string.support_status_open
    SupportStatus.IN_PROGRESS -> R.string.support_status_in_progress
    SupportStatus.WAITING_USER -> R.string.support_status_waiting_user
    SupportStatus.RESOLVED -> R.string.support_status_resolved
    SupportStatus.CLOSED -> R.string.support_status_closed
}

/** Maps a [SupportPriority] enum value to its display string resource ID. */
@StringRes
fun SupportPriority.toStringResId(): Int = when (this) {
    SupportPriority.LOW -> R.string.support_priority_low
    SupportPriority.MEDIUM -> R.string.support_priority_medium
    SupportPriority.HIGH -> R.string.support_priority_high
    SupportPriority.URGENT -> R.string.support_priority_urgent
}

/** Maps a [SupportCategory] enum value to its display string resource ID. */
@StringRes
fun SupportCategory.toStringResId(): Int = when (this) {
    SupportCategory.CALCULATION -> R.string.support_category_calculation
    SupportCategory.SETTLEMENT -> R.string.support_category_settlement
    SupportCategory.EXPENSE -> R.string.support_category_expense
    SupportCategory.GROUP_ACCESS -> R.string.support_category_group_access
    SupportCategory.PAYMENT_INFO -> R.string.support_category_payment_info
    SupportCategory.ACCOUNT -> R.string.support_category_account
    SupportCategory.BUG -> R.string.support_category_bug
    SupportCategory.OTHER -> R.string.support_category_other
}
