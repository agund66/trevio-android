package com.trevio.android.domain.repository

import com.trevio.android.domain.model.BroadcastMessage
import com.trevio.android.domain.model.BroadcastPriority
import com.trevio.android.domain.model.BroadcastRead
import com.trevio.android.domain.model.BroadcastTargetType

interface BroadcastService {
    suspend fun createBroadcast(
        title: String,
        htmlContent: String,
        priority: BroadcastPriority,
        targetType: BroadcastTargetType,
        targetUids: List<String>,
        startAt: Long,
        endAt: Long?
    ): Result<String>

    suspend fun getAllBroadcasts(): Result<List<BroadcastMessage>>
    suspend fun stopBroadcast(id: String): Result<Unit>
    suspend fun getReadCount(broadcastId: String): Result<Int>
    suspend fun getBroadcastReads(broadcastId: String): Result<List<BroadcastRead>>

    suspend fun getActiveBroadcastsForUser(uid: String, isBlocked: Boolean): Result<List<BroadcastMessage>>
    suspend fun getUnreadBroadcastsForUser(uid: String, isBlocked: Boolean): Result<List<BroadcastMessage>>
    suspend fun acknowledgeBroadcast(broadcastId: String, uid: String): Result<Unit>
}
