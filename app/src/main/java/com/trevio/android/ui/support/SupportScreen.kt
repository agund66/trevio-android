package com.trevio.android.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.trevio.android.core.designsystem.components.LoadingIndicator
import com.trevio.android.core.navigation.TrevioRouteSupport
import com.trevio.android.domain.model.HelpArticle
import com.trevio.android.domain.model.SupportCategory
import com.trevio.android.domain.model.SupportMessage
import com.trevio.android.domain.model.SupportPriority
import com.trevio.android.domain.model.SupportStatus
import com.trevio.android.domain.model.SupportTicket
import com.trevio.android.domain.model.SupportTicketContext
import com.trevio.android.domain.repository.SupportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════════
// ViewModels
// ═══════════════════════════════════════════════════════════════════

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val supportService: SupportService
) : ViewModel() {
    data class State(
        val isLoading: Boolean = true,
        val articles: List<HelpArticle> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init { loadArticles() }

    fun loadArticles() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            supportService.getHelpArticles()
                .onSuccess { articles -> _state.value = State(isLoading = false, articles = articles) }
                .onFailure { e -> _state.value = State(isLoading = false, error = e.message) }
        }
    }
}

@HiltViewModel
class CreateTicketViewModel @Inject constructor(
    private val supportService: SupportService
) : ViewModel() {
    data class State(
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val createdTicketId: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun submit(
        subject: String,
        description: String,
        category: SupportCategory,
        context: SupportTicketContext?
    ) {
        _state.value = _state.value.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            supportService.createTicket(subject, description, category, context)
                .onSuccess { id -> _state.value = State(isSubmitting = false, createdTicketId = id) }
                .onFailure { e -> _state.value = State(isSubmitting = false, error = e.message) }
        }
    }
}

@HiltViewModel
class MyTicketsViewModel @Inject constructor(
    private val supportService: SupportService
) : ViewModel() {
    data class State(
        val isLoading: Boolean = true,
        val tickets: List<SupportTicket> = emptyList(),
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init { loadTickets() }

    fun loadTickets() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            supportService.getMyTickets(20, null)
                .onSuccess { result -> _state.value = State(isLoading = false, tickets = result.items, hasMore = result.hasMore) }
                .onFailure { e -> _state.value = State(isLoading = false, error = e.message) }
        }
    }

    fun loadMoreTickets() {
        if (!_state.value.hasMore || _state.value.loadingMore) return
        _state.value = _state.value.copy(loadingMore = true)
        val lastId = _state.value.tickets.lastOrNull()?.ticketId
        viewModelScope.launch {
            supportService.getMyTickets(20, lastId)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        tickets = _state.value.tickets + result.items,
                        loadingMore = false,
                        hasMore = result.hasMore
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loadingMore = false)
                }
        }
    }
}

@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    private val supportService: SupportService
) : ViewModel() {
    data class State(
        val isLoading: Boolean = true,
        val ticket: SupportTicket? = null,
        val messages: List<SupportMessage> = emptyList(),
        val isSending: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun load(ticketId: String) {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val ticketResult = supportService.getTicket(ticketId)
            val messagesResult = supportService.getMessages(ticketId)

            val ticket = ticketResult.getOrNull()
            val messages = messagesResult.getOrNull() ?: emptyList()
            val error = ticketResult.exceptionOrNull()?.message ?: messagesResult.exceptionOrNull()?.message

            _state.value = State(
                isLoading = false,
                ticket = ticket,
                messages = messages,
                error = error
            )

            // Mark as read
            if (ticket != null && ticket.unreadByUser) {
                supportService.markTicketReadByUser(ticketId)
            }
        }
    }

    fun sendReply(ticketId: String, body: String) {
        _state.value = _state.value.copy(isSending = true, error = null)
        viewModelScope.launch {
            supportService.sendMessage(ticketId, body)
                .onSuccess {
                    // Reload messages
                    supportService.getMessages(ticketId)
                        .onSuccess { msgs ->
                            supportService.getTicket(ticketId).onSuccess { t ->
                                _state.value = _state.value.copy(
                                    isSending = false,
                                    ticket = t,
                                    messages = msgs
                                )
                            }
                        }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSending = false, error = e.message)
                }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Support Hub Screen (FAQ + quick actions)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    navController: NavController,
    contextGroupId: String? = null,
    contextGroupName: String? = null,
    contextScreen: String? = null
) {
    val viewModel: SupportViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf("all") }
    var selectedArticle by remember { mutableStateOf<HelpArticle?>(null) }

    val articleCategories = state.articles.map { it.category }.distinct()

    if (selectedArticle != null) {
        HelpArticleDetailScreen(
            article = selectedArticle!!,
            onBack = { selectedArticle = null },
            onReportIssue = {
                selectedArticle = null
                navController.navigate(
                    TrevioRouteSupport.CreateTicket.createRoute(contextGroupId, contextGroupName, contextScreen)
                )
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quick actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Filled.Edit,
                    label = "Report Issue",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        navController.navigate(
                            TrevioRouteSupport.CreateTicket.createRoute(contextGroupId, contextGroupName, contextScreen)
                        )
                    }
                )
                QuickActionCard(
                    icon = Icons.Filled.Receipt,
                    label = "My Tickets",
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(TrevioRouteSupport.MyTickets.route) }
                )
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search articles...") },
                leadingIcon = { Icon(Icons.Filled.Search, "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Category filter chips
            if (articleCategories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = activeCategory == "all",
                            onClick = { activeCategory = "all" },
                            label = { Text("All") }
                        )
                    }
                    items(articleCategories) { cat ->
                        FilterChip(
                            selected = activeCategory == cat,
                            onClick = { activeCategory = cat },
                            label = { Text(cat.replace("_", " ").replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            // Articles
            when {
                state.isLoading -> LoadingIndicator()
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    val filtered = state.articles.filter { a ->
                        (activeCategory == "all" || a.category == activeCategory) &&
                        (searchQuery.isBlank() ||
                        a.title.contains(searchQuery, ignoreCase = true) ||
                        a.content.contains(searchQuery, ignoreCase = true) ||
                        a.tags.any { it.contains(searchQuery, ignoreCase = true) })
                    }
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No articles found. Try reporting your issue directly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered) { article ->
                                ArticleListItem(article = article) { selectedArticle = it }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ArticleListItem(article: HelpArticle, onClick: (HelpArticle) -> Unit) {
    Card(
        onClick = { onClick(article) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    article.category.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Help Article Detail
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpArticleDetailScreen(
    article: HelpArticle,
    onBack: () -> Unit,
    onReportIssue: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                article.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            // Render HTML content as structured text
            val blocks = parseHtmlToBlocks(article.content)
            blocks.forEach { block ->
                when (block) {
                    is HtmlBlock.Heading -> Text(
                        block.text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    is HtmlBlock.Paragraph -> Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    is HtmlBlock.ListItem -> Row(
                        modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
                    ) {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(block.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onReportIssue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Still need help? Report an issue")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Create Ticket Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    navController: NavController,
    contextGroupId: String? = null,
    contextGroupName: String? = null,
    contextScreen: String? = null
) {
    val viewModel: CreateTicketViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<SupportCategory?>(null) }

    // Handle successful creation
    LaunchedEffect(state.createdTicketId) {
        state.createdTicketId?.let { id ->
            navController.navigate(TrevioRouteSupport.TicketDetail.createRoute(id)) {
                popUpTo(TrevioRouteSupport.Support.route) { inclusive = true }
            }
        }
    }

    val categories = listOf(
        SupportCategory.CALCULATION to "Balance & Calculations" to "Balance looks wrong, split issues",
        SupportCategory.SETTLEMENT to "Settlement Issue" to "Payment recording, settlement not updating",
        SupportCategory.EXPENSE to "Expense Issue" to "Can't add/edit/delete expense",
        SupportCategory.GROUP_ACCESS to "Group Access" to "Can't join, create, or access a group",
        SupportCategory.PAYMENT_INFO to "Payment Info" to "UPI ID or phone number issues",
        SupportCategory.ACCOUNT to "Account Issue" to "Profile, currency, or account settings",
        SupportCategory.BUG to "Bug / Crash" to "App crashing, data not loading",
        SupportCategory.OTHER to "Other" to "Something else entirely"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report an Issue", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Context indicator
            if (contextGroupId != null || contextScreen != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        "📍 Context: ${contextGroupName ?: contextGroupId ?: "Group"}${if (contextScreen != null) " • $contextScreen" else ""}",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text("What type of issue?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            // Category chips — FlowRow wraps them nicely
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { (pair, desc) ->
                    val (cat, label) = pair
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Describe the issue") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                ),
                supportingText = {
                    Text(
                        "${description.length}/2000 characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = description.isNotBlank() && description.trim().length < 10
            )
            if (description.isNotBlank() && description.trim().length < 10) {
                Text(
                    "Please describe your issue (at least 10 characters)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            state.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text("Error: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val cat = selectedCategory ?: SupportCategory.OTHER
                    val ctx = if (contextGroupId != null || contextScreen != null) {
                        SupportTicketContext(
                            groupId = contextGroupId ?: "",
                            groupName = contextGroupName ?: "",
                            screen = contextScreen ?: ""
                        )
                    } else null
                    viewModel.submit(subject.trim(), description.trim(), cat, ctx)
                },
                enabled = subject.isNotBlank() && description.trim().length >= 10 && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submit Issue")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// My Tickets Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTicketsScreen(navController: NavController) {
    val viewModel: MyTicketsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Tickets", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(TrevioRouteSupport.CreateTicket.route)
                    }) {
                        Icon(Icons.Filled.Add, "New Ticket")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            state.tickets.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("No tickets yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("When you report an issue, it will appear here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { navController.navigate(TrevioRouteSupport.CreateTicket.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Report an Issue")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.tickets) { ticket ->
                        TicketListItem(ticket = ticket) {
                            navController.navigate(TrevioRouteSupport.TicketDetail.createRoute(ticket.ticketId))
                        }
                    }
                    if (state.hasMore) {
                        item {
                            LaunchedEffect(state.tickets.lastOrNull()?.ticketId) {
                                viewModel.loadMoreTickets()
                            }
                            if (state.loadingMore) {
                                LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketListItem(ticket: SupportTicket, onClick: () -> Unit) {
    val statusColor = when (ticket.status) {
        SupportStatus.OPEN -> MaterialTheme.colorScheme.primary
        SupportStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        SupportStatus.WAITING_USER -> MaterialTheme.colorScheme.secondary
        SupportStatus.RESOLVED -> MaterialTheme.colorScheme.outline
        SupportStatus.CLOSED -> MaterialTheme.colorScheme.outline
    }
    val priorityColor = when (ticket.priority) {
        SupportPriority.URGENT -> MaterialTheme.colorScheme.error
        SupportPriority.HIGH -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        SupportPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        SupportPriority.LOW -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = if (ticket.unreadByUser) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(priorityColor)
                    .align(Alignment.Top)
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
                        fontWeight = if (ticket.unreadByUser) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (ticket.unreadByUser) {
                        Badge { Text("NEW", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    ticket.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(ticket.status.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = statusColor)
                    )
                    Text(
                        formatRelativeTime(ticket.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ticket.context.groupName.isNotEmpty()) {
                        Text(
                            "• ${ticket.context.groupName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(ts: Long): String {
    if (ts == 0L) return ""
    val diffMs = System.currentTimeMillis() - ts
    val diffMins = diffMs / 60000
    val diffHours = diffMs / 3600000
    val diffDays = diffMs / 86400000
    return when {
        diffMins < 1 -> "just now"
        diffMins < 60 -> "${diffMins}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays < 7 -> "${diffDays}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(ts))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Ticket Detail Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    navController: NavController,
    ticketId: String
) {
    val viewModel: TicketDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    var reply by remember { mutableStateOf("") }

    LaunchedEffect(ticketId) {
        viewModel.load(ticketId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ticket", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (state.ticket != null) {
                Surface(tonalElevation = 2.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Closed ticket notice
                        if (state.ticket!!.status == SupportStatus.CLOSED) {
                            Text(
                                "This ticket is closed. Send a message to reopen it.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        // Error
                        state.error?.let { err ->
                            Text(
                                "Error: $err",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = reply,
                                onValueChange = { reply = it },
                                placeholder = {
                                    Text(
                                        if (state.ticket!!.status == SupportStatus.CLOSED)
                                            "Send a message to reopen..."
                                        else "Type your reply..."
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (reply.isNotBlank()) {
                                        viewModel.sendReply(ticketId, reply.trim())
                                        reply = ""
                                    }
                                },
                                enabled = reply.isNotBlank() && !state.isSending
                            ) {
                                if (state.isSending) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val ticket = state.ticket
        if (ticket == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Ticket not found")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ticket info card
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            ticket.subject,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(ticket.status.name.replace("_", " ")) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ticket.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Category: ${ticket.category.name.replace("_", " ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Messages
            val listState = rememberLazyListState()
            LaunchedEffect(state.messages.size) {
                if (state.messages.isNotEmpty()) {
                    listState.animateScrollToItem(state.messages.size - 1)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { msg ->
                    MessageBubble(message = msg)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SupportMessage) {
    val isUser = message.fromRole == com.trevio.android.domain.model.SupportMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser) {
                    Text(
                        "Support Team",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatMessageTime(ts: Long): String {
    if (ts == 0L) return ""
    val date = java.util.Date(ts)
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}

// ─── HTML parsing for article rendering ──────────────────────────

sealed class HtmlBlock {
    data class Heading(val text: String) : HtmlBlock()
    data class Paragraph(val text: String) : HtmlBlock()
    data class ListItem(val text: String) : HtmlBlock()
}

private fun parseHtmlToBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    val normalized = html
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")

    // Match headings, paragraphs, and list items in order of appearance
    data class Match(val start: Int, val end: Int, val inner: String, val type: Int)
    val matches = mutableListOf<Match>()

    val headingRegex = Regex("<h[1-4][^>]*>(.*?)</h[1-4]>", RegexOption.DOT_MATCHES_ALL)
    val pRegex = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    val liRegex = Regex("<li[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)

    headingRegex.findAll(normalized).forEach { m ->
        val inner = m.groupValues[1].replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
        if (inner.isNotEmpty()) matches.add(Match(m.range.first, m.range.last + 1, inner, 0))
    }
    pRegex.findAll(normalized).forEach { m ->
        val inner = m.groupValues[1].replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
        if (inner.isNotEmpty()) matches.add(Match(m.range.first, m.range.last + 1, inner, 1))
    }
    liRegex.findAll(normalized).forEach { m ->
        val inner = m.groupValues[1].replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
        if (inner.isNotEmpty()) matches.add(Match(m.range.first, m.range.last + 1, inner, 2))
    }

    matches.sortBy { it.start }
    matches.forEach { m ->
        when (m.type) {
            0 -> blocks.add(HtmlBlock.Heading(m.inner))
            1 -> blocks.add(HtmlBlock.Paragraph(m.inner))
            2 -> blocks.add(HtmlBlock.ListItem(m.inner))
        }
    }

    // Fallback: if no blocks were extracted, just strip all tags
    if (blocks.isEmpty()) {
        val plainText = normalized
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (plainText.isNotEmpty()) {
            blocks.add(HtmlBlock.Paragraph(plainText))
        }
    }

    return blocks
}
