package com.trevio.android.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.util.computeGroupAnalytics
import com.trevio.android.util.rememberCurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CATEGORY_COLORS = mapOf(
    "food" to Color(0xFFF97316),
    "transport" to Color(0xFF3B82F6),
    "shopping" to Color(0xFFA855F7),
    "turf" to Color(0xFF22C55E),
    "accommodation" to Color(0xFFEC4899),
    "other" to Color(0xFF94A3B8)
)

private val CATEGORY_LABELS = mapOf(
    "food" to "Food",
    "transport" to "Transport",
    "shopping" to "Shopping",
    "turf" to "Turf",
    "accommodation" to "Stay",
    "other" to "Other"
)

@Composable
fun AnalyticsTab(
    groupId: String,
    groupName: String,
    expenses: List<Expense>,
    members: List<Member>
) {
    val currencyFormatter = rememberCurrencyFormatter()
    val analytics = remember(expenses, members) {
        computeGroupAnalytics(groupId, groupName, expenses, members)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Key Stats Cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Receipt,
                label = "Total",
                value = currencyFormatter.formatBase(analytics.totalExpenses),
                color = MaterialTheme.colorScheme.primary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.BarChart,
                label = "Avg",
                value = currencyFormatter.formatBase(analytics.avgExpenseAmount),
                color = Color(0xFF3B82F6)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Paid,
                label = "Expenses",
                value = analytics.expenseCount.toString(),
                color = Color(0xFFA855F7)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.TrendingUp,
                label = "Recent",
                value = "${analytics.recentActivityRate}%",
                color = Color(0xFF22C55E)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Monthly Trend Chart
        AnalyticsCard(title = "Monthly Spending", icon = Icons.Default.CalendarMonth) {
            val maxTrend = analytics.monthlyTrends.maxOfOrNull { it.totalAmount } ?: 1.0
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                analytics.monthlyTrends.forEach { trend ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        val barHeight = ((trend.totalAmount / maxTrend) * 80.0).coerceAtLeast(2.0)
                        Text(
                            text = if (trend.totalAmount > 0) currencyFormatter.formatBase(trend.totalAmount) else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = trend.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category Breakdown
        if (analytics.categoryBreakdown.isNotEmpty()) {
            AnalyticsCard(title = "By Category", icon = Icons.Default.Insights) {
                analytics.categoryBreakdown.forEach { cat ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(CATEGORY_COLORS[cat.category] ?: Color.Gray)
                                )
                                Text(
                                    text = CATEGORY_LABELS[cat.category] ?: cat.category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "(${cat.expenseCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currencyFormatter.formatBase(cat.totalAmount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${cat.percentage}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (cat.percentage / 100).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = CATEGORY_COLORS[cat.category] ?: Color.Gray,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Top Spenders
        val topSpenders = analytics.memberSpending.filter { it.totalPaid > 0 }.take(5)
        if (topSpenders.isNotEmpty()) {
            val maxPaid = topSpenders.maxOfOrNull { it.totalPaid } ?: 1.0
            AnalyticsCard(title = "Top Spenders", icon = Icons.Default.TrendingUp) {
                topSpenders.forEachIndexed { index, member ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MemberAvatar(
                            name = member.displayName,
                            photoURL = member.photoURL,
                            size = 32
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            LinearProgressIndicator(
                                progress = { (member.totalPaid / maxPaid).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp).clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = currencyFormatter.formatBase(member.totalPaid),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${member.expenseCount} expenses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Highest Expense
        analytics.highestExpense?.let { highest ->
            AnalyticsCard(title = "Biggest Expense", icon = Icons.Default.TrendingUp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = highest.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (highest.date > 0) {
                            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(highest.date))
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = currencyFormatter.formatBase(highest.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
