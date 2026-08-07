package com.trevio.android.domain.repository

import com.trevio.android.domain.model.GroupAnalytics
import com.trevio.android.domain.model.UserAnalytics

interface AnalyticsService {
    suspend fun getGroupAnalytics(groupId: String): Result<GroupAnalytics>
    suspend fun getUserAnalytics(): Result<UserAnalytics>
}
