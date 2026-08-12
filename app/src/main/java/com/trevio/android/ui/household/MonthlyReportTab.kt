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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.resolveLocalizedString
import com.trevio.android.core.designsystem.theme.*
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
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Month Navigator ──
        MonthNavigator(
            monthLabel = resolveLocalizedString(report?.monthLabelText),
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
                    title = stringResource(R.string.monthly_where_money_went),
                    breakdown = r.spentByCategory,
                    currencySymbol = state.currencySymbol
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Income Breakdown ──
            if (r.receivedByCategory.isNotEmpty()) {
                CategoryBreakdownSection(
                    title = stringResource(R.string.monthly_money_received),
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
            state.gamification?.insightMessageText?.let { insight ->
                InsightCard(message = resolveLocalizedString(insight))
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
                    stringResource(R.string.monthly_no_data),
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
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.monthly_previous_month))
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
                contentDescription = stringResource(R.string.monthly_next_month),
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
                text = stringResource(R.string.monthly_entry_count, report.entryCount, if (report.entryCount == 1) stringResource(R.string.daily_entry) else stringResource(R.string.daily_entries)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                Text(stringResource(R.string.monthly_spent), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$currencySymbol${FormatUtils.formatAmount(report.totalSpent)}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = BalanceNegative
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.monthly_received), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$currencySymbol${FormatUtils.formatAmount(report.totalReceived)}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = BalancePositive
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
                Text(stringResource(R.string.monthly_net), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val isPositive = report.netAmount >= 0
                Text(
                    text = "${if (isPositive) "+" else "-"}$currencySymbol${FormatUtils.formatAmount(kotlin.math.abs(report.netAmount))}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPositive) BalancePositive else BalanceNegative
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
        progress >= 100 -> BalanceNegative
        progress >= 80 -> BudgetWarning
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
                Text(stringResource(R.string.daily_budget), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
                    text = "$currencySymbol${FormatUtils.formatAmount(spent)} ${stringResource(R.string.monthly_spent_suffix)}",
                    fontSize = 12.sp,
                    color = progressColor
                )
                Text(
                    text = if (isOverBudget) {
                        "$currencySymbol${FormatUtils.formatAmount(kotlin.math.abs(remaining))} ${stringResource(R.string.monthly_over)}"
                    } else {
                        "$currencySymbol${FormatUtils.formatAmount(remaining)} ${stringResource(R.string.monthly_left)}"
                    },
                    fontSize = 12.sp,
                    color = if (isOverBudget) BalanceNegative else MaterialTheme.colorScheme.onSurfaceVariant
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
    val changeColor = if (isIncrease) BalanceNegative else BalancePositive

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
                Text(stringResource(R.string.monthly_vs_last_month), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "$currencySymbol${FormatUtils.formatAmount(comparison.lastMonthSpent)} ${stringResource(R.string.monthly_last_month_label)}",
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
            Text(stringResource(R.string.monthly_daily_trend), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
                val color = category?.color ?: if (isIncome) BalancePositive else CategoryFallback
                CategoryBreakdownRow(
                    label = category?.labelResId?.let { stringResource(it) } ?: cat.category.replaceFirstChar { it.uppercase() },
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
        text = stringResource(R.string.monthly_who_paid),
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
                contentDescription = stringResource(R.string.monthly_insight),
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
