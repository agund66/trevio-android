package com.trevio.android.domain.repository

import com.trevio.android.domain.model.MonthlyRecap
import com.trevio.android.domain.model.WrappedSummary

interface WrappedService {
    suspend fun getWrappedSummary(year: Int): Result<WrappedSummary>
    suspend fun generateWrappedSummary(year: Int): Result<WrappedSummary>
    suspend fun getMonthlyRecap(year: Int, month: Int): Result<MonthlyRecap>
    suspend fun generateMonthlyRecap(year: Int, month: Int): Result<MonthlyRecap>
}
