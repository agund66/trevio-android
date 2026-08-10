package com.trevio.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.LoadingIndicator
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.domain.model.HelpArticle
import com.trevio.android.domain.model.SupportCategory
import com.trevio.android.domain.model.SupportMessage
import com.trevio.android.domain.model.SupportMessageRole
import com.trevio.android.domain.model.SupportPriority
import com.trevio.android.domain.model.SupportStatus
import com.trevio.android.domain.model.SupportTicket
import com.trevio.android.domain.repository.SupportService
import com.trevio.android.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminSupportViewModel @Inject constructor(
    private val supportService: SupportService
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val tickets: List<SupportTicket> = emptyList(),
        val ticketsHasMore: Boolean = false,
        val ticketsLoadingMore: Boolean = false,
        val articles: List<HelpArticle> = emptyList(),
        val selectedTicket: SupportTicket? = null,
        val messages: List<SupportMessage> = emptyList(),
        val isSending: Boolean = false,
        val error: String? = null,
        val subTab: Int = 0,
        val statusFilter: SupportStatus? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init { loadTickets() }

    fun selectSubTab(index: Int) {
        _state.value = _state.value.copy(subTab = index)
        if (index == 1 && state.value.articles.isEmpty()) {
            loadArticles()
        }
    }

    fun setStatusFilter(status: SupportStatus?) {
        _state.value = _state.value.copy(statusFilter = status)
        loadTickets(status)
    }

    fun loadTickets(status: SupportStatus? = null) {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            supportService.getAllTickets(status = status, pageSize = 20, lastTicketId = null)
                .onSuccess { result ->
                    _state.value = _state.value.copy(isLoading = false, tickets = result.items, ticketsHasMore = result.hasMore)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun loadMoreTickets() {
        if (!_state.value.ticketsHasMore || _state.value.ticketsLoadingMore) return
        _state.value = _state.value.copy(ticketsLoadingMore = true)
        val lastId = _state.value.tickets.lastOrNull()?.ticketId
        viewModelScope.launch {
            supportService.getAllTickets(status = _state.value.statusFilter, pageSize = 20, lastTicketId = lastId)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        tickets = _state.value.tickets + result.items,
                        ticketsLoadingMore = false,
                        ticketsHasMore = result.hasMore
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(ticketsLoadingMore = false)
                }
        }
    }

    fun selectTicket(ticket: SupportTicket) {
        _state.value = _state.value.copy(selectedTicket = ticket)
        loadMessages(ticket.ticketId)
        if (ticket.unreadByAdmin) {
            viewModelScope.launch { supportService.markTicketReadByAdmin(ticket.ticketId) }
        }
    }

    fun closeTicketDetail() {
        _state.value = _state.value.copy(selectedTicket = null, messages = emptyList())
        loadTickets(_state.value.statusFilter)
    }

    private fun loadMessages(ticketId: String) {
        viewModelScope.launch {
            supportService.getMessages(ticketId)
                .onSuccess { msgs ->
                    _state.value = _state.value.copy(messages = msgs)
                }
        }
    }

    fun sendReply(ticketId: String, body: String) {
        _state.value = _state.value.copy(isSending = true)
        viewModelScope.launch {
            supportService.sendAdminMessage(ticketId, body)
                .onSuccess {
                    supportService.getMessages(ticketId).onSuccess { msgs ->
                        supportService.getTicket(ticketId).onSuccess { t ->
                            _state.value = _state.value.copy(isSending = false, messages = msgs, selectedTicket = t)
                        }
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSending = false, error = e.message)
                }
        }
    }

    fun updateStatus(ticketId: String, status: SupportStatus) {
        viewModelScope.launch {
            supportService.updateTicketStatus(ticketId, status)
                .onSuccess {
                    supportService.getTicket(ticketId).onSuccess { t ->
                        _state.value = _state.value.copy(selectedTicket = t)
                    }
                    loadTickets(_state.value.statusFilter)
                }
        }
    }

    fun updatePriority(ticketId: String, priority: SupportPriority) {
        viewModelScope.launch {
            supportService.updateTicketPriority(ticketId, priority)
                .onSuccess {
                    supportService.getTicket(ticketId).onSuccess { t ->
                        _state.value = _state.value.copy(selectedTicket = t)
                    }
                    loadTickets(_state.value.statusFilter)
                }
        }
    }

    private fun loadArticles() {
        viewModelScope.launch {
            supportService.getAllHelpArticles()
                .onSuccess { articles -> _state.value = _state.value.copy(articles = articles) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
        }
    }

    fun toggleArticleActive(article: HelpArticle) {
        viewModelScope.launch {
            supportService.updateHelpArticle(
                articleId = article.articleId,
                title = null,
                content = null,
                category = null,
                tags = null,
                order = null,
                active = !article.active
            ).onSuccess { loadArticles() }
        }
    }

    fun deleteArticle(article: HelpArticle) {
        viewModelScope.launch {
            supportService.deleteHelpArticle(article.articleId)
                .onSuccess { loadArticles() }
        }
    }

    fun createArticle(title: String, content: String, category: String, tags: List<String>, order: Int) {
        viewModelScope.launch {
            supportService.createHelpArticle(title, content, category, tags, order)
                .onSuccess { loadArticles() }
        }
    }

    fun updateArticle(
        article: HelpArticle,
        title: String,
        content: String,
        category: String,
        tags: List<String>,
        order: Int
    ) {
        viewModelScope.launch {
            supportService.updateHelpArticle(
                articleId = article.articleId,
                title = title,
                content = content,
                category = category,
                tags = tags,
                order = order,
                active = article.active
            ).onSuccess { loadArticles() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSupportScreen(
    viewModel: AdminSupportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.selectedTicket != null) {
        AdminTicketDetail(
            ticket = state.selectedTicket!!,
            messages = state.messages,
            isSending = state.isSending,
            error = state.error,
            onBack = { viewModel.closeTicketDetail() },
            onSendReply = { body -> viewModel.sendReply(state.selectedTicket!!.ticketId, body) },
            onUpdateStatus = { status -> viewModel.updateStatus(state.selectedTicket!!.ticketId, status) },
            onUpdatePriority = { priority -> viewModel.updatePriority(state.selectedTicket!!.ticketId, priority) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-tabs
        TabRow(selectedTabIndex = state.subTab) {
            Tab(
                selected = state.subTab == 0,
                onClick = { viewModel.selectSubTab(0) },
                text = { Text("Tickets") },
                icon = { Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = state.subTab == 1,
                onClick = { viewModel.selectSubTab(1) },
                text = { Text("Articles") },
                icon = { Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (state.subTab == 0) {
            AdminTicketsList(
                state = state,
                onFilterChange = { viewModel.setStatusFilter(it) },
                onSelectTicket = { viewModel.selectTicket(it) },
                onLoadMore = { viewModel.loadMoreTickets() }
            )
        } else {
            AdminArticlesList(
                state = state,
                onToggleActive = { viewModel.toggleArticleActive(it) },
                onDelete = { viewModel.deleteArticle(it) },
                onCreate = { title, content, category, tags, order ->
                    viewModel.createArticle(title, content, category, tags, order)
                },
                onEdit = { article, title, content, category, tags, order ->
                    viewModel.updateArticle(article, title, content, category, tags, order)
                }
            )
        }
    }
}

@Composable
private fun AdminTicketsList(
    state: AdminSupportViewModel.State,
    onFilterChange: (SupportStatus?) -> Unit,
    onSelectTicket: (SupportTicket) -> Unit,
    onLoadMore: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<SupportCategory?>(null) }

    // Stats
    val total = state.tickets.size
    val open = state.tickets.count { it.status == SupportStatus.OPEN || it.status == SupportStatus.IN_PROGRESS }
    val unread = state.tickets.count { it.unreadByAdmin }

    // Client-side filter by search + category
    val filteredTickets = remember(state.tickets, searchQuery, categoryFilter) {
        state.tickets.filter { t ->
            (categoryFilter == null || t.category == categoryFilter) &&
            (searchQuery.isBlank() ||
                t.subject.contains(searchQuery, ignoreCase = true) ||
                t.userEmail.contains(searchQuery, ignoreCase = true) ||
                t.userDisplayName.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip("Total", total.toString())
            StatChip("Open", open.toString())
            StatChip("Unread", unread.toString())
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by subject, user, email...") },
            leadingIcon = { Icon(Icons.Filled.Search, "Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Filter chips — FlowRow wraps them
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = state.statusFilter == null,
                onClick = { onFilterChange(null) },
                label = { Text("All Status") }
            )
            SupportStatus.values().forEach { status ->
                FilterChip(
                    selected = state.statusFilter == status,
                    onClick = { onFilterChange(status) },
                    label = { Text(status.name.replace("_", " ")) }
                )
            }
        }

        // Category filter chips
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = categoryFilter == null,
                onClick = { categoryFilter = null },
                label = { Text("All Categories") }
            )
            SupportCategory.values().forEach { cat ->
                FilterChip(
                    selected = categoryFilter == cat,
                    onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                    label = { Text(cat.name.replace("_", " ")) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredTickets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No tickets found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTickets) { ticket ->
                    AdminTicketItem(ticket = ticket) { onSelectTicket(it) }
                }
                if (state.ticketsHasMore && searchQuery.isBlank() && categoryFilter == null) {
                    item {
                        LaunchedEffect(state.tickets.lastOrNull()?.ticketId) {
                            onLoadMore()
                        }
                        if (state.ticketsLoadingMore) {
                            LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatChip(label: String, value: String) {
    TrevioCard(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminTicketItem(ticket: SupportTicket, onClick: (SupportTicket) -> Unit) {
    val priorityColor = when (ticket.priority) {
        SupportPriority.URGENT -> MaterialTheme.colorScheme.error
        SupportPriority.HIGH -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        SupportPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        SupportPriority.LOW -> MaterialTheme.colorScheme.outline
    }

    TrevioCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick(ticket) }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(priorityColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        ticket.subject,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (ticket.unreadByAdmin) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (ticket.unreadByAdmin) {
                        Badge { Text("NEW", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Text(
                    "${ticket.userDisplayName} @${ticket.userUsername}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(ticket.status.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                    )
                    Text(
                        ticket.priority.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor
                    )
                    Text(
                        "• ${DateUtils.formatRelativeTime(ticket.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTicketDetail(
    ticket: SupportTicket,
    messages: List<SupportMessage>,
    isSending: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSendReply: (String) -> Unit,
    onUpdateStatus: (SupportStatus) -> Unit,
    onUpdatePriority: (SupportPriority) -> Unit
) {
    var reply by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ticket Detail", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (ticket.status == SupportStatus.CLOSED) {
                        Text(
                            "This ticket is closed. Change status to reopen, or send a message below.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    error?.let {
                        Text(
                            "Error: $it",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = reply,
                            onValueChange = { reply = it },
                            placeholder = { Text("Type your response...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (reply.isNotBlank()) {
                                    onSendReply(reply.trim())
                                    reply = ""
                                }
                            },
                            enabled = reply.isNotBlank() && !isSending
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Ticket info
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(ticket.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(ticket.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("From: ${ticket.userDisplayName} @${ticket.userUsername}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ticket.context.groupName.isNotEmpty()) {
                        Text("Context: ${ticket.context.groupName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Status controls
                    Text("Status:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SupportStatus.values().forEach { status ->
                            FilterChip(
                                selected = ticket.status == status,
                                onClick = { onUpdateStatus(status) },
                                label = { Text(status.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Priority controls
                    Text("Priority:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SupportPriority.values().forEach { priority ->
                            FilterChip(
                                selected = ticket.priority == priority,
                                onClick = { onUpdatePriority(priority) },
                                label = { Text(priority.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // Messages
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    AdminMessageBubble(message = msg)
                }
            }
        }
    }
}

@Composable
private fun AdminMessageBubble(message: SupportMessage) {
    val isAdmin = message.fromRole == SupportMessageRole.SUPERADMIN
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAdmin) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    if (isAdmin) "You (Admin)" else message.fromName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAdmin) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAdmin) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatAdminMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                    color = if (isAdmin) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


private fun formatAdminMessageTime(ts: Long): String {
    if (ts == 0L) return ""
    val date = java.util.Date(ts)
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}

@Composable
private fun AdminArticlesList(
    state: AdminSupportViewModel.State,
    onToggleActive: (HelpArticle) -> Unit,
    onDelete: (HelpArticle) -> Unit,
    onCreate: (String, String, String, List<String>, Int) -> Unit,
    onEdit: (HelpArticle, String, String, String, List<String>, Int) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingArticle by remember { mutableStateOf<HelpArticle?>(null) }

    if (showCreateDialog) {
        CreateArticleDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, content, category, tags, order ->
                onCreate(title, content, category, tags, order)
                showCreateDialog = false
            }
        )
    }

    editingArticle?.let { article ->
        EditArticleDialog(
            article = article,
            onDismiss = { editingArticle = null },
            onSave = { title, content, category, tags, order ->
                onEdit(article, title, content, category, tags, order)
                editingArticle = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Help Articles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }

        if (state.articles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No articles yet. Create one to help your users.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.articles) { article ->
                    AdminArticleItem(
                        article = article,
                        onToggleActive = { onToggleActive(it) },
                        onEdit = { editingArticle = it },
                        onDelete = { onDelete(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminArticleItem(
    article: HelpArticle,
    onToggleActive: (HelpArticle) -> Unit,
    onEdit: (HelpArticle) -> Unit,
    onDelete: (HelpArticle) -> Unit
) {
    TrevioCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!article.active) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Hidden", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    "${article.category} • Order: ${article.order}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onEdit(article) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { onToggleActive(article) }) {
                Icon(
                    if (article.active) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (article.active) "Hide" else "Show"
                )
            }
            IconButton(onClick = { onDelete(article) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateArticleDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, List<String>, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var tags by remember { mutableStateOf("") }
    var order by remember { mutableStateOf("99") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Help Article") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content (HTML)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = order,
                    onValueChange = { order = it.filter { c -> c.isDigit() } },
                    label = { Text("Display Order") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onCreate(
                            title.trim(),
                            content.trim(),
                            category.trim(),
                            tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            order.toIntOrNull() ?: 99
                        )
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditArticleDialog(
    article: HelpArticle,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<String>, Int) -> Unit
) {
    var title by remember { mutableStateOf(article.title) }
    var content by remember { mutableStateOf(article.content) }
    var category by remember { mutableStateOf(article.category) }
    var tags by remember { mutableStateOf(article.tags.joinToString(", ")) }
    var order by remember { mutableStateOf(article.order.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Help Article") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content (HTML)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = order,
                    onValueChange = { order = it.filter { c -> c.isDigit() } },
                    label = { Text("Display Order") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(
                            title.trim(),
                            content.trim(),
                            category.trim(),
                            tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            order.toIntOrNull() ?: article.order
                        )
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
