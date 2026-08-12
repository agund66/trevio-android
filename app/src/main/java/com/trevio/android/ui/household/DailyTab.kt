package com.trevio.android.ui.household

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.resolveLocalizedString
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.domain.model.DailySummary
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.HouseholdGamification
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.util.DateUtils
import com.trevio.android.util.FormatUtils
import com.trevio.android.util.HouseholdCategories
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DailyTab(
    state: HouseholdState,
    onFullFormClick: () -> Unit,
    onViewEntry: (Expense) -> Unit,
    onEditEntry: (Expense) -> Unit,
    onDeleteEntry: (Expense) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dailySummary = state.dailySummary
    val gamification = state.gamification

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Loading State ──
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // ── Error State ──
            state.error?.let { errorMsg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = stringResource(errorMsg),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val isToday = DateUtils.isSameDay(state.selectedDate, System.currentTimeMillis())

            // ── Date Navigator ──
            DateNavigator(
                dateLabel = resolveLocalizedString(dailySummary?.dateLabelText),
                onPrevious = onPreviousDay,
                onNext = onNextDay,
                nextEnabled = !isToday
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Gamification Row ──
            gamification?.let { g ->
                GamificationRow(gamification = g, isToday = isToday)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Summary Card ──
            dailySummary?.let { summary ->
                DailySummaryCard(summary = summary, currencySymbol = state.currencySymbol)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Today's Entries ──
            Text(
                text = stringResource(R.string.daily_recent_entries),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (dailySummary?.entries.isNullOrEmpty()) {
                EmptyEntriesState(isToday = isToday, expenses = state.expenses, currencySymbol = state.currencySymbol, onPreviousDay = onPreviousDay)
            } else {
                dailySummary?.entries?.forEach { entry ->
                    EntryCard(
                        entry = entry,
                        currencySymbol = state.currencySymbol,
                        onView = { onViewEntry(entry) },
                        onEdit = { onEditEntry(entry) },
                        onDelete = { onDeleteEntry(entry) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // ── Save Success Toast ──
        AnimatedVisibility(
            visible = state.saveSuccess && state.lastSavedMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .padding(bottom = 200.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = resolveLocalizedString(state.lastSavedMessage),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DateNavigator(
    dateLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.daily_previous_day))
        }
        Text(
            text = dateLabel,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.daily_next_day),
                tint = if (nextEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun GamificationRow(gamification: HouseholdGamification, isToday: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // Streak card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = stringResource(R.string.daily_streak),
                    tint = StreakFire,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "${gamification.loggingStreak} ${stringResource(R.string.daily_days)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.daily_streak),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Participation card — only when viewing today
        if (isToday) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ParticipationDots(
                        logged = gamification.membersLoggedToday,
                        total = gamification.totalMembers
                    )
                    Column {
                        Text(
                            text = "${gamification.membersLoggedToday}/${gamification.totalMembers}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.daily_logged_today),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Show monthly badge or insight when not today
            gamification.monthlyBadge?.let { badge ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Category,
                            contentDescription = stringResource(R.string.daily_badge),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = resolveLocalizedString(badge),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.group_detail_monthly),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        }

        // Insight message
        gamification.insightMessageText?.let { insight ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = stringResource(R.string.monthly_insight),
                    modifier = Modifier.size(14.dp),
                    tint = BudgetWarning
                )
                Text(
                    text = resolveLocalizedString(insight),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ParticipationDots(logged: Int, total: Int) {
    if (total == 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until total) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i < logged) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun DailySummaryCard(summary: DailySummary, currencySymbol: String) {
    val isPositiveNet = summary.netAmount >= 0
    val gradientColors = if (isPositiveNet) {
        listOf(BalancePositive.copy(alpha = 0.1f), CategoryAccommodation.copy(alpha = 0.05f))
    } else {
        listOf(BalanceNegative.copy(alpha = 0.1f), StreakFire.copy(alpha = 0.05f))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .background(Brush.verticalGradient(gradientColors))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.daily_entries_count, summary.entryCount, if (summary.entryCount == 1) stringResource(R.string.daily_entry) else stringResource(R.string.daily_entries)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.daily_spent), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "$currencySymbol${FormatUtils.formatAmount(summary.totalSpent)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BalanceNegative
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.daily_received), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "$currencySymbol${FormatUtils.formatAmount(summary.totalReceived)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BalancePositive
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.daily_net), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${if (isPositiveNet) "+" else "-"}$currencySymbol${FormatUtils.formatAmount(kotlin.math.abs(summary.netAmount))}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPositiveNet) BalancePositive else BalanceNegative
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: Expense,
    currencySymbol: String = "₹",
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = HouseholdCategories.getCategory(entry.category)
    val isIncome = entry.transactionType == TransactionType.INCOME
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background((category?.color ?: CategoryFallback).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category?.icon ?: Icons.Filled.Category,
                    contentDescription = category?.labelResId?.let { stringResource(it) },
                    tint = category?.color ?: CategoryFallback,
                    modifier = Modifier.size(20.dp)
                )
            }
            // Description and payer
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.description.ifBlank { category?.labelResId?.let { stringResource(it) } ?: stringResource(R.string.daily_other) },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "${entry.paidByName.ifBlank { stringResource(R.string.daily_someone) }} · ${timeFormat.format(entry.date)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Amount
            Text(
                text = "${if (isIncome) "+" else "-"}$currencySymbol${FormatUtils.formatAmount(entry.amount)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) BalancePositive else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyEntriesState(
    isToday: Boolean = false,
    expenses: List<Expense> = emptyList(),
    currencySymbol: String = "₹",
    onPreviousDay: () -> Unit = {}
) {
    // Find the most recent day with entries (if today is empty)
    val recentEntries = remember(expenses, isToday) {
        if (!isToday || expenses.isEmpty()) emptyList()
        else {
            val now = Calendar.getInstance()
            val sorted = expenses.sortedByDescending { it.date }
            if (sorted.isEmpty()) return@remember emptyList()
            val latest = sorted.first()
            val latestCal = Calendar.getInstance().apply { timeInMillis = latest.date }
            // If latest entry is today, no hint needed
            if (latestCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                latestCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
            ) return@remember emptyList()
            // Return entries from the latest entry's day
            sorted.filter { entry ->
                val cal = Calendar.getInstance().apply { timeInMillis = entry.date }
                cal.get(Calendar.YEAR) == latestCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == latestCal.get(Calendar.DAY_OF_YEAR)
            }.take(3)
        }
    }

    val yesterdayLabel = stringResource(R.string.daily_yesterday)
    val lastEntryDayLabel = remember(recentEntries, yesterdayLabel) {
        if (recentEntries.isEmpty()) ""
        else {
            val latest = recentEntries.first()
            val latestCal = Calendar.getInstance().apply { timeInMillis = latest.date }
            val now = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            if (latestCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                latestCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
            ) yesterdayLabel
            else {
                val fmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                fmt.format(latest.date)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isToday) stringResource(R.string.daily_no_entries) else stringResource(R.string.daily_no_entries),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.daily_tap_add),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Recent entries preview when today is empty
        if (recentEntries.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$lastEntryDayLabel · ${stringResource(R.string.daily_entries_count, recentEntries.size, if (recentEntries.size == 1) stringResource(R.string.daily_entry) else stringResource(R.string.daily_entries))}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onPreviousDay,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(stringResource(R.string.daily_view_all), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    recentEntries.forEach { entry ->
                        val category = HouseholdCategories.getCategory(entry.category)
                        val isIncome = entry.transactionType == TransactionType.INCOME
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (category?.color ?: CategoryFallback).copy(alpha = 0.15f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = category?.icon ?: Icons.Default.Category,
                                        contentDescription = category?.labelResId?.let { stringResource(it) },
                                        modifier = Modifier.size(16.dp),
                                        tint = category?.color ?: CategoryFallback
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = entry.description.ifBlank { category?.labelResId?.let { stringResource(it) } ?: stringResource(R.string.daily_other) },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${if (isIncome) "+" else "-"}$currencySymbol${FormatUtils.formatAmount(entry.amount)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) BalancePositive else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
