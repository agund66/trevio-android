package com.trevio.android.domain.repository

import com.trevio.android.domain.model.KarmaBreakdown

interface KarmaService {
    suspend fun getKarmaBreakdown(): Result<KarmaBreakdown>
    suspend fun refreshKarma(): Result<KarmaBreakdown>
    suspend fun setKarmaPublic(public: Boolean): Result<Unit>
    suspend fun getPublicKarma(uid: String): Result<KarmaBreakdown?>
}
