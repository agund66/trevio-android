package com.trevio.android.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Template accent colors (light)
val TemplateTrip = Color(0xFF6366F1)
val TemplateTurf = Color(0xFF22C55E)
val TemplateCasual = Color(0xFFF59E0B)
val TemplateHousehold = Color(0xFF0D9488)

// Template accent colors (dark)
val TemplateTripDark = Color(0xFF818CF8)
val TemplateTurfDark = Color(0xFF4ADE80)
val TemplateCasualDark = Color(0xFFFBBF24)
val TemplateHouseholdDark = Color(0xFF2DD4BF)

// Light Theme Colors
val TrevioPrimary = Color(0xFF0D9488)
val TrevioPrimaryLight = Color(0xFF5EEAD4)
val TrevioPrimaryDark = Color(0xFF134E4A)
val TrevioOnPrimary = Color(0xFFFFFFFF)

val TrevioSecondary = Color(0xFF6366F1)
val TrevioSecondaryLight = Color(0xFFA5B4FC)
val TrevioSecondaryDark = Color(0xFF312E81)

val TrevioBackground = Color(0xFFF8FAFC)
val TrevioSurface = Color(0xFFFFFFFF)
val TrevioSurfaceVariant = Color(0xFFF1F5F9)
val TrevioOnBackground = Color(0xFF1E293B)
val TrevioOnSurface = Color(0xFF1E293B)
val TrevioOnSurfaceVariant = Color(0xFF64748B)

val TrevioError = Color(0xFFEF4444)
val TrevioOnError = Color(0xFFFFFFFF)
val TrevioSuccess = Color(0xFF22C55E)
val TrevioWarning = Color(0xFFF59E0B)

// Balance colors
val BalancePositive = Color(0xFF22C55E)
val BalanceNegative = Color(0xFFEF4444)
val BalanceNeutral = Color(0xFF64748B)

// Dark Theme Colors
val TrevioPrimaryDarkTheme = Color(0xFF2DD4BF)
val TrevioBackgroundDark = Color(0xFF0F172A)
val TrevioSurfaceDark = Color(0xFF1E293B)
val TrevioSurfaceVariantDark = Color(0xFF334155)
val TrevioOnBackgroundDark = Color(0xFFF1F5F9)
val TrevioOnSurfaceDark = Color(0xFFF1F5F9)
val TrevioOnSurfaceVariantDark = Color(0xFF94A3B8)

// Accent colors
val TrevioAccent = Color(0xFFF59E0B)
val TrevioAccentLight = Color(0xFFFCD34D)

// Border / divider (slate-200 equivalent)
val TrevioBorder = Color(0xFFE2E8F0)
val TrevioBorderDark = Color(0xFF334155)

// Dark theme variants for balance/status colors
val BalancePositiveDark = Color(0xFF4ADE80)
val BalanceNegativeDark = Color(0xFFF87171)
val BalanceNeutralDark = Color(0xFF94A3B8)
val TrevioWarningDarkTheme = Color(0xFFFBBF24)
val TrevioSecondaryDarkTheme = Color(0xFF818CF8)

// Category colors (light)
val CategoryFood = Color(0xFFF59E0B)
val CategoryTransport = Color(0xFF6366F1)
val CategoryShopping = Color(0xFFEC4899)
val CategoryTurf = Color(0xFF22C55E)
val CategoryAccommodation = Color(0xFF0D9488)
val CategoryOther = Color(0xFF6B7280)

// Category colors (dark)
val CategoryFoodDark = Color(0xFFFBBF24)
val CategoryTransportDark = Color(0xFF818CF8)
val CategoryShoppingDark = Color(0xFFF472B6)
val CategoryTurfDark = Color(0xFF4ADE80)
val CategoryAccommodationDark = Color(0xFF2DD4BF)
val CategoryOtherDark = Color(0xFF9CA3AF)

// Gamification colors
val StreakFire = Color(0xFFF97316)
val BudgetWarning = Color(0xFFF59E0B)

// Settlement / success celebration colors
val SettlementGradientStart = Color(0xFF34D399)
val SettlementGradientEnd = Color(0xFF059669)
val SettlementBgStart = Color(0xFF10B981)
val SettlementBgEnd = Color(0xFF059669)
val SaveButtonGreen = Color(0xFF4CAF50)

// Category fallback
val CategoryFallback = Color(0xFF94A3B8)

// Info / notification priority colors
val InfoBlue = Color(0xFF3B82F6)
val InfoBlueDark = Color(0xFF60A5FA)

// Analytics chart colors
val AnalyticsPurple = Color(0xFFA855F7)

// Success text colors (for inline success messages)
val SuccessTextLight = Color(0xFF16A34A)
val SuccessTextDark = Color(0xFF86EFAC)

// Helper: adaptive category color
@androidx.compose.runtime.Composable
fun adaptiveCategoryColor(key: String): androidx.compose.ui.graphics.Color {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    return when (key) {
        "food" -> if (isDark) CategoryFoodDark else CategoryFood
        "transport" -> if (isDark) CategoryTransportDark else CategoryTransport
        "shopping" -> if (isDark) CategoryShoppingDark else CategoryShopping
        "turf" -> if (isDark) CategoryTurfDark else CategoryTurf
        "accommodation" -> if (isDark) CategoryAccommodationDark else CategoryAccommodation
        else -> if (isDark) CategoryOtherDark else CategoryOther
    }
}

// Helper: adaptive balance color
@androidx.compose.runtime.Composable
fun adaptiveBalanceColor(balance: Double): androidx.compose.ui.graphics.Color {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    return when {
        balance > 0.01 -> if (isDark) BalancePositiveDark else BalancePositive
        balance < -0.01 -> if (isDark) BalanceNegativeDark else BalanceNegative
        else -> if (isDark) BalanceNeutralDark else BalanceNeutral
    }
}
