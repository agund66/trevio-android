package com.trevio.android.domain.model

enum class SplitType { EQUAL, EXACT, PERCENT, SHARES }
enum class GroupTemplate { TRIP, TURF, CASUAL }
enum class MemberRole { ADMIN, MEMBER }
enum class MemberStatus { ACTIVE, LEFT }
enum class InvitationStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }
enum class SettlementMethod { UPI, CASH, OTHER }

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
    val archived: Boolean = false
)

data class Member(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoURL: String = "",
    val balance: Double = 0.0,
    val role: String = "member",
    val status: String = "active"
)

data class SplitEntry(
    val amount: Double = 0.0,
    val shareValue: Double = 0.0
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
    val isRecurring: Boolean = false,
    val createdBy: String = "",
    val exchangeRateToBase: Double = 1.0
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
    val date: Long = 0
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
    val createdAt: Long = 0
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
