package com.trevio.android.ui.admin

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.domain.model.BroadcastMessage
import com.trevio.android.domain.model.BroadcastPriority
import com.trevio.android.domain.model.BroadcastRead
import com.trevio.android.domain.model.BroadcastTargetType
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AdminService
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.BroadcastService
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BroadcastViewModel @Inject constructor(
    private val broadcastService: BroadcastService,
    private val adminService: AdminService,
    private val authService: AuthService
) : ViewModel() {

    data class BroadcastState(
        val isLoading: Boolean = true,
        val broadcasts: List<BroadcastMessage> = emptyList(),
        val readCounts: Map<String, Int> = emptyMap(),
        @StringRes val error: Int? = null,
        val actionLoading: String? = null,
        val showForm: Boolean = false,
        val allUsers: List<User> = emptyList(),
        val formTitle: String = "",
        val formHtmlContent: String = "",
        val formPriority: BroadcastPriority = BroadcastPriority.INFO,
        val formTargetType: BroadcastTargetType = BroadcastTargetType.ALL,
        val formTargetUids: Set<String> = emptySet(),
        val formStartAt: Long? = null,
        val formEndAt: Long? = null,
        @StringRes val formError: Int? = null,
        val isSubmitting: Boolean = false,
        val selectedBroadcast: BroadcastMessage? = null,
        val detailReads: List<BroadcastRead> = emptyList(),
        val detailAllUsers: List<User> = emptyList(),
        val detailLoading: Boolean = false,
        val currentUserId: String? = null
    )

    private val _state = MutableStateFlow(BroadcastState())
    val state: StateFlow<BroadcastState> = _state

    init {
        loadCurrentUserId()
        loadBroadcasts()
    }

    private fun loadCurrentUserId() {
        viewModelScope.launch {
            _state.value = _state.value.copy(currentUserId = authService.getCurrentUserId())
        }
    }

    fun loadBroadcasts() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            broadcastService.getAllBroadcasts()
                .onSuccess { broadcasts ->
                    val counts = mutableMapOf<String, Int>()
                    for (b in broadcasts) {
                        broadcastService.getReadCount(b.id).onSuccess { count ->
                            counts[b.id] = count
                        }
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        broadcasts = broadcasts,
                        readCounts = counts
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.toStringResId())
                }
        }
    }

    fun stopBroadcast(id: String) {
        _state.value = _state.value.copy(actionLoading = id)
        viewModelScope.launch {
            broadcastService.stopBroadcast(id)
                .onSuccess { loadBroadcasts() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.toStringResId()) }
            _state.value = _state.value.copy(actionLoading = null)
        }
    }

    fun showForm() {
        viewModelScope.launch {
            adminService.getAllUsers(500, null)
                .onSuccess { result ->
                    _state.value = _state.value.copy(showForm = true, allUsers = result.items)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toStringResId())
                }
        }
    }

    fun hideForm() {
        _state.value = _state.value.copy(showForm = false, formError = null)
    }

    fun showDetail(broadcast: BroadcastMessage) {
        _state.value = _state.value.copy(selectedBroadcast = broadcast, detailLoading = true)
        viewModelScope.launch {
            adminService.getAllUsers(500, null)
                .onSuccess { result ->
                    broadcastService.getBroadcastReads(broadcast.id)
                        .onSuccess { reads ->
                            _state.value = _state.value.copy(
                                detailAllUsers = result.items,
                                detailReads = reads,
                                detailLoading = false
                            )
                        }
                        .onFailure {
                            _state.value = _state.value.copy(
                                detailAllUsers = result.items,
                                detailLoading = false
                            )
                        }
                }
                .onFailure {
                    _state.value = _state.value.copy(detailLoading = false)
                }
        }
    }

    fun hideDetail() {
        _state.value = _state.value.copy(selectedBroadcast = null, detailReads = emptyList(), detailAllUsers = emptyList())
    }

    fun updateFormTitle(value: String) {
        _state.value = _state.value.copy(formTitle = value)
    }

    fun updateFormHtmlContent(value: String) {
        _state.value = _state.value.copy(formHtmlContent = value)
    }

    fun updateFormPriority(value: BroadcastPriority) {
        _state.value = _state.value.copy(formPriority = value)
    }

    fun updateFormTargetType(value: BroadcastTargetType) {
        _state.value = _state.value.copy(formTargetType = value)
    }

    fun toggleTargetUser(uid: String) {
        val current = _state.value.formTargetUids.toMutableSet()
        if (uid in current) current.remove(uid) else current.add(uid)
        _state.value = _state.value.copy(formTargetUids = current)
    }

    fun updateFormStartAt(value: Long?) {
        _state.value = _state.value.copy(formStartAt = value)
    }

    fun updateFormEndAt(value: Long?) {
        _state.value = _state.value.copy(formEndAt = value)
    }

    fun submitBroadcast() {
        val s = _state.value
        if (s.formTitle.isBlank()) {
            _state.value = _state.value.copy(formError = R.string.broadcast_error_title_required)
            return
        }
        if (s.formHtmlContent.isBlank()) {
            _state.value = _state.value.copy(formError = R.string.broadcast_error_content_required)
            return
        }
        if (s.formStartAt == null) {
            _state.value = _state.value.copy(formError = R.string.broadcast_error_start_required)
            return
        }
        if (s.formTargetType == BroadcastTargetType.SPECIFIC && s.formTargetUids.isEmpty()) {
            _state.value = _state.value.copy(formError = R.string.broadcast_error_select_users)
            return
        }

        _state.value = _state.value.copy(isSubmitting = true, formError = null)
        viewModelScope.launch {
            try {
                val startMs = s.formStartAt
                val endMs = s.formEndAt
                if (endMs != null && endMs < startMs) {
                    _state.value = _state.value.copy(isSubmitting = false, formError = R.string.broadcast_error_end_after_start)
                    return@launch
                }

                broadcastService.createBroadcast(
                    title = s.formTitle.trim(),
                    htmlContent = s.formHtmlContent,
                    priority = s.formPriority,
                    targetType = s.formTargetType,
                    targetUids = if (s.formTargetType == BroadcastTargetType.SPECIFIC) s.formTargetUids.toList() else emptyList(),
                    startAt = startMs,
                    endAt = endMs
                ).onSuccess {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        showForm = false,
                        formTitle = "",
                        formHtmlContent = "",
                        formPriority = BroadcastPriority.INFO,
                        formTargetType = BroadcastTargetType.ALL,
                        formTargetUids = emptySet(),
                        formStartAt = null,
                        formEndAt = null
                    )
                    loadBroadcasts()
                }.onFailure { e ->
                    _state.value = _state.value.copy(isSubmitting = false, formError = e.toStringResId())
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, formError = e.toStringResId())
            }
        }
    }
}
