package com.trevio.android.domain.repository

import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.Settlement
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.model.SettlementMethod

interface SettlementService {
    suspend fun addSettlement(
        groupId: String,
        fromUid: String,
        toUid: String,
        amount: Double,
        currency: String,
        method: SettlementMethod,
        upiRefId: String?
    ): Result<String>

    suspend fun getSimplifiedDebts(groupId: String): Result<List<SimplifiedDebt>>
    suspend fun getGroupBalances(groupId: String): Result<List<Member>>
    suspend fun getSettlementHistory(groupId: String): Result<List<Settlement>>
}
