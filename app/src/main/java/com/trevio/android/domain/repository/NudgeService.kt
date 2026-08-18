package com.trevio.android.domain.repository

import com.trevio.android.domain.model.Nudge

interface NudgeService {
    suspend fun getActiveNudges(): Result<List<Nudge>>
    suspend fun generateNudges(): Result<List<Nudge>>
    suspend fun dismissNudge(nudgeId: String): Result<Unit>
    suspend fun markNudgeRead(nudgeId: String): Result<Unit>
}
