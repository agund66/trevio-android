package com.trevio.android.util

import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import kotlin.math.round

object Calculations {

    fun calculateSplits(
        totalAmount: Double,
        splitType: SplitType,
        memberUids: List<String>,
        splits: Map<String, SplitEntry> = emptyMap()
    ): Map<String, SplitEntry> {
        val result = mutableMapOf<String, SplitEntry>()

        when (splitType) {
            SplitType.EQUAL -> {
                val perPerson = round((totalAmount / memberUids.size) * 100) / 100
                var allocated = 0.0
                memberUids.forEachIndexed { index, uid ->
                    if (index == memberUids.size - 1) {
                        result[uid] = SplitEntry(amount = round((totalAmount - allocated) * 100) / 100)
                    } else {
                        result[uid] = SplitEntry(amount = perPerson)
                        allocated += perPerson
                    }
                }
            }

            SplitType.EXACT -> {
                memberUids.forEach { uid ->
                    result[uid] = SplitEntry(amount = splits[uid]?.amount ?: 0.0)
                }
            }

            SplitType.PERCENT -> {
                memberUids.forEach { uid ->
                    val percent = splits[uid]?.shareValue ?: 0.0
                    result[uid] = SplitEntry(
                        amount = round((totalAmount * percent / 100) * 100) / 100,
                        shareValue = percent
                    )
                }
            }

            SplitType.SHARES -> {
                val totalShares = memberUids.sumOf { splits[it]?.shareValue ?: 0.0 }
                if (totalShares > 0) {
                    var allocated = 0.0
                    memberUids.forEachIndexed { index, uid ->
                        val shares = splits[uid]?.shareValue ?: 0.0
                        val amount = round((totalAmount * shares / totalShares) * 100) / 100
                        if (index == memberUids.size - 1) {
                            result[uid] = SplitEntry(
                                amount = round((totalAmount - allocated) * 100) / 100,
                                shareValue = shares
                            )
                        } else {
                            result[uid] = SplitEntry(amount = amount, shareValue = shares)
                            allocated += amount
                        }
                    }
                }
            }
        }

        return result
    }

    fun calculateBalances(
        expenses: List<ExpenseBalanceData>,
        settlements: List<Triple<String, String, Double>>,
        memberUids: List<String>
    ): Map<String, Double> {
        val balances = mutableMapOf<String, Double>()
        memberUids.forEach { balances[it] = 0.0 }

        for (expense in expenses) {
            val rate = expense.exchangeRateToBase
            val amountInBase = expense.amount * rate
            balances[expense.paidBy] = (balances[expense.paidBy] ?: 0.0) + amountInBase
            for ((uid, split) in expense.splits) {
                val splitInBase = split.amount * rate
                balances[uid] = (balances[uid] ?: 0.0) - splitInBase
            }
        }

        for ((fromUid, toUid, amount) in settlements) {
            balances[fromUid] = (balances[fromUid] ?: 0.0) + amount
            balances[toUid] = (balances[toUid] ?: 0.0) - amount
        }

        return balances
    }

    data class ExpenseBalanceData(
        val paidBy: String,
        val splits: Map<String, SplitEntry>,
        val amount: Double,
        val exchangeRateToBase: Double = 1.0
    )

    data class SimplifiedDebtRaw(
        val fromUid: String,
        val toUid: String,
        val amount: Double
    )

    fun simplifyDebts(balances: Map<String, Double>): List<SimplifiedDebtRaw> {
        val debts = mutableListOf<SimplifiedDebtRaw>()

        data class Debtor(val uid: String, var amount: Double)
        data class Creditor(val uid: String, var amount: Double)

        val debtors = mutableListOf<Debtor>()
        val creditors = mutableListOf<Creditor>()

        for ((uid, balance) in balances) {
            val rounded = round(balance * 100) / 100
            if (rounded < -0.01) {
                debtors.add(Debtor(uid, -rounded))
            } else if (rounded > 0.01) {
                creditors.add(Creditor(uid, rounded))
            }
        }

        debtors.sortBy { it.amount }
        creditors.sortByDescending { it.amount }

        var dIndex = 0
        var cIndex = 0

        while (dIndex < debtors.size && cIndex < creditors.size) {
            val debtor = debtors[dIndex]
            val creditor = creditors[cIndex]
            val settleAmount = minOf(debtor.amount, creditor.amount)

            if (settleAmount > 0.01) {
                debts.add(SimplifiedDebtRaw(
                    fromUid = debtor.uid,
                    toUid = creditor.uid,
                    amount = round(settleAmount * 100) / 100
                ))
            }

            debtor.amount -= settleAmount
            creditor.amount -= settleAmount

            if (debtor.amount < 0.01) dIndex++
            if (creditor.amount < 0.01) cIndex++
        }

        return debts
    }

    fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun generateBaseUsername(firstName: String, lastName: String): String {
        val first = firstName.lowercase().replace(Regex("[^a-z0-9]"), "")
        val last = lastName.lowercase().replace(Regex("[^a-z0-9]"), "")
        return if (last.isNotEmpty()) "$first.$last" else first
    }
}
