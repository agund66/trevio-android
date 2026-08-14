package com.trevio.android.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.domain.model.FeaturedMessage
import com.trevio.android.domain.model.ReminderConfig
import com.trevio.android.domain.repository.AdminService
import com.trevio.android.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val adminService: AdminService
) : ViewModel() {

    data class RemindersState(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val config: ReminderConfig = ReminderConfig(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(RemindersState())
    val state: StateFlow<RemindersState> = _state

    init {
        loadConfig()
    }

    fun loadConfig() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            adminService.getReminderConfig()
                .onSuccess { config ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        config = config ?: ReminderConfig()
                    )
                }
                .onFailure { e ->
                    Logger.w("RemindersViewModel", "Failed to load config: ${e.message}", e)
                    _state.value = _state.value.copy(isLoading = false, message = e.message)
                }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(config = _state.value.config.copy(enabled = enabled))
        saveConfig()
    }

    fun setDefaultTime(time: String) {
        _state.value = _state.value.copy(config = _state.value.config.copy(defaultLocalTime = time))
    }

    fun addTimezoneOverride(timezone: String, time: String) {
        val newOverrides = _state.value.config.timezoneOverrides.toMutableMap()
        newOverrides[timezone] = time
        _state.value = _state.value.copy(config = _state.value.config.copy(timezoneOverrides = newOverrides))
    }

    fun removeTimezoneOverride(timezone: String) {
        val newOverrides = _state.value.config.timezoneOverrides.toMutableMap()
        newOverrides.remove(timezone)
        _state.value = _state.value.copy(config = _state.value.config.copy(timezoneOverrides = newOverrides))
    }

    fun setFeaturedMessage(title: String?, body: String, startAt: Long, endAt: Long) {
        val featured = if (body.isNotBlank() && endAt > startAt) {
            FeaturedMessage(title = title, body = body, startAt = startAt, endAt = endAt)
        } else null
        _state.value = _state.value.copy(config = _state.value.config.copy(featuredMessage = featured))
    }

    fun clearFeaturedMessage() {
        _state.value = _state.value.copy(config = _state.value.config.copy(featuredMessage = null))
    }

    fun saveConfig() {
        val config = _state.value.config
        _state.value = _state.value.copy(isSaving = true, message = null)
        viewModelScope.launch {
            adminService.saveReminderConfig(config)
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false, message = "Saved successfully")
                }
                .onFailure { e ->
                    Logger.w("RemindersViewModel", "Failed to save config: ${e.message}", e)
                    _state.value = _state.value.copy(isSaving = false, message = e.message)
                }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
