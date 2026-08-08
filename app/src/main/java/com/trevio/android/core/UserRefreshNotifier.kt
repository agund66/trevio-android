package com.trevio.android.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRefreshNotifier @Inject constructor() {
    private val _userRefreshed = MutableSharedFlow<Unit>(replay = 0)
    val userRefreshed: SharedFlow<Unit> = _userRefreshed

    suspend fun notifyUserRefreshed() {
        _userRefreshed.emit(Unit)
    }
}
