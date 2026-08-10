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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                        text = errorMsg,
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
                dateLabel = dailySummary?.dateLabel ?: "Today",
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
                text = "Entries",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (dailySummary?.entries.isNullOrEmpty()) {
                EmptyEntriesState(isToday = isToday, expenses = state.expenses, onPreviousDay = onPreviousDay)
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
                    text = state.lastSavedMessage ?: "",
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
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
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
                contentDescription = "Next day",
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
                    contentDescription = "Streak",
                    tint = Color(0xFFF97316),
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "${gamification.loggingStreak} day${if (gamification.loggingStreak != 1) "s" else ""}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Streak",
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
                            text = "Logged today",
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
                            contentDescription = "Badge",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = badge.replace("_", " ").replaceFirstChar { it.uppercase() },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "This month",
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
        gamification.insightMessage?.let { insight ->
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
                    contentDescription = "Insight",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFF59E0B)
                )
                Text(
                    text = insight,
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
        listOf(Color(0xFF22C55E).copy(alpha = 0.1f), Color(0xFF0D9488).copy(alpha = 0.05f))
    } else {
        listOf(Color(0xFFEF4444).copy(alpha = 0.1f), Color(0xFFF97316).copy(alpha = 0.05f))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .background(Brush.verticalGradient(gradientColors))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "${summary.entryCount} ${if (summary.entryCount == 1) "entry" else "entries"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Spent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "$currencySymbol${FormatUtils.formatAmount(summary.totalSpent)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Received", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "$currencySymbol${FormatUtils.formatAmount(summary.totalReceived)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22C55E)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Net", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${if (isPositiveNet) "+" else "-"}$currencySymbol${FormatUtils.formatAmount(kotlin.math.abs(summary.netAmount))}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPositiveNet) Color(0xFF22C55E) else Color(0xFFEF4444)
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
                    .background((category?.color ?: Color(0xFF94A3B8)).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category?.icon ?: Icons.Filled.Category,
                    contentDescription = category?.label,
                    tint = category?.color ?: Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
            // Description and payer
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.description.ifBlank { category?.label ?: "Other" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "${entry.paidByName.ifBlank { "Someone" }} · ${timeFormat.format(entry.date)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Amount
            Text(
                text = "${if (isIncome) "+" else "-"}$currencySymbol${FormatUtils.formatAmount(entry.amount)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyEntriesState(
    isToday: Boolean = false,
    expenses: List<Expense> = emptyList(),
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

    val lastEntryDayLabel = remember(recentEntries) {
        if (recentEntries.isEmpty()) ""
        else {
            val latest = recentEntries.first()
            val latestCal = Calendar.getInstance().apply { timeInMillis = latest.date }
            val now = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            if (latestCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                latestCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
            ) "Yesterday"
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
                    text = if (isToday) "No entries yet today" else "No entries for this day",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap Add Entry to log a new entry",
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
                            text = "$lastEntryDayLabel · ${recentEntries.size} ${if (recentEntries.size == 1) "entry" else "entries"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onPreviousDay,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("View all →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                color = (category?.color ?: Color.Gray).copy(alpha = 0.15f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = category?.icon ?: Icons.Default.Category,
                                        contentDescription = category?.label,
                                        modifier = Modifier.size(16.dp),
                                        tint = category?.color ?: Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = entry.description.ifBlank { category?.label ?: "Other" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = FormatUtils.formatAmount(entry.amount),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
