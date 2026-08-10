package com.trevio.android.ui.household

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.domain.model.CategoryBreakdown
import com.trevio.android.domain.model.DailyTrend
import com.trevio.android.domain.model.MemberContribution
import com.trevio.android.domain.model.MonthlyReport
import com.trevio.android.util.FormatUtils
import com.trevio.android.util.HouseholdCategories
import java.util.Calendar
import java.util.Locale

@Composable
fun MonthlyReportTab(
    state: HouseholdState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val report = state.monthlyReport

    val isCurrentMonth = state.selectedYear == Calendar.getInstance().get(Calendar.YEAR) &&
        state.selectedMonth == Calendar.getInstance().get(Calendar.MONTH)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Month Navigator ──
        MonthNavigator(
            monthLabel = report?.monthLabel ?: "",
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            nextEnabled = !isCurrentMonth
        )

        Spacer(modifier = Modifier.height(16.dp))

        report?.let { r ->
            // ── Summary Card ──
            MonthlySummaryCard(report = r, currencySymbol = state.currencySymbol)
            Spacer(modifier = Modifier.height(20.dp))

            // ── Budget Progress ──
            r.budget?.let { budget ->
                BudgetProgressCard(
                    spent = r.totalSpent,
                    budget = budget,
                    remaining = r.budgetRemaining,
                    progress = r.budgetProgress,
                    currencySymbol = state.currencySymbol
                )
                Spacer(modifier = Modifier.height(20.dp)
                )
            }

            // ── Month Comparison ──
            r.comparisonWithLastMonth?.let { comparison ->
                MonthComparisonCard(comparison = comparison, currencySymbol = state.currencySymbol)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Daily Trend Chart ──
            if (r.dailyTrend.isNotEmpty()) {
                DailyTrendChart(trend = r.dailyTrend, currencySymbol = state.currencySymbol)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Category Breakdown ──
            if (r.spentByCategory.isNotEmpty()) {
                CategoryBreakdownSection(
                    title = "Where Money Went",
                    breakdown = r.spentByCategory,
                    currencySymbol = state.currencySymbol
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Income Breakdown ──
            if (r.receivedByCategory.isNotEmpty()) {
                CategoryBreakdownSection(
                    title = "Money Received",
                    breakdown = r.receivedByCategory,
                    currencySymbol = state.currencySymbol,
                    isIncome = true
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Who Paid ──
            if (r.memberContributions.isNotEmpty()) {
                WhoPaidSection(contributions = r.memberContributions, currencySymbol = state.currencySymbol)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Insight ──
            state.gamification?.insightMessage?.let { insight ->
                InsightCard(message = insight)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (report == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No data for this month",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MonthNavigator(
    monthLabel: String,
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
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = monthLabel,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(
            onClick = onNext,
            enabled = nextEnabled
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Next month",
                tint = if (nextEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun MonthlySummaryCard(report: MonthlyReport, currencySymbol: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "${report.entryCount} ${if (report.entryCount == 1) "entry" else "entries"} this month",
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
                        text = "$currencySymbol${FormatUtils.formatAmount(report.totalSpent)}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Received", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$currencySymbol${FormatUtils.formatAmount(report.totalReceived)}",
                        fontSize = 26.sp,
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
                val isPositive = report.netAmount >= 0
                Text(
                    text = "${if (isPositive) "+" else "-"}$currencySymbol${FormatUtils.formatAmount(kotlin.math.abs(report.netAmount))}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPositive) Color(0xFF22C55E) else Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
private fun BudgetProgressCard(
    spent: Double,
    budget: Double,
    remaining: Double,
    progress: Double,
    currencySymbol: String
) {
    val isOverBudget = progress > 100
    val progressColor = when {
        progress >= 100 -> Color(0xFFEF4444)
        progress >= 80 -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Budget", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "$currencySymbol${FormatUtils.formatAmount(budget)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isOverBudget) 1f else (progress / 100f).toFloat().coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(progressColor)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$currencySymbol${FormatUtils.formatAmount(spent)} spent",
                    fontSize = 12.sp,
                    color = progressColor
                )
                Text(
                    text = if (isOverBudget) {
                        "$currencySymbol${FormatUtils.formatAmount(kotlin.math.abs(remaining))} over"
                    } else {
                        "$currencySymbol${FormatUtils.formatAmount(remaining)} left"
                    },
                    fontSize = 12.sp,
                    color = if (isOverBudget) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonthComparisonCard(
    comparison: com.trevio.android.domain.model.MonthComparison,
    currencySymbol: String
) {
    val isIncrease = comparison.spentChange > 0
    val changeColor = if (isIncrease) Color(0xFFEF4444) else Color(0xFF22C55E)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = changeColor.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("vs Last Month", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "$currencySymbol${FormatUtils.formatAmount(comparison.lastMonthSpent)} last month",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncrease) "+" else ""}$currencySymbol${FormatUtils.formatAmount(comparison.spentChange)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = changeColor
                )
                Text(
                    text = "${if (isIncrease) "+" else ""}${String.format(Locale.getDefault(), "%.1f", comparison.spentChangePercent)}%",
                    fontSize = 12.sp,
                    color = changeColor
                )
            }
        }
    }
}

@Composable
private fun DailyTrendChart(trend: List<DailyTrend>, currencySymbol: String) {
    val maxSpent = trend.maxOfOrNull { it.totalSpent } ?: 0.0
    val maxBarHeight = 80.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Daily Trend", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxBarHeight + 20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                trend.forEach { day ->
                    val barHeight = if (maxSpent > 0) {
                        ((day.totalSpent / maxSpent) * maxBarHeight.value).toFloat()
                    } else 0f
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(if (barHeight > 0) barHeight.dp else 2.dp)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(
                                    if (day.totalSpent > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                        if (day.day == 1 || day.day % 5 == 0 || day.day == trend.size) {
                            Text(
                                text = "${day.day}",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownSection(
    title: String,
    breakdown: List<CategoryBreakdown>,
    currencySymbol: String,
    isIncome: Boolean = false
) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            breakdown.take(8).forEach { cat ->
                val category = HouseholdCategories.getCategory(cat.category)
                val color = category?.color ?: if (isIncome) Color(0xFF22C55E) else Color(0xFF94A3B8)
                CategoryBreakdownRow(
                    label = category?.label ?: cat.category.replaceFirstChar { it.uppercase() },
                    amount = cat.totalAmount,
                    percentage = cat.percentage,
                    color = color,
                    currencySymbol = currencySymbol
                )
                if (cat != breakdown.take(8).last()) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    label: String,
    amount: Double,
    percentage: Double,
    color: Color,
    currencySymbol: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$currencySymbol${FormatUtils.formatAmount(amount)} · ${String.format(Locale.getDefault(), "%.0f", percentage)}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percentage / 100f).toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun WhoPaidSection(contributions: List<MemberContribution>, currencySymbol: String) {
    Text(
        text = "Who Paid",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            contributions.forEach { member ->
                MemberContributionRow(
                    rank = member.rank,
                    name = member.displayName,
                    amount = member.totalSpent,
                    percentage = member.spentPercentage,
                    currencySymbol = currencySymbol
                )
                if (member != contributions.last()) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun MemberContributionRow(
    rank: Int,
    name: String,
    amount: Double,
    percentage: Double,
    currencySymbol: String
) {
    val medal = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> ""
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (medal.isNotEmpty()) {
            Text(text = medal, fontSize = 20.sp)
        } else {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Text(text = "$rank", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((percentage / 100f).toFloat().coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Text(
            text = "$currencySymbol${FormatUtils.formatAmount(amount)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InsightCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = "Insight",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
