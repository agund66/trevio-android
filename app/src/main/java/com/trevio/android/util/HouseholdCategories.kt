package com.trevio.android.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Celebration
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.trevio.android.R

data class HouseholdCategory(
    val key: String,
    val label: String,
    @StringRes val labelResId: Int,
    val icon: ImageVector,
    val color: Color,
    val isIncome: Boolean = false
)

object HouseholdCategories {

    // ─── Expense Categories ──────────────────────────────────────

    val EXPENSE_CATEGORIES = listOf(
        HouseholdCategory("groceries", "Groceries", R.string.cat_groceries, Icons.Filled.LocalGroceryStore, Color(0xFFF97316)),
        HouseholdCategory("vegetables", "Vegetables", R.string.cat_vegetables, Icons.Filled.Eco, Color(0xFF84CC16)),
        HouseholdCategory("utilities", "Utilities", R.string.cat_utilities, Icons.Filled.Bolt, Color(0xFF3B82F6)),
        HouseholdCategory("rent", "Rent", R.string.cat_rent, Icons.Filled.Home, Color(0xFF8B5CF6)),
        HouseholdCategory("transport", "Transport", R.string.cat_transport, Icons.Filled.DirectionsCar, Color(0xFF06B6D4)),
        HouseholdCategory("medical", "Medical", R.string.cat_medical, Icons.Filled.MedicalServices, Color(0xFFEF4444)),
        HouseholdCategory("education", "Education", R.string.cat_education, Icons.Filled.School, Color(0xFF6366F1)),
        HouseholdCategory("entertainment", "Entertainment", R.string.cat_entertainment, Icons.Filled.Movie, Color(0xFFEC4899)),
        HouseholdCategory("dining", "Dining Out", R.string.cat_dining, Icons.Filled.Restaurant, Color(0xFFF59E0B)),
        HouseholdCategory("shopping", "Shopping", R.string.cat_shopping, Icons.Filled.ShoppingBag, Color(0xFFA855F7)),
        HouseholdCategory("household", "Household", R.string.cat_household, Icons.Filled.CleaningServices, Color(0xFF14B8A6)),
        HouseholdCategory("insurance", "Insurance", R.string.cat_insurance, Icons.Filled.Shield, Color(0xFF64748B)),
        HouseholdCategory("other", "Other", R.string.cat_other, Icons.Filled.Category, Color(0xFF94A3B8))
    )

    // ─── Income Categories ───────────────────────────────────────

    val INCOME_CATEGORIES = listOf(
        HouseholdCategory("salary", "Salary", R.string.cat_salary, Icons.Filled.Work, Color(0xFF22C55E), isIncome = true),
        HouseholdCategory("bonus", "Bonus", R.string.cat_bonus, Icons.Filled.Celebration, Color(0xFFF59E0B), isIncome = true),
        HouseholdCategory("gift", "Gift", R.string.cat_gift, Icons.Filled.CardGiftcard, Color(0xFFEC4899), isIncome = true),
        HouseholdCategory("refund", "Refund", R.string.cat_refund, Icons.Filled.Undo, Color(0xFF3B82F6), isIncome = true),
        HouseholdCategory("investment", "Investment", R.string.cat_investment, Icons.Filled.TrendingUp, Color(0xFF14B8A6), isIncome = true),
        HouseholdCategory("rental_income", "Rental Income", R.string.cat_rental_income, Icons.Filled.Apartment, Color(0xFF8B5CF6), isIncome = true),
        HouseholdCategory("pension", "Pension", R.string.cat_pension, Icons.Filled.Elderly, Color(0xFF6366F1), isIncome = true),
        HouseholdCategory("other_income", "Other Income", R.string.cat_other_income, Icons.Filled.AccountBalance, Color(0xFF94A3B8), isIncome = true)
    )

    val ALL_CATEGORIES = EXPENSE_CATEGORIES + INCOME_CATEGORIES

    // ─── Lookup helpers ──────────────────────────────────────────

    private val categoryMap = ALL_CATEGORIES.associateBy { it.key }

    fun getCategory(key: String): HouseholdCategory? = categoryMap[key]

    fun getCategoryLabel(key: String): String = categoryMap[key]?.label ?: key.replaceFirstChar { it.uppercase() }

    @StringRes
    fun getCategoryLabelResId(key: String): Int = categoryMap[key]?.labelResId ?: R.string.cat_other

    fun getCategoryColor(key: String): Color = categoryMap[key]?.color ?: Color(0xFF94A3B8)

    fun getCategoryIcon(key: String): ImageVector = categoryMap[key]?.icon ?: Icons.Filled.Category

    fun getCategories(isIncome: Boolean): List<HouseholdCategory> =
        if (isIncome) INCOME_CATEGORIES else EXPENSE_CATEGORIES

    // ─── Keyword map for auto-category suggestion ────────────────

    private val KEYWORD_MAP: Map<String, String> = mapOf(
        // groceries
        "grocery" to "groceries", "groceries" to "groceries", "supermarket" to "groceries",
        "big bazaar" to "groceries", "bigbasket" to "groceries", "dmart" to "groceries",
        "reliance" to "groceries", "amazon" to "groceries", "flipkart" to "groceries",
        "provisions" to "groceries", "kirana" to "groceries", "store" to "groceries",
        // vegetables
        "vegetable" to "vegetables", "vegetables" to "vegetables", "sabzi" to "vegetables",
        "mandi" to "vegetables", "green" to "vegetables", "fruits" to "vegetables",
        "fruit" to "vegetables", "market" to "vegetables",
        // utilities
        "electric" to "utilities", "electricity" to "utilities", "bill" to "utilities",
        "water" to "utilities", "gas" to "utilities", "internet" to "utilities",
        "wifi" to "utilities", "broadband" to "utilities", "mobile" to "utilities",
        "recharge" to "utilities", "phone" to "utilities", "dth" to "utilities",
        "mseb" to "utilities", "bescom" to "utilities", "tata power" to "utilities",
        // rent
        "rent" to "rent", "lease" to "rent", "landlord" to "rent", "maintenance" to "rent",
        "society" to "rent", "hoa" to "rent",
        // transport
        "petrol" to "transport", "diesel" to "transport", "fuel" to "transport",
        "uber" to "transport", "ola" to "transport", "auto" to "transport",
        "rickshaw" to "transport", "bus" to "transport", "metro" to "transport",
        "train" to "transport", "cab" to "transport", "taxi" to "transport",
        "parking" to "transport", "toll" to "transport",
        // medical
        "medicine" to "medical", "medicines" to "medical", "pharmacy" to "medical",
        "medical" to "medical", "doctor" to "medical", "hospital" to "medical",
        "clinic" to "medical", "health" to "medical", "lab" to "medical",
        "test" to "medical", "checkup" to "medical", "tablet" to "medical",
        "tablet" to "medical", "drug" to "medical", "apollo" to "medical",
        // education
        "school" to "education", "college" to "education", "tuition" to "education",
        "fees" to "education", "fee" to "education", "book" to "education",
        "books" to "education", "course" to "education", "exam" to "education",
        "uniform" to "education", "stationery" to "education",
        // entertainment
        "movie" to "entertainment", "cinema" to "entertainment", "netflix" to "entertainment",
        "hotstar" to "entertainment", "prime" to "entertainment", "ott" to "entertainment",
        "game" to "entertainment", "concert" to "entertainment", "show" to "entertainment",
        "ticket" to "entertainment",
        // dining
        "restaurant" to "dining", "food" to "dining", "lunch" to "dining",
        "dinner" to "dining", "breakfast" to "dining", "swiggy" to "dining",
        "zomato" to "dining", "pizza" to "dining", "burger" to "dining",
        "cafe" to "dining", "coffee" to "dining", "tea" to "dining",
        "snack" to "dining", "hotel" to "dining", "dine" to "dining",
        // shopping
        "clothes" to "shopping", "clothing" to "shopping", "shirt" to "shopping",
        "pant" to "shopping", "shoe" to "shopping", "shoes" to "shopping",
        "myntra" to "shopping", "mall" to "shopping", "dress" to "shopping",
        "furniture" to "shopping", "electronic" to "shopping", "gadget" to "shopping",
        // household
        "cleaning" to "household", "maid" to "household", "servant" to "household",
        "cook" to "household", "laundry" to "household", "wash" to "household",
        "repair" to "household", "plumber" to "household", "electrician" to "household",
        "carpenter" to "household", "paint" to "household",
        // insurance
        "insurance" to "insurance", "premium" to "insurance", "policy" to "insurance",
        "lic" to "insurance", "health insurance" to "insurance", "car insurance" to "insurance",
        // salary
        "salary" to "salary", "wage" to "salary", "paycheck" to "salary",
        "pay" to "salary", "income" to "salary", "stipend" to "salary",
        // bonus
        "bonus" to "bonus", "incentive" to "bonus", "commission" to "bonus",
        "reward" to "bonus",
        // gift
        "gift" to "gift", "present" to "gift", "donation" to "gift",
        "charity" to "gift",
        // refund
        "refund" to "refund", "return" to "refund", "cashback" to "refund",
        "reversal" to "refund",
        // investment
        "dividend" to "investment", "interest" to "investment", "mutual fund" to "investment",
        "stock" to "investment", "share" to "investment", "profit" to "investment",
        "sip" to "investment",
        // rental_income
        "rental" to "rental_income", "tenant" to "rental_income", "lease income" to "rental_income",
        // pension
        "pension" to "pension", "retirement" to "pension", "social security" to "pension",
        "provident fund" to "pension", "pf" to "pension", "epf" to "pension"
    )

    /**
     * Suggests a category key based on the description text.
     * Returns null if no match found.
     */
    fun suggestCategory(description: String): String? {
        if (description.isBlank()) return null
        val lower = description.trim().lowercase()
        // Check multi-word phrases first (longer matches take priority)
        val sortedKeys = KEYWORD_MAP.keys.sortedByDescending { it.length }
        for (keyword in sortedKeys) {
            if (lower.contains(keyword)) {
                return KEYWORD_MAP[keyword]
            }
        }
        return null
    }

    /**
     * Returns the list of category keys sorted by usage frequency (most used first).
     * Categories with no usage keep their default order.
     */
    fun sortedByUsage(usageCount: Map<String, Int>, isIncome: Boolean): List<HouseholdCategory> {
        val categories = getCategories(isIncome)
        return categories.sortedByDescending { usageCount[it.key] ?: 0 }
    }

    // ─── Default split group categories (for non-household groups) ─

    val DEFAULT_CATEGORIES = listOf("food", "transport", "shopping", "turf", "accommodation", "other")

    val DEFAULT_CATEGORY_LABELS = mapOf(
        "food" to R.string.cat_food,
        "transport" to R.string.cat_transport,
        "shopping" to R.string.cat_shopping,
        "turf" to R.string.cat_turf,
        "accommodation" to R.string.cat_accommodation,
        "other" to R.string.cat_other
    )

    val DEFAULT_CATEGORY_COLORS = mapOf(
        "food" to Color(0xFFF97316),
        "transport" to Color(0xFF3B82F6),
        "shopping" to Color(0xFFA855F7),
        "turf" to Color(0xFF22C55E),
        "accommodation" to Color(0xFFEC4899),
        "other" to Color(0xFF94A3B8)
    )
}
