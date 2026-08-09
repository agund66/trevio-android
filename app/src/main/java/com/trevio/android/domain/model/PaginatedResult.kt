package com.trevio.android.domain.model

data class PaginatedResult<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val lastId: String?
)
