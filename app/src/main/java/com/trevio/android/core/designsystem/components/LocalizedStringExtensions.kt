package com.trevio.android.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.trevio.android.domain.model.LocalizedString
import com.trevio.android.util.DateUtils

/**
 * Resolves a [LocalizedString] to a platform [String] within a Compose context.
 * Returns empty string when null. Recursively resolves nested [LocalizedString] args.
 */
@Composable
fun resolveLocalizedString(localized: LocalizedString?): String {
    if (localized == null) return ""
    val resolvedArgs = localized.args.map { arg ->
        if (arg is LocalizedString) resolveLocalizedString(arg) else arg
    }
    return if (resolvedArgs.isEmpty()) {
        stringResource(localized.resId)
    } else {
        stringResource(localized.resId, *resolvedArgs.toTypedArray())
    }
}

/**
 * Formats a timestamp as a relative time string, falling back to a short date
 * for timestamps older than 7 days. Resolves within a Compose context.
 */
@Composable
fun formatRelativeTimeText(timestamp: Long): String {
    return resolveLocalizedString(DateUtils.formatRelativeTime(timestamp))
        .ifEmpty { DateUtils.formatShortDate(timestamp) }
}
