package com.trevio.android.domain.model

enum class SplitType { EQUAL, EXACT, PERCENT, SHARES, ITEMIZED }
enum class GroupTemplate { TRIP, TURF, CASUAL, HOUSEHOLD }
enum class TransactionType { EXPENSE, INCOME }
enum class MemberRole { ADMIN, MEMBER }
enum class MemberStatus { ACTIVE, LEFT }
enum class InvitationStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }
enum class SettlementMethod { UPI, CASH, OTHER }
enum class RecurringFrequency { WEEKLY, MONTHLY }

data class LocalizedString(
    val resId: Int,
    val args: List<Any> = emptyList()
)

data class RecurringConfig(
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val endDate: Long? = null,
    val nextDueDate: Long? = null,
    val parentExpenseId: String? = null
)

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val photoURL: String = "",
    val defaultCurrency: String = "INR",
    val acceptedTnC: Boolean = false,
    val role: String = "user",
    val blocked: Boolean = false,
    val upiId: String = "",
    val phoneNumber: String = "",
    val countryCode: String = ""
)

data class Group(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val template: GroupTemplate = GroupTemplate.CASUAL,
    val currency: String = "INR",
    val createdBy: String = "",
    val inviteCode: String = "",
    val memberCount: Int = 0,
    val totalExpenses: Double = 0.0,
    val yourBalance: Double = 0.0,
    val yourRole: String = "member",
    val archived: Boolean = false,
    val monthlyBudget: Double? = null,
    val budgetCategories: Map<String, Double>? = null
)

data class Member(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoURL: String = "",
    val balance: Double = 0.0,
    val role: String = "member",
    val status: String = "active",
    val isOffline: Boolean = false
)

data class SplitEntry(
    val amount: Double = 0.0,
    val shareValue: Double = 0.0
)

data class BillItem(
    val itemId: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val assignedTo: List<String> = emptyList()
)

data class ItemizedSplitData(
    val items: List<BillItem> = emptyList(),
    val taxAmount: Double = 0.0,
    val tipAmount: Double = 0.0,
    val taxSplitMode: String = "proportional",
    val tipSplitMode: String = "proportional"
)

data class Expense(
    val expenseId: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val paidBy: String = "",
    val paidByName: String = "",
    val splitType: SplitType = SplitType.EQUAL,
    val splits: Map<String, SplitEntry> = emptyMap(),
    val category: String = "other",
    val date: Long = 0,
    val createdBy: String = "",
    val exchangeRateToBase: Double = 1.0,
    val note: String = "",
    val recurring: RecurringConfig? = null,
    val itemizedData: ItemizedSplitData? = null,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val originalAmount: Double = 0.0,
    val originalCurrency: String = "INR"
)

data class Settlement(
    val settlementId: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val fromName: String = "",
    val toName: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val method: SettlementMethod = SettlementMethod.CASH,
    val upiRefId: String = "",
    val date: Long = 0,
    val createdBy: String = ""
)

data class SimplifiedDebt(
    val fromUid: String = "",
    val toUid: String = "",
    val fromName: String = "",
    val toName: String = "",
    val fromPhotoURL: String = "",
    val toPhotoURL: String = "",
    val toUpiId: String = "",
    val fromUpiId: String = "",
    val toPhoneNumber: String = "",
    val toCountryCode: String = "",
    val amount: Double = 0.0
)

data class Activity(
    val activityId: String = "",
    val type: String = "",
    val description: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoURL: String = "",
    val createdAt: Long = 0,
    val data: Map<String, Any>? = null
)

data class AppNotification(
    val notificationId: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    val createdAt: Long = 0,
    val data: Map<String, String> = emptyMap()
)

data class UserSearchResult(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoURL: String = ""
)

data class ExchangeRates(
    val base: String = "INR",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap(),
    val updatedAt: Long = 0
)

enum class BroadcastPriority { CRITICAL, MAINTENANCE, INFO }
enum class BroadcastTargetType { ALL, ALL_EXCEPT_BLOCKED, SPECIFIC }

data class BroadcastMessage(
    val id: String = "",
    val title: String = "",
    val htmlContent: String = "",
    val priority: BroadcastPriority = BroadcastPriority.INFO,
    val targetType: BroadcastTargetType = BroadcastTargetType.ALL,
    val targetUids: List<String> = emptyList(),
    val startAt: Long = 0,
    val endAt: Long? = null,
    val active: Boolean = true,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Long = 0,
    val stoppedAt: Long? = null
)

data class BroadcastRead(
    val uid: String = "",
    val readAt: Long = 0
)

data class CategoryBreakdown(
    val category: String = "",
    val totalAmount: Double = 0.0,
    val expenseCount: Int = 0,
    val percentage: Double = 0.0
)

data class MonthlyTrend(
    val month: String = "",
    val label: String = "",
    val labelText: LocalizedString? = null,
    val totalAmount: Double = 0.0,
    val expenseCount: Int = 0
)

data class MemberSpending(
    val uid: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val totalPaid: Double = 0.0,
    val totalShare: Double = 0.0,
    val expenseCount: Int = 0,
    val netBalance: Double = 0.0
)

data class HighestExpense(
    val description: String = "",
    val amount: Double = 0.0,
    val date: Long = 0
)

data class GroupAnalytics(
    val groupId: String = "",
    val groupName: String = "",
    val totalExpenses: Double = 0.0,
    val expenseCount: Int = 0,
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val monthlyTrends: List<MonthlyTrend> = emptyList(),
    val memberSpending: List<MemberSpending> = emptyList(),
    val avgExpenseAmount: Double = 0.0,
    val highestExpense: HighestExpense? = null,
    val recentActivityRate: Double = 0.0
)

data class TopGroupSpending(
    val groupId: String = "",
    val groupName: String = "",
    val totalSpent: Double = 0.0,
    val expenseCount: Int = 0
)

data class UserAnalytics(
    val totalSpent: Double = 0.0,
    val totalPaid: Double = 0.0,
    val totalOwed: Double = 0.0,
    val totalOwing: Double = 0.0,
    val netBalance: Double = 0.0,
    val groupCount: Int = 0,
    val expenseCount: Int = 0,
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val monthlyTrends: List<MonthlyTrend> = emptyList(),
    val topGroups: List<TopGroupSpending> = emptyList()
)

data class TripItineraryItem(
    val itemId: String = "",
    val title: String = "",
    val description: String = "",
    val date: Long = 0,
    val location: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val category: String = "other",
    val estimatedCost: Double = 0.0,
    val assignedTo: List<String> = emptyList(),
    val completed: Boolean = false
)

data class TripDay(
    val date: Long = 0,
    val label: String = "",
    val items: List<TripItineraryItem> = emptyList(),
    val totalEstimatedCost: Double = 0.0
)

data class TripLocation(
    val locationId: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val category: String = "other",
    val visitedOn: Long = 0,
    val expenseId: String = ""
)

data class TripData(
    val startDate: Long = 0,
    val endDate: Long = 0,
    val destination: String = "",
    val coverPhotoURL: String = "",
    val itinerary: List<TripItineraryItem> = emptyList(),
    val locations: List<TripLocation> = emptyList()
)

// ─── Support System ──────────────────────────────────────────────

enum class SupportCategory {
    CALCULATION, SETTLEMENT, EXPENSE, GROUP_ACCESS, PAYMENT_INFO, ACCOUNT, BUG, OTHER;

    companion object {
        fun fromString(value: String?): SupportCategory =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}

enum class SupportPriority { LOW, MEDIUM, HIGH, URGENT }

enum class SupportStatus { OPEN, IN_PROGRESS, WAITING_USER, RESOLVED, CLOSED }

enum class SupportMessageRole { USER, SUPERADMIN }

data class SupportTicketContext(
    val groupId: String = "",
    val groupName: String = "",
    val expenseId: String = "",
    val screen: String = ""
)

data class SupportTicket(
    val ticketId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userDisplayName: String = "",
    val userUsername: String = "",
    val subject: String = "",
    val description: String = "",
    val category: SupportCategory = SupportCategory.OTHER,
    val priority: SupportPriority = SupportPriority.LOW,
    val status: SupportStatus = SupportStatus.OPEN,
    val context: SupportTicketContext = SupportTicketContext(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null,
    val lastMessageAt: Long = 0,
    val lastMessageBy: String? = null,
    val unreadByUser: Boolean = false,
    val unreadByAdmin: Boolean = false
)

data class SupportMessage(
    val messageId: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromRole: SupportMessageRole = SupportMessageRole.USER,
    val body: String = "",
    val createdAt: Long = 0
)

data class HelpArticle(
    val articleId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "general",
    val tags: List<String> = emptyList(),
    val order: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val createdBy: String = ""
)

// ─── Household Analytics ─────────────────────────────────────────

data class DailySummary(
    val date: Long = 0,
    val dateLabel: String = "",
    val dateLabelText: LocalizedString? = null,
    val totalSpent: Double = 0.0,
    val totalReceived: Double = 0.0,
    val netAmount: Double = 0.0,
    val entryCount: Int = 0,
    val entries: List<Expense> = emptyList()
)

data class MemberContribution(
    val uid: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val totalSpent: Double = 0.0,
    val totalReceived: Double = 0.0,
    val entryCount: Int = 0,
    val spentPercentage: Double = 0.0,
    val rank: Int = 0
)

data class DailyTrend(
    val day: Int = 0,
    val date: Long = 0,
    val totalSpent: Double = 0.0,
    val totalReceived: Double = 0.0
)

data class MonthComparison(
    val lastMonthSpent: Double = 0.0,
    val spentChange: Double = 0.0,
    val spentChangePercent: Double = 0.0,
    val lastMonthReceived: Double = 0.0,
    val receivedChange: Double = 0.0
)

data class MonthlyReport(
    val month: String = "",
    val monthLabel: String = "",
    val monthLabelText: LocalizedString? = null,
    val totalSpent: Double = 0.0,
    val totalReceived: Double = 0.0,
    val netAmount: Double = 0.0,
    val entryCount: Int = 0,
    val spentByCategory: List<CategoryBreakdown> = emptyList(),
    val receivedByCategory: List<CategoryBreakdown> = emptyList(),
    val memberContributions: List<MemberContribution> = emptyList(),
    val dailyTrend: List<DailyTrend> = emptyList(),
    val budget: Double? = null,
    val budgetProgress: Double = 0.0,
    val budgetRemaining: Double = 0.0,
    val comparisonWithLastMonth: MonthComparison? = null
)

data class HouseholdGamification(
    val loggingStreak: Int = 0,
    val streakStartDate: Long? = null,
    val monthlyBadge: LocalizedString? = null,
    val participationToday: Double = 0.0,
    val membersLoggedToday: Int = 0,
    val totalMembers: Int = 0,
    val insightMessageText: LocalizedString? = null
)
