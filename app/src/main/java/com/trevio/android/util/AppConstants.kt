package com.trevio.android.util

object AppConstants {
    const val BASE_CURRENCY = "INR"

    // Default category
    const val DEFAULT_CATEGORY = "other"

    // Pagination
    const val DEFAULT_PAGE_SIZE = 20
    const val LARGE_PAGE_SIZE = 500
    const val HOUSEHOLD_PAGE_SIZE = 1000

    // Delays (milliseconds)
    const val SPLASH_DELAY_MS = 800L
    const val DEBOUNCE_DELAY_MS = 300L
    const val SAVE_AND_ADD_DELAY_MS = 800L
}

object MemberStatus {
    const val ACTIVE = "active"
    const val PENDING = "pending"
    const val LEFT = "left"
}

object MemberRole {
    const val ADMIN = "admin"
    const val MEMBER = "member"
}

object UserRole {
    const val USER = "user"
    const val SUPERADMIN = "superadmin"
}

object TransactionTypeStr {
    const val EXPENSE = "expense"
    const val INCOME = "income"
}
