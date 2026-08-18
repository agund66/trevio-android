package com.trevio.android.ui.group

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.BalancePositive
import com.trevio.android.core.designsystem.theme.BalancePositiveDark
import com.trevio.android.core.designsystem.theme.SuccessTextDark
import com.trevio.android.core.designsystem.theme.SuccessTextLight
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.model.Member
import com.trevio.android.util.CurrencyConverter
import com.trevio.android.util.MemberRole
import com.trevio.android.util.MemberStatus
import com.trevio.android.util.toStringResId
import com.trevio.android.util.rememberCurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    private val groupService: GroupService,
    private val settlementService: SettlementService,
    private val authService: AuthService,
    private val exchangeRateService: com.trevio.android.domain.repository.ExchangeRateService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class SettingsState(
        val isLoading: Boolean = true,
        val groupInfo: GroupInfo? = null,
        val members: List<Member> = emptyList(),
        val currentUserId: String? = null,
        val name: String = "",
        val description: String = "",
        val monthlyBudget: String = "",
        val userCurrency: String = com.trevio.android.util.AppConstants.BASE_CURRENCY,
        val currencySymbol: String = "₹",
        val rates: Map<String, Double> = emptyMap(),
        @StringRes val error: Int? = null,
        @StringRes val success: Int? = null,
        val transferTargetUid: String? = null,
        val showDeleteConfirm: Boolean = false,
        val showLeaveConfirm: Boolean = false,
        val isSaving: Boolean = false,
        val savedAndNavigateBack: Boolean = false
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init { loadData() }

    fun loadData() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val uid = authService.getCurrentUserId()
            val user = authService.getCurrentUser()
            val userCurrency = user?.defaultCurrency ?: com.trevio.android.util.AppConstants.BASE_CURRENCY
            val info = groupService.getGroupInfo(groupId).getOrNull()
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            val groupCurrency = info?.currency ?: userCurrency
            val currencySymbol = CurrencyConverter.getCurrencySymbol(groupCurrency)
            val rates = emptyMap<String, Double>()
            val budgetInGroupCurrency = info?.monthlyBudget
            _state.value = _state.value.copy(
                isLoading = false,
                groupInfo = info,
                members = members,
                currentUserId = uid,
                name = info?.name ?: "",
                description = info?.description ?: "",
                monthlyBudget = budgetInGroupCurrency?.let { formatBudgetDisplay(it) } ?: "",
                userCurrency = userCurrency,
                currencySymbol = currencySymbol,
                rates = rates
            )
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            val uid = authService.getCurrentUserId()
            val info = groupService.getGroupInfo(groupId).getOrNull()
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            val groupCurrency = info?.currency ?: _state.value.userCurrency
            val currencySymbol = CurrencyConverter.getCurrencySymbol(groupCurrency)
            val rates = _state.value.rates
            val budgetInGroupCurrency = info?.monthlyBudget
            _state.value = _state.value.copy(
                groupInfo = info,
                members = members,
                currentUserId = uid,
                name = info?.name ?: "",
                description = info?.description ?: "",
                monthlyBudget = budgetInGroupCurrency?.let { formatBudgetDisplay(it) } ?: "",
                currencySymbol = currencySymbol,
                rates = rates
            )
        }
    }

    private fun formatBudgetDisplay(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            String.format("%.2f", amount)
        }
    }

    fun updateName(v: String) { _state.value = _state.value.copy(name = v) }
    fun updateDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun updateMonthlyBudget(v: String) { _state.value = _state.value.copy(monthlyBudget = v.filter { it.isDigit() || it == '.' }) }

    fun setTransferTarget(uid: String?) { _state.value = _state.value.copy(transferTargetUid = uid) }
    fun setShowDeleteConfirm(v: Boolean) { _state.value = _state.value.copy(showDeleteConfirm = v) }
    fun setShowLeaveConfirm(v: Boolean) { _state.value = _state.value.copy(showLeaveConfirm = v) }

    val isAdmin: Boolean get() = _state.value.currentUserId == _state.value.groupInfo?.createdBy ||
        _state.value.members.find { it.uid == _state.value.currentUserId }?.role == MemberRole.ADMIN

    /**
     * Saves group details (name + description) and, for household groups,
     * the monthly budget in a single operation. On success, signals the
     * UI to navigate back to the previous screen.
     */
    fun saveAllAndNavigateBack() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.value = s.copy(error = R.string.group_settings_name_required)
            return
        }
        val isHousehold = s.groupInfo?.template == com.trevio.android.domain.model.GroupTemplate.HOUSEHOLD
        val budgetValue = if (isHousehold) s.monthlyBudget.toDoubleOrNull() else null
        if (isHousehold && s.monthlyBudget.isNotBlank() && (budgetValue == null || budgetValue < 0)) {
            _state.value = s.copy(error = R.string.group_settings_budget_error)
            return
        }
        val budgetInGroupCurrency = if (isHousehold && budgetValue != null && budgetValue > 0) {
            budgetValue
        } else null

        _state.value = s.copy(isSaving = true, error = null, success = null)
        viewModelScope.launch {
            // Step 1: update group name + description
            val detailsResult = groupService.updateGroup(groupId, s.name, s.description)
            if (detailsResult.isFailure) {
                _state.value = s.copy(isSaving = false, error = detailsResult.exceptionOrNull()?.toStringResId())
                return@launch
            }
            // Step 2: update budget for household groups
            if (isHousehold) {
                val budgetResult = groupService.updateGroupBudget(groupId, budgetInGroupCurrency, null)
                if (budgetResult.isFailure) {
                    _state.value = s.copy(isSaving = false, error = budgetResult.exceptionOrNull()?.toStringResId())
                    return@launch
                }
            }
            _state.value = s.copy(
                isSaving = false,
                success = R.string.group_settings_updated,
                savedAndNavigateBack = true
            )
        }
    }

    fun consumeNavigateBack() {
        _state.value = _state.value.copy(savedAndNavigateBack = false)
    }

    fun transferAdmin() {
        val s = _state.value
        val targetUid = s.transferTargetUid ?: return
        _state.value = s.copy(isSaving = true, error = null, success = null)
        viewModelScope.launch {
            groupService.transferAdminRole(groupId, targetUid)
                .onSuccess {
                    _state.value = s.copy(isSaving = false, success = R.string.group_settings_admin_transferred, transferTargetUid = null)
                    refreshData()
                }
                .onFailure { e ->
                    _state.value = s.copy(isSaving = false, error = e.toStringResId())
                }
        }
    }

    fun updateMemberRole(memberUid: String, role: String) {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null, success = null)
        viewModelScope.launch {
            groupService.updateMemberRole(groupId, memberUid, role)
                .onSuccess {
                    _state.value = s.copy(isSaving = false, success = R.string.group_settings_role_updated)
                    refreshData()
                }
                .onFailure { e ->
                    _state.value = s.copy(isSaving = false, error = e.toStringResId())
                }
        }
    }

    fun deleteGroup() {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            groupService.deleteGroup(groupId)
                .onSuccess {
                    _state.value = s.copy(isSaving = false, showDeleteConfirm = false, groupInfo = null)
                }
                .onFailure { e ->
                    _state.value = s.copy(isSaving = false, error = e.toStringResId(), showDeleteConfirm = false)
                }
        }
    }

    fun leaveGroup() {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            groupService.leaveGroup(groupId)
                .onSuccess {
                    _state.value = s.copy(isSaving = false, showLeaveConfirm = false, groupInfo = null)
                }
                .onFailure { e ->
                    _state.value = s.copy(isSaving = false, error = e.toStringResId(), showLeaveConfirm = false)
                }
        }
    }
}

@Composable
fun GroupSettingsScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: GroupSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(
                title = stringResource(R.string.group_settings_title),
                onBack = { navController.popBackStack() }
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    if (!viewModel.isAdmin) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(
                title = stringResource(R.string.group_settings_title),
                onBack = { navController.popBackStack() }
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.group_settings_admin_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    val activeMembers = state.members.filter { it.status == MemberStatus.ACTIVE && it.uid != state.currentUserId }
    val onlineActiveMembers = activeMembers.filter { !it.isOffline }
    val isHousehold = state.groupInfo?.template == com.trevio.android.domain.model.GroupTemplate.HOUSEHOLD

    LaunchedEffect(state.savedAndNavigateBack) {
        if (state.savedAndNavigateBack) {
            viewModel.consumeNavigateBack()
            navController.popBackStack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = stringResource(R.string.group_settings_title),
            onBack = { navController.popBackStack() }
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(state.error!!), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (state.success != null && !state.savedAndNavigateBack) {
                val isDark = isSystemInDarkTheme()
                val successColor = if (isDark) BalancePositiveDark else BalancePositive
                val successTextColor = if (isDark) SuccessTextDark else SuccessTextLight
                Surface(color = successColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(state.success!!), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = successTextColor)
                }
            }

            // ── Group Details + Budget (single unified form) ──
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.group_settings_group_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text(stringResource(R.string.group_settings_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { viewModel.updateDescription(it) },
                        label = { Text(stringResource(R.string.group_settings_description_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    // Budget field for household groups — inside the same card
                    if (isHousehold) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Text(stringResource(R.string.group_settings_monthly_budget), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.group_settings_budget_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = state.monthlyBudget,
                            onValueChange = { viewModel.updateMonthlyBudget(it) },
                            label = { Text(stringResource(R.string.group_settings_budget_amount)) },
                            prefix = { Text(state.currencySymbol) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    // Single Save button for all fields
                    Button(
                        onClick = { viewModel.saveAllAndNavigateBack() },
                        enabled = state.name.isNotBlank() && !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(stringResource(R.string.group_settings_save))
                    }
                }
            }

            // Manage Admins Section — allows admin to promote/demote members
            if (viewModel.isAdmin && onlineActiveMembers.isNotEmpty()) {
                var roleUpdateTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.group_settings_manage_admins), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.group_settings_manage_admins_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        onlineActiveMembers.forEach { m ->
                            val isMemberAdmin = m.role == MemberRole.ADMIN
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberAvatar(name = m.displayName, photoURL = m.photoURL, size = 32)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(m.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        if (isMemberAdmin) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(stringResource(R.string.group_settings_admin_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                            }
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { roleUpdateTarget = Pair(m.uid, if (isMemberAdmin) "member" else "admin") },
                                        enabled = !state.isSaving,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (isMemberAdmin) stringResource(R.string.group_settings_remove_admin) else stringResource(R.string.group_settings_make_admin),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Role update confirmation dialog
                roleUpdateTarget?.let { (targetUid, newRole) ->
                    val targetMember = onlineActiveMembers.find { it.uid == targetUid }
                    val targetName = targetMember?.displayName ?: ""
                    AlertDialog(
                        onDismissRequest = { roleUpdateTarget = null },
                        title = { Text(if (newRole == "admin") stringResource(R.string.group_settings_make_admin) else stringResource(R.string.group_settings_remove_admin)) },
                        text = {
                            Text(
                                if (newRole == "admin")
                                    stringResource(R.string.group_settings_make_admin_confirm, targetName)
                                else
                                    stringResource(R.string.group_settings_remove_admin_confirm, targetName)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.updateMemberRole(targetUid, newRole)
                                    roleUpdateTarget = null
                                }
                            ) { Text(stringResource(R.string.common_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { roleUpdateTarget = null }) {
                                Text(stringResource(R.string.group_detail_cancel))
                            }
                        }
                    )
                }
            }

            // Danger Zone
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.group_settings_danger_zone), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.group_settings_delete_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    Text(stringResource(R.string.group_settings_delete_sole_member), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    if (!state.showDeleteConfirm) {
                        OutlinedButton(
                            onClick = { viewModel.setShowDeleteConfirm(true) },
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.group_settings_delete))
                        }
                    } else {
                        Text(stringResource(R.string.group_settings_delete_absolute), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.deleteGroup() },
                                enabled = !state.isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                else Text(stringResource(R.string.group_settings_yes_delete))
                            }
                            OutlinedButton(onClick = { viewModel.setShowDeleteConfirm(false) }) { Text(stringResource(R.string.group_detail_cancel)) }
                        }
                    }
                }
            }

            // Leave Group (non-admins only)
            if (!viewModel.isAdmin) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.group_settings_leave), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                        Text(stringResource(R.string.group_settings_leave_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f))
                        if (!state.showLeaveConfirm) {
                            OutlinedButton(
                                onClick = { viewModel.setShowLeaveConfirm(true) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.group_settings_leave))
                            }
                        } else {
                            Text(stringResource(R.string.group_settings_leave_confirm), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.leaveGroup() },
                                    enabled = !state.isSaving,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    else Text(stringResource(R.string.group_settings_yes_leave))
                                }
                                OutlinedButton(onClick = { viewModel.setShowLeaveConfirm(false) }) { Text(stringResource(R.string.group_detail_cancel)) }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(state.groupInfo) {
        if (state.groupInfo == null && !state.isLoading) {
            navController.popBackStack(TrevioRoute.Groups.route, inclusive = false)
        }
    }
}
