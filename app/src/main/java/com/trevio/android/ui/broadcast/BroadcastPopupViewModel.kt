package com.trevio.android.ui.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.domain.model.BroadcastMessage
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.BroadcastService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BroadcastPopupViewModel @Inject constructor(
    private val broadcastService: BroadcastService,
    private val authService: AuthService
) : ViewModel() {

    data class PopupState(
        val unreadBroadcasts: List<BroadcastMessage> = emptyList(),
        val currentIndex: Int = 0,
        val isAcknowledging: Boolean = false,
        val dismissedInfoIds: Set<String> = emptySet()
    )

    private val _state = MutableStateFlow(PopupState())
    val state: StateFlow<PopupState> = _state

    fun loadUnreadBroadcasts() {
        viewModelScope.launch {
            val uid = authService.getCurrentUserId() ?: return@launch
            val user = authService.getCurrentUser() ?: return@launch
            broadcastService.getUnreadBroadcastsForUser(uid, user.blocked)
                .onSuccess { unread ->
                    _state.value = _state.value.copy(unreadBroadcasts = unread, currentIndex = 0)
                }
        }
    }

    fun acknowledge(broadcastId: String) {
        _state.value = _state.value.copy(isAcknowledging = true)
        viewModelScope.launch {
            val uid = authService.getCurrentUserId() ?: run {
                _state.value = _state.value.copy(isAcknowledging = false)
                return@launch
            }
            broadcastService.acknowledgeBroadcast(broadcastId, uid)
                .onSuccess {
                    val remaining = _state.value.unreadBroadcasts.filter { it.id != broadcastId }
                    _state.value = _state.value.copy(
                        unreadBroadcasts = remaining,
                        currentIndex = 0,
                        isAcknowledging = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isAcknowledging = false)
                }
        }
    }

    fun dismissInfo(broadcastId: String) {
        _state.value = _state.value.copy(
            dismissedInfoIds = _state.value.dismissedInfoIds + broadcastId
        )
    }

    val currentBroadcast: BroadcastMessage?
        get() {
            val s = _state.value
            if (s.currentIndex >= s.unreadBroadcasts.size) return null
            val b = s.unreadBroadcasts[s.currentIndex]
            return if (b.priority == com.trevio.android.domain.model.BroadcastPriority.INFO && b.id in s.dismissedInfoIds) {
                s.unreadBroadcasts.drop(s.currentIndex + 1).firstOrNull { it.id !in s.dismissedInfoIds }
            } else {
                b
            }
        }
}
