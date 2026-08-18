package com.trevio.android.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.domain.model.KarmaBreakdown
import com.trevio.android.domain.model.KarmaComponents
import com.trevio.android.domain.repository.KarmaService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── KarmaViewModel ───────────────────────────────────────────────

@HiltViewModel
class KarmaViewModel @Inject constructor(
    private val karmaService: KarmaService
) : ViewModel() {

    data class KarmaState(
        val breakdown: KarmaBreakdown? = null,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isPublic: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(KarmaState())
    val state: StateFlow<KarmaState> = _state

    init { loadKarma() }

    fun loadKarma() {
        viewModelScope.launch {
            val result = karmaService.getKarmaBreakdown()
            result
                .onSuccess { breakdown ->
                    _state.value = KarmaState(
                        breakdown = breakdown,
                        isLoading = false,
                        isPublic = breakdown.uid.isNotEmpty() // placeholder; service tracks public flag
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun refreshKarma() {
        _state.value = _state.value.copy(isRefreshing = true, error = null)
        viewModelScope.launch {
            val result = karmaService.refreshKarma()
            result
                .onSuccess { breakdown ->
                    _state.value = _state.value.copy(
                        breakdown = breakdown,
                        isRefreshing = false
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        error = e.message
                    )
                }
        }
    }

    fun togglePublic(currentPublic: Boolean) {
        viewModelScope.launch {
            val result = karmaService.setKarmaPublic(!currentPublic)
            result
                .onSuccess {
                    _state.value = _state.value.copy(isPublic = !currentPublic)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
        }
    }
}

// ─── KarmaCard composable ─────────────────────────────────────────

@Composable
fun KarmaCard(
    viewModel: KarmaViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    TrevioCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Header row: Star icon + title + refresh button ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = state.breakdown?.let { tierColor(it.tier) }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.karma_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.refreshKarma() },
                    enabled = !state.isRefreshing
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.karma_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val breakdown = state.breakdown
            if (breakdown == null) {
                // ── No data state ──
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.karma_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.refreshKarma() },
                        enabled = !state.isRefreshing
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.karma_refresh))
                        }
                    }
                }
                return@Column
            }

            // ── Score display ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = breakdown.score.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.karma_score),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ── Tier badge ──
                val tierColor = tierColor(breakdown.tier)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(tierColor.copy(alpha = 0.18f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(tierLabelResId(breakdown.tier)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = tierColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Expandable breakdown toggle row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.karma_breakdown),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Expandable breakdown section ──
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    KarmaComponentRow(
                        label = stringResource(R.string.karma_component_reliability),
                        description = stringResource(R.string.karma_component_reliability_desc),
                        score = breakdown.components.reliabilityScore,
                        max = 300
                    )
                    KarmaComponentRow(
                        label = stringResource(R.string.karma_component_generosity),
                        description = stringResource(R.string.karma_component_generosity_desc),
                        score = breakdown.components.generosityScore,
                        max = 250
                    )
                    KarmaComponentRow(
                        label = stringResource(R.string.karma_component_consistency),
                        description = stringResource(R.string.karma_component_consistency_desc),
                        score = breakdown.components.consistencyScore,
                        max = 200
                    )
                    KarmaComponentRow(
                        label = stringResource(R.string.karma_component_settlement_speed),
                        description = stringResource(R.string.karma_component_settlement_speed_desc),
                        score = breakdown.components.settlementSpeedScore,
                        max = 150
                    )
                    KarmaComponentRow(
                        label = stringResource(R.string.karma_component_group_health),
                        description = stringResource(R.string.karma_component_group_health_desc),
                        score = breakdown.components.groupHealthScore,
                        max = 100
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── Public toggle ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.karma_make_public),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (state.isPublic) {
                            stringResource(R.string.karma_shared)
                        } else {
                            stringResource(R.string.karma_not_shared)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = state.isPublic,
                    onCheckedChange = { viewModel.togglePublic(state.isPublic) }
                )
            }
        }
    }
}

@Composable
private fun KarmaComponentRow(
    label: String,
    description: String,
    score: Int,
    max: Int
) {
    val progress = if (max > 0) (score.toFloat() / max).coerceIn(0f, 1f) else 0f
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
                text = "$score / $max",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

// ─── Tier helpers ─────────────────────────────────────────────────

private fun tierColor(tier: String): Color = when (tier) {
    "platinum" -> Color(0xFFE5E4E2)
    "gold" -> Color(0xFFFFD700)
    "silver" -> Color(0xFFC0C0C0)
    else -> Color(0xFFCD7F32) // bronze
}

private fun tierLabelResId(tier: String): Int = when (tier) {
    "platinum" -> R.string.karma_tier_platinum
    "gold" -> R.string.karma_tier_gold
    "silver" -> R.string.karma_tier_silver
    else -> R.string.karma_tier_bronze
}
