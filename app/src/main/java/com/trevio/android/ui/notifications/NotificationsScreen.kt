package com.trevio.android.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.trevio.android.R
import com.trevio.android.core.designsystem.components.LoadingIndicator
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.components.formatRelativeTimeText
import com.trevio.android.core.designsystem.theme.BalanceNegative
import com.trevio.android.core.designsystem.theme.BalanceNegativeDark
import com.trevio.android.core.designsystem.theme.BalancePositive
import com.trevio.android.core.designsystem.theme.BalancePositiveDark
import com.trevio.android.core.designsystem.theme.InfoBlue
import com.trevio.android.core.designsystem.theme.InfoBlueDark
import com.trevio.android.core.designsystem.theme.TemplateCasual
import com.trevio.android.core.designsystem.theme.TemplateCasualDark
import com.trevio.android.core.designsystem.theme.TemplateHousehold
import com.trevio.android.core.designsystem.theme.TemplateHouseholdDark
import com.trevio.android.core.designsystem.theme.TemplateTrip
import com.trevio.android.core.designsystem.theme.TemplateTripDark
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.data.remote.FirestoreObservers
import com.trevio.android.domain.model.AppNotification
import com.trevio.android.domain.model.BroadcastMessage
import com.trevio.android.domain.model.BroadcastPriority
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.BroadcastService
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.NotificationService
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.util.DateUtils
import com.trevio.android.util.rememberCurrencyFormatter
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationService: NotificationService,
    private val broadcastService: BroadcastService,
    private val authService: AuthService,
    private val groupService: GroupService,
    private val firestoreObservers: FirestoreObservers
) : ViewModel() {

    data class NotificationsState(
        val isLoading: Boolean = true,
        val notifications: List<AppNotification> = emptyList(),
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val broadcasts: List<BroadcastMessage> = emptyList(),
        @StringRes val error: Int? = null,
        val invitationLoading: String? = null,
        val invitationResult: Map<String, String> = emptyMap()
    )

    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state

    /// Tracks the current notifications listener so repeated
    /// loadNotifications() calls don't create multiple Firestore listeners.
    private var notificationsListenerJob: kotlinx.coroutines.Job? = null

    init { loadNotifications() }

    /**
     * Starts a real-time listener for notifications.  Broadcasts are
     * fetched once (they change rarely).  With offline persistence
     * enabled, the first notification emission comes from cache.
     */
    fun loadNotifications() {
        _state.value = _state.value.copy(isLoading = true)
        // Cancel any existing listener before starting a new one
        // to prevent duplicate Firestore listeners.
        notificationsListenerJob?.cancel()
        notificationsListenerJob = viewModelScope.launch {
            try {
                firestoreObservers.observeNotifications(20).collect { result ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        notifications = result.items,
                        hasMore = result.hasMore
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = NotificationsState(isLoading = false, error = e.toStringResId())
            }
        }
        // Fetch broadcasts once (best-effort, non-blocking)
        viewModelScope.launch {
            val uid = authService.getCurrentUserId()
            val user = authService.getCurrentUser()
            if (uid != null && user != null) {
                broadcastService.getActiveBroadcastsForUser(uid, user.blocked)
                    .onSuccess { broadcasts ->
                        _state.value = _state.value.copy(broadcasts = broadcasts)
                    }
            }
        }
    }

    fun loadMoreNotifications() {
        if (!_state.value.hasMore || _state.value.loadingMore) return
        _state.value = _state.value.copy(loadingMore = true)
        val lastId = _state.value.notifications.lastOrNull()?.notificationId
        viewModelScope.launch {
            notificationService.getNotifications(20, lastId)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        notifications = _state.value.notifications + result.items,
                        loadingMore = false,
                        hasMore = result.hasMore
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loadingMore = false)
                }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            notificationService.markAllNotificationsRead()
                .onSuccess { loadNotifications() }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toStringResId())
                }
        }
    }

    fun acceptInvitation(notificationId: String, invitationId: String) {
        _state.value = _state.value.copy(invitationLoading = invitationId)
        viewModelScope.launch {
            groupService.acceptInvitation(invitationId)
                .onSuccess { result ->
                    notificationService.updateNotificationData(notificationId, mapOf("status" to "accepted"))
                    _state.value = _state.value.copy(
                        invitationLoading = null,
                        invitationResult = _state.value.invitationResult + (invitationId to "accepted")
                    )
                    loadNotifications()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(invitationLoading = null, error = e.toStringResId())
                }
        }
    }

    fun declineInvitation(notificationId: String, invitationId: String) {
        _state.value = _state.value.copy(invitationLoading = invitationId)
        viewModelScope.launch {
            groupService.declineInvitation(invitationId)
                .onSuccess {
                    notificationService.updateNotificationData(notificationId, mapOf("status" to "declined"))
                    _state.value = _state.value.copy(
                        invitationLoading = null,
                        invitationResult = _state.value.invitationResult + (invitationId to "declined")
                    )
                    loadNotifications()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(invitationLoading = null, error = e.toStringResId())
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormatter = rememberCurrencyFormatter()
    val unreadCount by remember {
        derivedStateOf { state.notifications.count { !it.read } }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }

    // Lazily request POST_NOTIFICATIONS when the user opens the Notifications screen
    // for the first time (not at app launch). Only needed on Android 13+ (TIRAMISU).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Result is intentionally ignored — in-app notifications still work from Firestore.
        // The permission only governs system-level push notification display.
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Reset the pull-to-refresh indicator shortly after a refresh is
    // triggered.  Notifications are kept fresh by a real-time Firestore
    // listener, so loadNotifications() returns quickly — the delay gives
    // the indicator enough time to be visible to the user.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            kotlinx.coroutines.delay(800)
            isRefreshing = false
        }
    }

    if (state.isLoading) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(title = stringResource(R.string.notifications_title))
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    state.error?.let { errMsg ->
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(title = stringResource(R.string.notifications_title))
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.notifications_failed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(errMsg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = if (unreadCount > 0) "${stringResource(R.string.notifications_title)} ($unreadCount)" else stringResource(R.string.notifications_title),
            actions = {
                if (unreadCount > 0) {
                    TextButton(
                        onClick = { viewModel.markAllRead() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.notifications_mark_all_read), fontWeight = FontWeight.Medium)
                    }
                }
            }
        )

        if (state.notifications.isEmpty() && state.broadcasts.isEmpty()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.loadNotifications()
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.notifications_no_notifications),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.notifications_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            } // end PullToRefreshBox
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.loadNotifications()
                },
                modifier = Modifier.weight(1f)
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(state.broadcasts, key = { _, it -> it.id }) { index, broadcast ->
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300, delayMillis = minOf(index, 10) * 50)) + slideInVertically(
                            animationSpec = tween(300, delayMillis = minOf(index, 10) * 50),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                        BroadcastNotificationCard(broadcast)
                    }
                }
                itemsIndexed(state.notifications, key = { _, it -> it.notificationId }) { index, notification ->
                    val staggerIndex = state.broadcasts.size + index
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300, delayMillis = minOf(staggerIndex, 10) * 50)) + slideInVertically(
                            animationSpec = tween(300, delayMillis = minOf(staggerIndex, 10) * 50),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                        NotificationCard(
                            notification = notification,
                            navController = navController,
                            invitationLoading = state.invitationLoading,
                            invitationResult = state.invitationResult,
                            formatDate = currencyFormatter.formatDate,
                            onAccept = { notificationId, invitationId -> viewModel.acceptInvitation(notificationId, invitationId) },
                            onDecline = { notificationId, invitationId -> viewModel.declineInvitation(notificationId, invitationId) }
                        )
                    }
                }
                if (state.hasMore) {
                    item {
                        LaunchedEffect(state.notifications.lastOrNull()?.notificationId) {
                            viewModel.loadMoreNotifications()
                        }
                        if (state.loadingMore) {
                            LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                    }
                }
            }
            } // end PullToRefreshBox
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    navController: androidx.navigation.NavHostController,
    invitationLoading: String? = null,
    invitationResult: Map<String, String> = emptyMap(),
    formatDate: (Long, Boolean) -> String = { _, _ -> "" },
    onAccept: (String, String) -> Unit = { _, _ -> },
    onDecline: (String, String) -> Unit = { _, _ -> }
) {
    val (icon, iconColor) = notificationIcon(notification.type)
    val isInvitation = notification.type == "invitation" && notification.data.containsKey("invitationId")
    val isSettlement = notification.type == "settlement" && notification.data.containsKey("groupId")
    val isExpense = notification.type == "expense_added" && notification.data.containsKey("groupId")
    val hasGroupLink = isSettlement || isExpense
    val notificationId = notification.notificationId
    val invitationId = notification.data["invitationId"] ?: ""
    val groupId = notification.data["groupId"] ?: ""
    val result = notification.data["status"]?.takeIf { it.isNotBlank() } ?: invitationResult[invitationId]

    TrevioCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (!notification.read) FontWeight.SemiBold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (notification.createdAt > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            formatRelativeTimeText(notification.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                if (!notification.read) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            if (isInvitation && result == null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(start = 52.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAccept(notificationId, invitationId) },
                        enabled = invitationLoading != invitationId,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (invitationLoading == invitationId) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(stringResource(R.string.notifications_accept), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = { onDecline(notificationId, invitationId) },
                        enabled = invitationLoading != invitationId,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.notifications_decline), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (isInvitation && result == "accepted") {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { navController.navigate(TrevioRoute.GroupDetail.createRoute(groupId)) },
                    modifier = Modifier.padding(start = 52.dp)
                ) {
                    Text(stringResource(R.string.notifications_open_group), color = MaterialTheme.colorScheme.primary)
                }
            }

            if (isInvitation && result == "declined") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.notifications_declined),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 52.dp)
                )
            }

            if (hasGroupLink) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { navController.navigate(TrevioRoute.GroupDetail.createRoute(groupId)) },
                    modifier = Modifier.padding(start = 52.dp)
                ) {
                    Text(stringResource(R.string.notifications_view_group), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun notificationIcon(type: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val isDark = isSystemInDarkTheme()
    return when (type) {
        "expense_added", "expense_updated", "expense_deleted" -> Icons.Default.Receipt to if (isDark) TemplateCasualDark else TemplateCasual
        "settlement_added" -> Icons.Default.Payments to if (isDark) BalancePositiveDark else BalancePositive
        "member_joined", "member_left", "group_invitation", "invitation" -> Icons.Default.Group to if (isDark) TemplateTripDark else TemplateTrip
        else -> Icons.Default.Notifications to if (isDark) TemplateHouseholdDark else TemplateHousehold
    }
}

@Composable
private fun BroadcastNotificationCard(broadcast: BroadcastMessage) {
    var isExpanded by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val priorityColor = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> if (isDark) BalanceNegativeDark else BalanceNegative
        BroadcastPriority.MAINTENANCE -> if (isDark) TemplateCasualDark else TemplateCasual
        BroadcastPriority.INFO -> if (isDark) InfoBlueDark else InfoBlue
    }
    val priorityLabel = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> stringResource(R.string.priority_critical)
        BroadcastPriority.MAINTENANCE -> stringResource(R.string.priority_maintenance)
        BroadcastPriority.INFO -> stringResource(R.string.priority_info)
    }
    val priorityIcon = when (broadcast.priority) {
        BroadcastPriority.CRITICAL -> Icons.Default.Warning
        BroadcastPriority.MAINTENANCE -> Icons.Default.Build
        BroadcastPriority.INFO -> Icons.Default.Info
    }

    val sanitizedHtml = remember(broadcast.htmlContent) {
        org.jsoup.Jsoup.clean(broadcast.htmlContent, org.jsoup.safety.Safelist.relaxed())
    }

    TrevioCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(priorityColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = priorityIcon,
                        contentDescription = null,
                        tint = priorityColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            broadcast.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = priorityColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                priorityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AndroidView(
                            factory = { context ->
                                android.webkit.WebView(context).apply {
                                    settings.javaScriptEnabled = false
                                    loadDataWithBaseURL(null, sanitizedHtml, "text/html", "UTF-8", null)
                                }
                            },
                            update = { webView ->
                                webView.loadDataWithBaseURL(null, sanitizedHtml, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) stringResource(R.string.notifications_show_less) else stringResource(R.string.notifications_read_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.padding(start = 52.dp)
            ) {
                Text(if (isExpanded) stringResource(R.string.notifications_show_less) else stringResource(R.string.notifications_read_more))
            }
        }
    }
}

