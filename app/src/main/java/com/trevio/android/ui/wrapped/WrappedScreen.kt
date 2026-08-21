package com.trevio.android.ui.wrapped

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.AnimatedCounter
import com.trevio.android.core.designsystem.components.ConfettiView
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.theme.TrevioAccent
import com.trevio.android.core.designsystem.theme.TrevioSecondary
import com.trevio.android.core.designsystem.theme.TrevioSecondaryDark
import com.trevio.android.core.designsystem.theme.TrevioSecondaryLight
import com.trevio.android.domain.model.WrappedSummary
import com.trevio.android.domain.repository.WrappedService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─── WrappedViewModel ─────────────────────────────────────────────

@HiltViewModel
class WrappedViewModel @Inject constructor(
    private val wrappedService: WrappedService
) : ViewModel() {

    data class WrappedState(
        val summary: WrappedSummary? = null,
        val isLoading: Boolean = true,
        val isGenerating: Boolean = false,
        val year: Int = currentYear()
    )

    private val _state = MutableStateFlow(WrappedState())
    val state: StateFlow<WrappedState> = _state

    init { loadSummary() }

    fun loadSummary() {
        val year = _state.value.year
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = wrappedService.getWrappedSummary(year)
            result
                .onSuccess { summary ->
                    _state.value = _state.value.copy(
                        summary = summary,
                        isLoading = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        summary = null,
                        isLoading = false
                    )
                }
        }
    }

    fun generateSummary() {
        val year = _state.value.year
        _state.value = _state.value.copy(isGenerating = true)
        viewModelScope.launch {
            val result = wrappedService.generateWrappedSummary(year)
            result
                .onSuccess { summary ->
                    _state.value = _state.value.copy(
                        summary = summary,
                        isGenerating = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isGenerating = false)
                }
        }
    }

    fun setYear(year: Int) {
        _state.value = _state.value.copy(year = year)
        loadSummary()
    }

    companion object {
        fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
    }
}

// ─── WrappedScreen composable ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrappedScreen(
    navController: NavHostController,
    viewModel: WrappedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showConfetti by remember { mutableStateOf(false) }
    val shownConfettiYears = remember { mutableStateMapOf<Int, Boolean>() }

    // Celebrate with confetti when wrapped data finishes loading — once per year.
    LaunchedEffect(state.summary, state.isLoading, state.year) {
        if (state.summary != null && !state.isLoading && shownConfettiYears[state.year] != true) {
            showConfetti = true
            shownConfettiYears[state.year] = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        // ── Gradient header ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = stringResource(R.string.wrapped_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Year selector
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.setYear(state.year - 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "Previous year",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = state.year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = { viewModel.setYear(state.year + 1) },
                        enabled = state.year < WrappedViewModel.currentYear(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Next year",
                            tint = if (state.year < WrappedViewModel.currentYear()) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.summary == null && !state.isGenerating -> {
                // ── Empty state ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.wrapped_no_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.generateSummary() },
                            enabled = !state.isGenerating
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.wrapped_generate))
                        }
                    }
                }
            }

            state.isGenerating -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.wrapped_generating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                val summary = state.summary ?: return
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Hero section
                    WrappedHeroCard(summary = summary, year = state.year)

                    // 2. Stats grid
                    StatsGrid(summary = summary)

                    // 3. Top highlights
                    TopHighlightsCard(summary = summary)

                    // 4. Breakdown by Month
                    val monthEntries = remember(summary.monthlyBreakdown) {
                        summary.monthlyBreakdown.entries
                            .sortedBy { it.key }
                            .map { it.key to it.value }
                    }
                    BreakdownCard(
                        title = stringResource(R.string.wrapped_breakdown_month),
                        entries = monthEntries,
                        labelFor = { month -> stringResource(monthLabelResId(month)) }
                    )

                    // 5. Breakdown by Category
                    BreakdownCard(
                        title = stringResource(R.string.wrapped_breakdown_category),
                        entries = summary.categoryBreakdown.entries
                            .sortedByDescending { it.value }
                            .map { it.key to it.value }
                    )

                    // 6. Breakdown by Group
                    BreakdownCard(
                        title = stringResource(R.string.wrapped_breakdown_group),
                        entries = summary.groupBreakdown.entries
                            .sortedByDescending { it.value }
                            .map { it.key to it.value }
                    )

                    // 7. Refresh button
                    OutlinedButton(
                        onClick = { viewModel.generateSummary() },
                        enabled = !state.isGenerating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.wrapped_refresh))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
        }

        ConfettiView(visible = showConfetti, onComplete = { showConfetti = false })
    }
}

// ─── Hero section ─────────────────────────────────────────────────

@Composable
private fun WrappedHeroCard(summary: WrappedSummary, year: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TrevioSecondary,
                        TrevioSecondaryDark
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.wrapped_your_year) + " $year",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TrevioSecondaryLight
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(personalityLabelResId(summary.personality)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (summary.personalityDesc.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = summary.personalityDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ─── Stats grid ───────────────────────────────────────────────────

@Composable
private fun StatsGrid(summary: WrappedSummary) {
    val fronted = (summary.totalPaid - summary.totalSpent).coerceAtLeast(0.0)
    val stats = listOf(
        StatItem(
            icon = Icons.Default.Payments,
            label = stringResource(R.string.wrapped_total_spent),
            value = summary.totalSpent
        ),
        StatItem(
            icon = Icons.Default.Receipt,
            label = stringResource(R.string.wrapped_expenses_logged),
            value = summary.expenseCount.toDouble(),
            isCount = true
        ),
        StatItem(
            icon = Icons.Default.Group,
            label = stringResource(R.string.wrapped_groups_active),
            value = summary.groupCount.toDouble(),
            isCount = true
        ),
        StatItem(
            icon = Icons.Default.Savings,
            label = stringResource(R.string.wrapped_total_paid),
            value = summary.totalPaid
        ),
        StatItem(
            icon = Icons.Default.VolunteerActivism,
            label = stringResource(R.string.wrapped_total_fronted),
            value = fronted
        ),
        StatItem(
            icon = Icons.Default.TrendingUp,
            label = stringResource(R.string.wrapped_avg_expense),
            value = summary.avgExpense
        )
    )

    // 2-column grid
    val rows = stats.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val flatIndex = rowIndex * 2 + colIndex
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300, delayMillis = minOf(flatIndex, 10) * 50)) + slideInVertically(
                            animationSpec = tween(300, delayMillis = minOf(flatIndex, 10) * 50),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                        StatCard(
                            stat = item,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Pad the last row if it has only one item
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class StatItem(
    val icon: ImageVector,
    val label: String,
    val value: Double,
    val isCount: Boolean = false
)

@Composable
private fun StatCard(stat: StatItem, modifier: Modifier = Modifier) {
    TrevioCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = stat.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (stat.isCount) {
                AnimatedCounter(
                    targetValue = stat.value.toInt(),
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                AnimatedCounter(
                    targetValue = stat.value,
                    prefix = "₹",
                    decimals = 0,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stat.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Top highlights ───────────────────────────────────────────────

@Composable
private fun TopHighlightsCard(summary: WrappedSummary) {
    TrevioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HighlightRow(
                icon = Icons.Default.Category,
                label = stringResource(R.string.wrapped_top_category),
                value = summary.topCategory,
                amount = summary.topCategoryAmount
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            HighlightRow(
                icon = Icons.Default.Group,
                label = stringResource(R.string.wrapped_top_group),
                value = summary.topGroup,
                amount = summary.topGroupAmount
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            HighlightRow(
                icon = Icons.Default.TrendingUp,
                label = stringResource(R.string.wrapped_busiest_month),
                value = if (summary.busiestMonth in 1..12) {
                    stringResource(monthLabelResId(summary.busiestMonth))
                } else {
                    "—"
                },
                amount = summary.busiestMonthAmount
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            HighlightRow(
                icon = Icons.Default.Receipt,
                label = stringResource(R.string.wrapped_largest_expense),
                value = summary.largestExpenseDesc.ifEmpty { "—" },
                amount = summary.largestExpense
            )
        }
    }
}

@Composable
private fun HighlightRow(
    icon: ImageVector,
    label: String,
    value: String,
    amount: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatCurrency(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Breakdown cards (bar visualizations) ─────────────────────────

@Composable
private fun <T> BreakdownCard(
    title: String,
    entries: List<Pair<T, Double>>,
    labelFor: @Composable (T) -> String = { it.toString() }
) {
    TrevioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (entries.isEmpty()) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxAmount = entries.maxOf { it.second }.coerceAtLeast(0.0)
                entries.forEach { (key, amount) ->
                    BreakdownBar(
                        label = labelFor(key),
                        amount = amount,
                        fraction = if (maxAmount > 0) (amount / maxAmount).toFloat() else 0f
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownBar(
    label: String,
    amount: Double,
    fraction: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatCurrency(amount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────

private fun formatCurrency(amount: Double): String = "₹${String.format("%.0f", amount)}"

private fun personalityLabelResId(personality: String): Int = when (personality) {
    "The Generous One" -> R.string.wrapped_personality_generous
    "The Active Splitter" -> R.string.wrapped_personality_active
    "The Big Spender" -> R.string.wrapped_personality_big_spender
    "The Social Butterfly" -> R.string.wrapped_personality_social
    else -> R.string.wrapped_personality_steady
}

private fun monthLabelResId(month: Int): Int = when (month) {
    1 -> R.string.wrapped_month_1
    2 -> R.string.wrapped_month_2
    3 -> R.string.wrapped_month_3
    4 -> R.string.wrapped_month_4
    5 -> R.string.wrapped_month_5
    6 -> R.string.wrapped_month_6
    7 -> R.string.wrapped_month_7
    8 -> R.string.wrapped_month_8
    9 -> R.string.wrapped_month_9
    10 -> R.string.wrapped_month_10
    11 -> R.string.wrapped_month_11
    12 -> R.string.wrapped_month_12
    else -> R.string.wrapped_month_1
}
