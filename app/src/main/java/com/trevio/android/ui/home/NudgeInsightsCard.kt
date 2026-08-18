package com.trevio.android.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.domain.model.Nudge
import com.trevio.android.domain.repository.NudgeService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── NudgeViewModel ───────────────────────────────────────────────

@HiltViewModel
class NudgeViewModel @Inject constructor(
    private val nudgeService: NudgeService
) : ViewModel() {

    data class NudgeState(
        val nudges: List<Nudge> = emptyList(),
        val isLoading: Boolean = false,
        val isGenerating: Boolean = false
    )

    private val _state = MutableStateFlow(NudgeState())
    val state: StateFlow<NudgeState> = _state

    init { loadNudges() }

    fun loadNudges() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = nudgeService.getActiveNudges()
            result
                .onSuccess { nudges ->
                    _state.value = NudgeState(
                        nudges = nudges,
                        isLoading = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoading = false)
                }
        }
    }

    fun generateNudges() {
        _state.value = _state.value.copy(isGenerating = true)
        viewModelScope.launch {
            nudgeService.generateNudges()
            loadNudges()
            _state.value = _state.value.copy(isGenerating = false)
        }
    }

    fun dismissNudge(nudgeId: String) {
        viewModelScope.launch {
            nudgeService.dismissNudge(nudgeId)
            _state.value = _state.value.copy(
                nudges = _state.value.nudges.filterNot { it.nudgeId == nudgeId }
            )
        }
    }

    fun markRead(nudgeId: String) {
        viewModelScope.launch {
            nudgeService.markNudgeRead(nudgeId)
        }
    }
}

// ─── NudgeInsightsCard composable ─────────────────────────────────

@Composable
fun NudgeInsightsCard(
    viewModel: NudgeViewModel = hiltViewModel(),
    onNudgeAction: ((Nudge) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()

    // ── Empty + not loading: compact "all caught up" card ──
    if (state.nudges.isEmpty() && !state.isLoading) {
        TrevioCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.nudges_no_nudges),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        return
    }

    TrevioCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Header row: AutoAwesome icon + title + count badge ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.nudges_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // ── Count badge ──
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = state.nudges.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (state.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Nudge items (max 3) ──
            val visibleNudges = state.nudges.take(3)
            visibleNudges.forEachIndexed { index, nudge ->
                NudgeItem(
                    nudge = nudge,
                    onAction = { onNudgeAction?.invoke(nudge) },
                    onDismiss = { viewModel.dismissNudge(nudge.nudgeId) }
                )
                if (index != visibleNudges.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ── "View All Insights" if more than 3 ──
            if (state.nudges.size > 3) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { /* TODO: navigate to full insights screen */ },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.nudges_view_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ─── NudgeItem composable ─────────────────────────────────────────

@Composable
private fun NudgeItem(
    nudge: Nudge,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // ── Severity icon ──
        Icon(
            imageVector = severityIcon(nudge.severity),
            contentDescription = null,
            tint = severityColor(nudge.severity),
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        // ── Title + body + optional action ──
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nudge.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = nudge.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (nudge.actionLabel.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = nudge.actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Dismiss button ──
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.nudges_dismiss),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Severity helpers ─────────────────────────────────────────────

private fun severityColor(severity: String): Color = when (severity) {
    "warning" -> Color(0xFFF59E0B) // amber
    "positive" -> Color(0xFF22C55E) // green
    else -> Color(0xFF3B82F6) // blue (info)
}

private fun severityIcon(severity: String) = when (severity) {
    "warning" -> Icons.Default.Warning
    "positive" -> Icons.Default.CheckCircle
    else -> Icons.Default.Info
}
