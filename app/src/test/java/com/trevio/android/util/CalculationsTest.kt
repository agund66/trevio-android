package com.trevio.android.util

import com.google.common.truth.Truth.assertThat
import com.trevio.android.domain.model.BillItem
import com.trevio.android.domain.model.ItemizedSplitData
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import org.junit.Test

class CalculationsTest {

    private val uids = listOf("u1", "u2", "u3", "u4", "u5")

    private fun mkSplits(vararg pairs: Pair<String, Pair<Double, Double>>): Map<String, SplitEntry> {
        return pairs.associate { (uid, values) ->
            uid to SplitEntry(amount = values.first, shareValue = values.second)
        }
    }

    private fun sumSplits(splits: Map<String, SplitEntry>): Double {
        return splits.values.sumOf { it.amount }
    }

    // ─── EQUAL SPLIT ──────────────────────────────────────────────

    @Test
    fun equalSplit_100_3members() {
        val result = Calculations.calculateSplits(100.0, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(result["u1"]!!.amount).isEqualTo(33.33)
        assertThat(result["u2"]!!.amount).isEqualTo(33.33)
        assertThat(result["u3"]!!.amount).isEqualTo(33.34)
    }

    @Test
    fun equalSplit_100_4members() {
        val result = Calculations.calculateSplits(100.0, SplitType.EQUAL, listOf("u1", "u2", "u3", "u4"))
        assertThat(result["u1"]!!.amount).isEqualTo(25.0)
        assertThat(result["u2"]!!.amount).isEqualTo(25.0)
        assertThat(result["u3"]!!.amount).isEqualTo(25.0)
        assertThat(result["u4"]!!.amount).isEqualTo(25.0)
    }

    @Test
    fun equalSplit_100_2members() {
        val result = Calculations.calculateSplits(100.0, SplitType.EQUAL, listOf("u1", "u2"))
        assertThat(result["u1"]!!.amount).isEqualTo(50.0)
        assertThat(result["u2"]!!.amount).isEqualTo(50.0)
    }

    @Test
    fun equalSplit_100_1member() {
        val result = Calculations.calculateSplits(100.0, SplitType.EQUAL, listOf("u1"))
        assertThat(result["u1"]!!.amount).isEqualTo(100.0)
    }

    @Test
    fun equalSplit_0_3members() {
        val result = Calculations.calculateSplits(0.0, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(result["u1"]!!.amount).isEqualTo(0.0)
        assertThat(result["u2"]!!.amount).isEqualTo(0.0)
        assertThat(result["u3"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun equalSplit_99_99_3members() {
        val result = Calculations.calculateSplits(99.99, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(sumSplits(result)).isWithin(0.01).of(99.99)
    }

    @Test
    fun equalSplit_1000_7members() {
        val result = Calculations.calculateSplits(1000.0, SplitType.EQUAL, listOf("u1", "u2", "u3", "u4", "u5", "u6", "u7"))
        assertThat(sumSplits(result)).isWithin(0.01).of(1000.0)
    }

    @Test
    fun equalSplit_10_3members_lastGetsRemainder() {
        val result = Calculations.calculateSplits(10.0, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(result["u1"]!!.amount).isEqualTo(3.33)
        assertThat(result["u2"]!!.amount).isEqualTo(3.33)
        assertThat(result["u3"]!!.amount).isEqualTo(3.34)
    }

    @Test
    fun equalSplit_200_3members() {
        val result = Calculations.calculateSplits(200.0, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(result["u1"]!!.amount).isEqualTo(66.67)
        assertThat(result["u2"]!!.amount).isEqualTo(66.67)
        assertThat(result["u3"]!!.amount).isEqualTo(66.66)
    }

    @Test
    fun equalSplit_emptyMembers() {
        val result = Calculations.calculateSplits(100.0, SplitType.EQUAL, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun equalSplit_1_3members() {
        val result = Calculations.calculateSplits(1.0, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(sumSplits(result)).isWithin(0.01).of(1.0)
    }

    @Test
    fun equalSplit_500_8members() {
        val result = Calculations.calculateSplits(500.0, SplitType.EQUAL, uids + listOf("u6", "u7", "u8"))
        assertThat(sumSplits(result)).isWithin(0.01).of(500.0)
    }

    @Test
    fun equalSplit_1234_56_5members() {
        val result = Calculations.calculateSplits(1234.56, SplitType.EQUAL, uids)
        assertThat(sumSplits(result)).isWithin(0.01).of(1234.56)
    }

    @Test
    fun equalSplit_1000000_4members() {
        val result = Calculations.calculateSplits(1000000.0, SplitType.EQUAL, listOf("u1", "u2", "u3", "u4"))
        assertThat(result["u1"]!!.amount).isEqualTo(250000.0)
        assertThat(result["u4"]!!.amount).isEqualTo(250000.0)
    }

    @Test
    fun equalSplit_0_01_3members() {
        val result = Calculations.calculateSplits(0.01, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(sumSplits(result)).isWithin(0.01).of(0.01)
    }

    @Test
    fun equalSplit_50_05_4members() {
        val result = Calculations.calculateSplits(50.05, SplitType.EQUAL, listOf("u1", "u2", "u3", "u4"))
        assertThat(sumSplits(result)).isWithin(0.01).of(50.05)
    }

    @Test
    fun equalSplit_33_33_3members() {
        val result = Calculations.calculateSplits(33.33, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(sumSplits(result)).isWithin(0.01).of(33.33)
    }

    @Test
    fun equalSplit_0_03_3members() {
        val result = Calculations.calculateSplits(0.03, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(sumSplits(result)).isWithin(0.01).of(0.03)
    }

    @Test
    fun equalSplit_999999_99_3members() {
        val result = Calculations.calculateSplits(999999.99, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(sumSplits(result)).isWithin(0.01).of(999999.99)
    }

    @Test
    fun equalSplit_100_6members() {
        val result = Calculations.calculateSplits(100.0, SplitType.EQUAL, listOf("u1", "u2", "u3", "u4", "u5", "u6"))
        assertThat(sumSplits(result)).isWithin(0.01).of(100.0)
    }

    @Test
    fun equalSplit_700_7members() {
        val result = Calculations.calculateSplits(700.0, SplitType.EQUAL, listOf("u1", "u2", "u3", "u4", "u5", "u6", "u7"))
        assertThat(result["u1"]!!.amount).isEqualTo(100.0)
        assertThat(result["u7"]!!.amount).isEqualTo(100.0)
    }

    @Test
    fun equalSplit_negativeAmount() {
        val result = Calculations.calculateSplits(-100.0, SplitType.EQUAL, listOf("u1", "u2"))
        assertThat(result["u1"]!!.amount).isEqualTo(-50.0)
        assertThat(result["u2"]!!.amount).isEqualTo(-50.0)
    }

    @Test
    fun equalSplit_100_100members() {
        val members = (1..100).map { "u$it" }
        val result = Calculations.calculateSplits(100.0, SplitType.EQUAL, members)
        assertThat(sumSplits(result)).isWithin(0.01).of(100.0)
    }

    @Test
    fun equalSplit_0_1_3members() {
        val result = Calculations.calculateSplits(0.1, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(sumSplits(result)).isWithin(0.01).of(0.1)
    }

    @Test
    fun equalSplit_0_02_3members() {
        val result = Calculations.calculateSplits(0.02, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(sumSplits(result)).isWithin(0.01).of(0.02)
    }

    @Test
    fun equalSplit_1000_3members() {
        val result = Calculations.calculateSplits(1000.0, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(result["u1"]!!.amount).isEqualTo(333.33)
        assertThat(result["u3"]!!.amount).isEqualTo(333.34)
    }

    @Test
    fun equalSplit_33_33_2members() {
        val result = Calculations.calculateSplits(33.33, SplitType.EQUAL, listOf("u1", "u2"))
        assertThat(result["u1"]!!.amount).isEqualTo(16.66)
        assertThat(result["u2"]!!.amount).isEqualTo(16.67)
    }

    // ─── EXACT SPLIT ──────────────────────────────────────────────

    @Test
    fun exactSplit_3members() {
        val splits = mkSplits("u1" to (30.0 to 0.0), "u2" to (30.0 to 0.0), "u3" to (40.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(30.0)
        assertThat(result["u2"]!!.amount).isEqualTo(30.0)
        assertThat(result["u3"]!!.amount).isEqualTo(40.0)
    }

    @Test
    fun exactSplit_missingMemberDefaultsTo0() {
        val splits = mkSplits("u1" to (100.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(100.0)
        assertThat(result["u2"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun exactSplit_noSplitsProvided() {
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2", "u3"))
        assertThat(result["u1"]!!.amount).isEqualTo(0.0)
        assertThat(result["u2"]!!.amount).isEqualTo(0.0)
        assertThat(result["u3"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun exactSplit_emptySplitsMap() {
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2"), emptyMap())
        assertThat(result["u1"]!!.amount).isEqualTo(0.0)
        assertThat(result["u2"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun exactSplit_1member() {
        val splits = mkSplits("u1" to (50.0 to 0.0))
        val result = Calculations.calculateSplits(50.0, SplitType.EXACT, listOf("u1"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(50.0)
    }

    @Test
    fun exactSplit_unequalAmounts() {
        val splits = mkSplits("u1" to (10.50 to 0.0), "u2" to (20.25 to 0.0), "u3" to (69.25 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(10.50)
        assertThat(result["u2"]!!.amount).isEqualTo(20.25)
        assertThat(result["u3"]!!.amount).isEqualTo(69.25)
    }

    @Test
    fun exactSplit_zeroForMember() {
        val splits = mkSplits("u1" to (100.0 to 0.0), "u2" to (0.0 to 0.0), "u3" to (0.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(100.0)
        assertThat(result["u2"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun exactSplit_allZero() {
        val splits = mkSplits("u1" to (0.0 to 0.0), "u2" to (0.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2"), splits)
        assertThat(sumSplits(result)).isEqualTo(0.0)
    }

    @Test
    fun exactSplit_verySmallAmounts() {
        val splits = mkSplits("u1" to (0.01 to 0.0), "u2" to (0.01 to 0.0))
        val result = Calculations.calculateSplits(0.02, SplitType.EXACT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(0.01)
        assertThat(result["u2"]!!.amount).isEqualTo(0.01)
    }

    @Test
    fun exactSplit_largeAmounts() {
        val splits = mkSplits("u1" to (500000.0 to 0.0), "u2" to (500000.0 to 0.0))
        val result = Calculations.calculateSplits(1000000.0, SplitType.EXACT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(500000.0)
        assertThat(result["u2"]!!.amount).isEqualTo(500000.0)
    }

    @Test
    fun exactSplit_doesNotEnforceSumEqualsTotal() {
        val splits = mkSplits("u1" to (10.0 to 0.0), "u2" to (10.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2"), splits)
        assertThat(sumSplits(result)).isEqualTo(20.0)
    }

    @Test
    fun exactSplit_5members() {
        val splits = mkSplits("u1" to (20.0 to 0.0), "u2" to (20.0 to 0.0), "u3" to (20.0 to 0.0), "u4" to (20.0 to 0.0), "u5" to (20.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, uids, splits)
        assertThat(sumSplits(result)).isEqualTo(100.0)
    }

    @Test
    fun exactSplit_emptyMembers() {
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun exactSplit_extraEntriesNotInMembers() {
        val splits = mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0), "u3" to (100.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(50.0)
        assertThat(result["u2"]!!.amount).isEqualTo(50.0)
        assertThat(result["u3"]).isNull()
    }

    @Test
    fun exactSplit_decimalAmounts() {
        val splits = mkSplits("u1" to (33.33 to 0.0), "u2" to (33.33 to 0.0), "u3" to (33.34 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2", "u3"), splits)
        assertThat(sumSplits(result)).isEqualTo(100.0)
    }

    @Test
    fun exactSplit_allSameAmount() {
        val splits = mkSplits("u1" to (25.0 to 0.0), "u2" to (25.0 to 0.0), "u3" to (25.0 to 0.0), "u4" to (25.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.EXACT, listOf("u1", "u2", "u3", "u4"), splits)
        assertThat(sumSplits(result)).isEqualTo(100.0)
    }

    // ─── PERCENT SPLIT ────────────────────────────────────────────

    @Test
    fun percentSplit_50_25_25() {
        val splits = mkSplits("u1" to (0.0 to 50.0), "u2" to (0.0 to 25.0), "u3" to (0.0 to 25.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(50.0)
        assertThat(result["u2"]!!.amount).isEqualTo(25.0)
        assertThat(result["u3"]!!.amount).isEqualTo(25.0)
    }

    @Test
    fun percentSplit_1000_10_20_30_40() {
        val splits = mkSplits("u1" to (0.0 to 10.0), "u2" to (0.0 to 20.0), "u3" to (0.0 to 30.0), "u4" to (0.0 to 40.0))
        val result = Calculations.calculateSplits(1000.0, SplitType.PERCENT, listOf("u1", "u2", "u3", "u4"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(100.0)
        assertThat(result["u2"]!!.amount).isEqualTo(200.0)
        assertThat(result["u3"]!!.amount).isEqualTo(300.0)
        assertThat(result["u4"]!!.amount).isEqualTo(400.0)
    }

    @Test
    fun percentSplit_0_0_100() {
        val splits = mkSplits("u1" to (0.0 to 0.0), "u2" to (0.0 to 0.0), "u3" to (0.0 to 100.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(0.0)
        assertThat(result["u3"]!!.amount).isEqualTo(100.0)
    }

    @Test
    fun percentSplit_noSplitsProvided() {
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2", "u3"))
        assertThat(result["u1"]!!.amount).isEqualTo(0.0)
        assertThat(result["u2"]!!.amount).isEqualTo(0.0)
        assertThat(result["u3"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun percentSplit_50_50() {
        val splits = mkSplits("u1" to (0.0 to 50.0), "u2" to (0.0 to 50.0))
        val result = Calculations.calculateSplits(50.0, SplitType.PERCENT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(25.0)
        assertThat(result["u2"]!!.amount).isEqualTo(25.0)
    }

    @Test
    fun percentSplit_100_singleMember() {
        val splits = mkSplits("u1" to (0.0 to 100.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(100.0)
    }

    @Test
    fun percentSplit_25_25_25_25() {
        val splits = mkSplits("u1" to (0.0 to 25.0), "u2" to (0.0 to 25.0), "u3" to (0.0 to 25.0), "u4" to (0.0 to 25.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2", "u3", "u4"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(25.0)
        assertThat(result["u4"]!!.amount).isEqualTo(25.0)
    }

    @Test
    fun percentSplit_0_1_0_1_99_8() {
        val splits = mkSplits("u1" to (0.0 to 0.1), "u2" to (0.0 to 0.1), "u3" to (0.0 to 99.8))
        val result = Calculations.calculateSplits(1000.0, SplitType.PERCENT, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(1.0)
        assertThat(result["u3"]!!.amount).isEqualTo(998.0)
    }

    @Test
    fun percentSplit_preservesShareValue() {
        val splits = mkSplits("u1" to (0.0 to 40.0), "u2" to (0.0 to 60.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.shareValue).isEqualTo(40.0)
        assertThat(result["u2"]!!.shareValue).isEqualTo(60.0)
    }

    @Test
    fun percentSplit_missingShareValueDefaultsTo0() {
        val splits = mkSplits("u1" to (0.0 to 100.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(100.0)
        assertThat(result["u2"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun percentSplit_0total() {
        val splits = mkSplits("u1" to (0.0 to 50.0), "u2" to (0.0 to 50.0))
        val result = Calculations.calculateSplits(0.0, SplitType.PERCENT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun percentSplit_1_1_98() {
        val splits = mkSplits("u1" to (0.0 to 1.0), "u2" to (0.0 to 1.0), "u3" to (0.0 to 98.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(1.0)
        assertThat(result["u3"]!!.amount).isEqualTo(98.0)
    }

    @Test
    fun percentSplit_emptyMembers() {
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun percentSplit_150total_overallocated() {
        val splits = mkSplits("u1" to (0.0 to 75.0), "u2" to (0.0 to 75.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(75.0)
        assertThat(result["u2"]!!.amount).isEqualTo(75.0)
    }

    @Test
    fun percentSplit_50_0_0() {
        val splits = mkSplits("u1" to (0.0 to 50.0), "u2" to (0.0 to 0.0), "u3" to (0.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.PERCENT, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(50.0)
        assertThat(result["u2"]!!.amount).isEqualTo(0.0)
    }

    // ─── SHARES SPLIT ─────────────────────────────────────────────

    @Test
    fun sharesSplit_1_2_3() {
        val splits = mkSplits("u1" to (0.0 to 1.0), "u2" to (0.0 to 2.0), "u3" to (0.0 to 3.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(16.67)
        assertThat(result["u2"]!!.amount).isEqualTo(33.33)
        assertThat(result["u3"]!!.amount).isEqualTo(50.0)
    }

    @Test
    fun sharesSplit_1_1_1_1() {
        val splits = mkSplits("u1" to (0.0 to 1.0), "u2" to (0.0 to 1.0), "u3" to (0.0 to 1.0), "u4" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3", "u4"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(25.0)
    }

    @Test
    fun sharesSplit_0_0_0_totalSharesZero() {
        val splits = mkSplits("u1" to (0.0 to 0.0), "u2" to (0.0 to 0.0), "u3" to (0.0 to 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(result).isEmpty()
    }

    @Test
    fun sharesSplit_1_1() {
        val splits = mkSplits("u1" to (0.0 to 1.0), "u2" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(50.0)
        assertThat(result["u2"]!!.amount).isEqualTo(50.0)
    }

    @Test
    fun sharesSplit_3_1() {
        val splits = mkSplits("u1" to (0.0 to 3.0), "u2" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(75.0)
        assertThat(result["u2"]!!.amount).isEqualTo(25.0)
    }

    @Test
    fun sharesSplit_1_2_3_4() {
        val splits = mkSplits("u1" to (0.0 to 1.0), "u2" to (0.0 to 2.0), "u3" to (0.0 to 3.0), "u4" to (0.0 to 4.0))
        val result = Calculations.calculateSplits(1000.0, SplitType.SHARES, listOf("u1", "u2", "u3", "u4"), splits)
        assertThat(sumSplits(result)).isWithin(0.01).of(1000.0)
    }

    @Test
    fun sharesSplit_10_20_30() {
        val splits = mkSplits("u1" to (0.0 to 10.0), "u2" to (0.0 to 20.0), "u3" to (0.0 to 30.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(16.67)
        assertThat(result["u3"]!!.amount).isEqualTo(50.0)
    }

    @Test
    fun sharesSplit_0_1_1_zeroShareGets0() {
        val splits = mkSplits("u1" to (0.0 to 0.0), "u2" to (0.0 to 1.0), "u3" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(0.0)
        assertThat(result["u2"]!!.amount).isEqualTo(50.0)
        assertThat(result["u3"]!!.amount).isEqualTo(50.0)
    }

    @Test
    fun sharesSplit_2_2_1() {
        val splits = mkSplits("u1" to (0.0 to 2.0), "u2" to (0.0 to 2.0), "u3" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(sumSplits(result)).isWithin(0.01).of(100.0)
    }

    @Test
    fun sharesSplit_singleMember() {
        val splits = mkSplits("u1" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(100.0)
    }

    @Test
    fun sharesSplit_5_3_2() {
        val splits = mkSplits("u1" to (0.0 to 5.0), "u2" to (0.0 to 3.0), "u3" to (0.0 to 2.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(50.0)
        assertThat(result["u2"]!!.amount).isEqualTo(30.0)
        assertThat(result["u3"]!!.amount).isEqualTo(20.0)
    }

    @Test
    fun sharesSplit_100_1() {
        val splits = mkSplits("u1" to (0.0 to 100.0), "u2" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(99.01)
        assertThat(result["u2"]!!.amount).isEqualTo(0.99)
    }

    @Test
    fun sharesSplit_0total() {
        val splits = mkSplits("u1" to (0.0 to 1.0), "u2" to (0.0 to 2.0), "u3" to (0.0 to 3.0))
        val result = Calculations.calculateSplits(0.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(0.0)
    }

    @Test
    fun sharesSplit_preservesShareValue() {
        val splits = mkSplits("u1" to (0.0 to 1.0), "u2" to (0.0 to 1.0), "u3" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(result["u1"]!!.shareValue).isEqualTo(1.0)
    }

    @Test
    fun sharesSplit_noSplitsProvided() {
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"))
        assertThat(result).isEmpty()
    }

    @Test
    fun sharesSplit_emptyMembers() {
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun sharesSplit_1_1_1_1_1() {
        val splits = mkSplits("u1" to (0.0 to 1.0), "u2" to (0.0 to 1.0), "u3" to (0.0 to 1.0), "u4" to (0.0 to 1.0), "u5" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, uids, splits)
        assertThat(result["u1"]!!.amount).isEqualTo(20.0)
        assertThat(result["u5"]!!.amount).isEqualTo(20.0)
    }

    @Test
    fun sharesSplit_fractional_0_5_0_5_1() {
        val splits = mkSplits("u1" to (0.0 to 0.5), "u2" to (0.0 to 0.5), "u3" to (0.0 to 1.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2", "u3"), splits)
        assertThat(sumSplits(result)).isWithin(0.01).of(100.0)
    }

    @Test
    fun sharesSplit_largeShareValues() {
        val splits = mkSplits("u1" to (0.0 to 1000000.0), "u2" to (0.0 to 1000000.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2"), splits)
        assertThat(result["u1"]!!.amount).isEqualTo(50.0)
    }

    // ─── CALCULATE BALANCES ───────────────────────────────────────

    @Test
    fun balances_noExpensesNoSettlements() {
        val result = Calculations.calculateBalances(emptyList(), emptyList(), listOf("u1", "u2", "u3"))
        assertThat(result["u1"]).isEqualTo(0.0)
        assertThat(result["u2"]).isEqualTo(0.0)
        assertThat(result["u3"]).isEqualTo(0.0)
    }

    @Test
    fun balances_payerPositiveSplittersNegative() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData(
                paidBy = "u1",
                splits = mkSplits("u1" to (33.33 to 0.0), "u2" to (33.33 to 0.0), "u3" to (33.34 to 0.0)),
                amount = 100.0,
                exchangeRateToBase = 1.0
            )
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2", "u3"))
        assertThat(result["u1"]!!).isWithin(0.01).of(66.67)
        assertThat(result["u2"]!!).isWithin(0.01).of(-33.33)
        assertThat(result["u3"]!!).isWithin(0.01).of(-33.34)
    }

    @Test
    fun balances_multipleExpensesAccumulate() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 1.0),
            Calculations.ExpenseBalanceData("u2", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 1.0)
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(0.0)
        assertThat(result["u2"]).isEqualTo(0.0)
    }

    @Test
    fun balances_settlementAdjusts() {
        val settlements = listOf(Triple("u1", "u2", 50.0))
        val result = Calculations.calculateBalances(emptyList(), settlements, listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(50.0)
        assertThat(result["u2"]).isEqualTo(-50.0)
    }

    @Test
    fun balances_expenseWithExchangeRate() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 83.5)
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(4175.0)
        assertThat(result["u2"]).isEqualTo(-4175.0)
    }

    @Test
    fun balances_multipleSettlementsAccumulate() {
        val settlements = listOf(Triple("u1", "u2", 30.0), Triple("u1", "u2", 20.0))
        val result = Calculations.calculateBalances(emptyList(), settlements, listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(50.0)
        assertThat(result["u2"]).isEqualTo(-50.0)
    }

    @Test
    fun balances_settlementsBetweenDifferentPairs() {
        val settlements = listOf(Triple("u1", "u2", 30.0), Triple("u3", "u2", 20.0))
        val result = Calculations.calculateBalances(emptyList(), settlements, listOf("u1", "u2", "u3"))
        assertThat(result["u1"]).isEqualTo(30.0)
        assertThat(result["u2"]).isEqualTo(-50.0)
        assertThat(result["u3"]).isEqualTo(20.0)
    }

    @Test
    fun balances_expenseAndSettlementCombined() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 1.0)
        )
        val settlements = listOf(Triple("u2", "u1", 50.0))
        val result = Calculations.calculateBalances(expenses, settlements, listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(0.0)
        assertThat(result["u2"]).isEqualTo(0.0)
    }

    @Test
    fun balances_payerNotInSplits() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u2" to (100.0 to 0.0)), 100.0, 1.0)
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(100.0)
        assertThat(result["u2"]).isEqualTo(-100.0)
    }

    @Test
    fun balances_payerIsOnlySplitter() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (100.0 to 0.0)), 100.0, 1.0)
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1"))
        assertThat(result["u1"]).isEqualTo(0.0)
    }

    @Test
    fun balances_emptyMemberList() {
        val result = Calculations.calculateBalances(emptyList(), emptyList(), emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun balances_emptySplits() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", emptyMap(), 100.0, 1.0)
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1"))
        assertThat(result["u1"]).isEqualTo(100.0)
    }

    @Test
    fun balances_selfSettlement() {
        val settlements = listOf(Triple("u1", "u1", 100.0))
        val result = Calculations.calculateBalances(emptyList(), settlements, listOf("u1"))
        assertThat(result["u1"]).isEqualTo(0.0)
    }

    @Test
    fun balances_largeNumberOfExpenses() {
        val expenses = (1..50).map {
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 1.0)
        }
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(2500.0)
        assertThat(result["u2"]).isEqualTo(-2500.0)
    }

    @Test
    fun balances_memberNotInAnyExpense() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 1.0)
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2", "u3"))
        assertThat(result["u3"]).isEqualTo(0.0)
    }

    @Test
    fun balances_zeroSettlementAmount() {
        val settlements = listOf(Triple("u1", "u2", 0.0))
        val result = Calculations.calculateBalances(emptyList(), settlements, listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(0.0)
        assertThat(result["u2"]).isEqualTo(0.0)
    }

    @Test
    fun balances_mixedCurrencies() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 1.0),
            Calculations.ExpenseBalanceData("u2", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 83.5)
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2"))
        assertThat(result["u1"]).isEqualTo(-4125.0)
        assertThat(result["u2"]).isEqualTo(4125.0)
    }

    @Test
    fun balances_5membersComplexScenario() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (20.0 to 0.0), "u2" to (20.0 to 0.0), "u3" to (20.0 to 0.0), "u4" to (20.0 to 0.0), "u5" to (20.0 to 0.0)), 100.0, 1.0),
            Calculations.ExpenseBalanceData("u2", mkSplits("u1" to (50.0 to 0.0), "u2" to (50.0 to 0.0)), 100.0, 1.0)
        )
        val settlements = listOf(Triple("u3", "u1", 20.0))
        val result = Calculations.calculateBalances(expenses, settlements, uids)
        assertThat(result["u1"]).isEqualTo(10.0)
        assertThat(result["u2"]).isEqualTo(30.0)
        assertThat(result["u3"]).isEqualTo(0.0)
        assertThat(result["u4"]).isEqualTo(-20.0)
        assertThat(result["u5"]).isEqualTo(-20.0)
    }

    @Test
    fun balances_eurExchangeRate() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mkSplits("u1" to (30.0 to 0.0), "u2" to (30.0 to 0.0), "u3" to (40.0 to 0.0)), 100.0, 90.5)
        )
        val result = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2", "u3"))
        assertThat(result["u1"]).isEqualTo(100.0 * 90.5 - 30.0 * 90.5)
        assertThat(result["u2"]).isEqualTo(-30.0 * 90.5)
    }

    // ─── SIMPLIFY DEBTS ───────────────────────────────────────────

    @Test
    fun simplifyDebts_allZero() {
        val balances = mapOf("u1" to 0.0, "u2" to 0.0)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).isEmpty()
    }

    @Test
    fun simplifyDebts_debtorCreditorPair() {
        val balances = mapOf("u1" to -100.0, "u2" to 100.0)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).hasSize(1)
        assertThat(result[0].fromUid).isEqualTo("u1")
        assertThat(result[0].toUid).isEqualTo("u2")
        assertThat(result[0].amount).isEqualTo(100.0)
    }

    @Test
    fun simplifyDebts_balancedSum0() {
        val balances = mapOf("u1" to 50.0, "u2" to -50.0)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).hasSize(1)
        assertThat(result[0].fromUid).isEqualTo("u2")
        assertThat(result[0].toUid).isEqualTo("u1")
    }

    @Test
    fun simplifyDebts_twoDebtorsOneCreditor() {
        val balances = mapOf("u1" to -50.0, "u2" to -50.0, "u3" to 100.0)
        val result = Calculations.simplifyDebts(balances)
        val total = result.sumOf { it.amount }
        assertThat(total).isEqualTo(100.0)
    }

    @Test
    fun simplifyDebts_oneDebtorTwoCreditors() {
        val balances = mapOf("u1" to -100.0, "u2" to 50.0, "u3" to 50.0)
        val result = Calculations.simplifyDebts(balances)
        val total = result.sumOf { it.amount }
        assertThat(total).isEqualTo(100.0)
    }

    @Test
    fun simplifyDebts_ignoresThreshold() {
        val balances = mapOf("u1" to 0.005, "u2" to -0.005)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).isEmpty()
    }

    @Test
    fun simplifyDebts_exactly001() {
        val balances = mapOf("u1" to 0.01, "u2" to -0.01)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).isEmpty()
    }

    @Test
    fun simplifyDebts_002() {
        val balances = mapOf("u1" to 0.02, "u2" to -0.02)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).hasSize(1)
        assertThat(result[0].amount).isEqualTo(0.02)
    }

    @Test
    fun simplifyDebts_fiveMembers() {
        val balances = mapOf("u1" to -100.0, "u2" to -50.0, "u3" to 30.0, "u4" to 70.0, "u5" to 50.0)
        val result = Calculations.simplifyDebts(balances)
        val total = result.sumOf { it.amount }
        assertThat(total).isWithin(0.01).of(150.0)
    }

    @Test
    fun simplifyDebts_largeAmount() {
        val balances = mapOf("u1" to -1000000.0, "u2" to 1000000.0)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).hasSize(1)
        assertThat(result[0].amount).isEqualTo(1000000.0)
    }

    @Test
    fun simplifyDebts_emptyBalances() {
        val result = Calculations.simplifyDebts(emptyMap())
        assertThat(result).isEmpty()
    }

    @Test
    fun simplifyDebts_onlyDebtors() {
        val balances = mapOf("u1" to -100.0, "u2" to -50.0)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).isEmpty()
    }

    @Test
    fun simplifyDebts_onlyCreditors() {
        val balances = mapOf("u1" to 100.0, "u2" to 50.0)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).isEmpty()
    }

    @Test
    fun simplifyDebts_amountsAlwaysPositive() {
        val balances = mapOf("u1" to -100.0, "u2" to 50.0, "u3" to 50.0)
        val result = Calculations.simplifyDebts(balances)
        result.forEach { assertThat(it.amount).isGreaterThan(0.0) }
    }

    @Test
    fun simplifyDebts_fromUidIsDebtor() {
        val balances = mapOf("u1" to -100.0, "u2" to 100.0)
        val result = Calculations.simplifyDebts(balances)
        result.forEach {
            assertThat(balances[it.fromUid]!!).isLessThan(0.0)
            assertThat(balances[it.toUid]!!).isGreaterThan(0.0)
        }
    }

    @Test
    fun simplifyDebts_rounding() {
        val balances = mapOf("u1" to -33.33, "u2" to -33.33, "u3" to 66.66)
        val result = Calculations.simplifyDebts(balances)
        val total = result.sumOf { it.amount }
        assertThat(total).isWithin(0.01).of(66.66)
    }

    @Test
    fun simplifyDebts_oneDebtorMultipleCreditors() {
        val balances = mapOf("u1" to -100.0, "u2" to 80.0, "u3" to 20.0)
        val result = Calculations.simplifyDebts(balances)
        val total = result.sumOf { it.amount }
        assertThat(total).isEqualTo(100.0)
    }

    @Test
    fun simplifyDebts_4membersComplex() {
        val balances = mapOf("u1" to 200.0, "u2" to -50.0, "u3" to -100.0, "u4" to -50.0)
        val result = Calculations.simplifyDebts(balances)
        val total = result.sumOf { it.amount }
        assertThat(total).isEqualTo(200.0)
        result.forEach { assertThat(it.fromUid).isNotEqualTo(it.toUid) }
    }

    @Test
    fun simplifyDebts_exactMatch() {
        val balances = mapOf("u1" to -250.50, "u2" to 250.50)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).hasSize(1)
        assertThat(result[0].amount).isEqualTo(250.5)
    }

    @Test
    fun simplifyDebts_multipleEqualDebtorsCreditors() {
        val balances = mapOf("u1" to -50.0, "u2" to -50.0, "u3" to 50.0, "u4" to 50.0)
        val result = Calculations.simplifyDebts(balances)
        val total = result.sumOf { it.amount }
        assertThat(total).isEqualTo(100.0)
    }

    @Test
    fun simplifyDebts_singleMemberZero() {
        val balances = mapOf("u1" to 0.0)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).isEmpty()
    }

    @Test
    fun simplifyDebts_allMembersZero() {
        val balances = mapOf("u1" to 0.0, "u2" to 0.0, "u3" to 0.0)
        val result = Calculations.simplifyDebts(balances)
        assertThat(result).isEmpty()
    }

    @Test
    fun simplifyDebts_multipleDebtorsSingleCreditor() {
        val balances = mapOf("u1" to -25.0, "u2" to -75.0, "u3" to -50.0, "u4" to 150.0)
        val result = Calculations.simplifyDebts(balances)
        val total = result.sumOf { it.amount }
        assertThat(total).isEqualTo(150.0)
    }

    // ─── GENERATE INVITE CODE ─────────────────────────────────────

    @Test
    fun inviteCode_length6() {
        val code = Calculations.generateInviteCode()
        assertThat(code).hasLength(6)
    }

    @Test
    fun inviteCode_uppercaseAlphanumeric() {
        val code = Calculations.generateInviteCode()
        assertThat(code).matches("[A-Z0-9]+")
    }

    @Test
    fun inviteCode_excludesAmbiguousChars() {
        repeat(100) {
            val code = Calculations.generateInviteCode()
            assertThat(code).doesNotContain("0")
            assertThat(code).doesNotContain("O")
            assertThat(code).doesNotContain("I")
            assertThat(code).doesNotContain("1")
        }
    }

    @Test
    fun inviteCode_randomness() {
        val codes = (1..100).map { Calculations.generateInviteCode() }.toSet()
        assertThat(codes.size).isGreaterThan(1)
    }

    @Test
    fun inviteCode_validCharset() {
        val validChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(50) {
            val code = Calculations.generateInviteCode()
            code.forEach { char -> assertThat(validChars.contains(char)).isTrue() }
        }
    }

    @Test
    fun inviteCode_isUppercase() {
        val code = Calculations.generateInviteCode()
        assertThat(code).isEqualTo(code.uppercase())
    }

    @Test
    fun inviteCode_1000CodesAllLength6() {
        repeat(1000) {
            assertThat(Calculations.generateInviteCode()).hasLength(6)
        }
    }

    @Test
    fun inviteCode_uniqueWithHighProbability() {
        val codes = (1..1000).map { Calculations.generateInviteCode() }.toSet()
        assertThat(codes.size).isGreaterThan(900)
    }

    // ─── GENERATE BASE USERNAME ───────────────────────────────────

    @Test
    fun username_fullName() {
        assertThat(Calculations.generateBaseUsername("John", "Doe")).isEqualTo("john.doe")
    }

    @Test
    fun username_lowercases() {
        assertThat(Calculations.generateBaseUsername("JANE", "SMITH")).isEqualTo("jane.smith")
    }

    @Test
    fun username_removesSpecialFromFirst() {
        assertThat(Calculations.generateBaseUsername("John@", "Doe")).isEqualTo("john.doe")
    }

    @Test
    fun username_removesSpecialFromLast() {
        assertThat(Calculations.generateBaseUsername("John", "Doe!")).isEqualTo("john.doe")
    }

    @Test
    fun username_emptyFirst() {
        assertThat(Calculations.generateBaseUsername("", "Doe")).isEqualTo(".doe")
    }

    @Test
    fun username_emptyLast() {
        assertThat(Calculations.generateBaseUsername("John", "")).isEqualTo("john")
    }

    @Test
    fun username_bothEmpty() {
        assertThat(Calculations.generateBaseUsername("", "")).isEqualTo("")
    }

    @Test
    fun username_withNumbers() {
        assertThat(Calculations.generateBaseUsername("John123", "Doe456")).isEqualTo("john123.doe456")
    }

    @Test
    fun username_withSpaces() {
        assertThat(Calculations.generateBaseUsername("John Paul", "Doe")).isEqualTo("johnpaul.doe")
    }

    @Test
    fun username_withHyphens() {
        assertThat(Calculations.generateBaseUsername("Jean-Paul", "Doe")).isEqualTo("jeanpaul.doe")
    }

    @Test
    fun username_withApostrophes() {
        assertThat(Calculations.generateBaseUsername("O'Brien", "Doe")).isEqualTo("obrien.doe")
    }

    @Test
    fun username_singleChar() {
        assertThat(Calculations.generateBaseUsername("A", "B")).isEqualTo("a.b")
    }

    @Test
    fun username_specialOnlyFirst() {
        assertThat(Calculations.generateBaseUsername("@#$", "Doe")).isEqualTo(".doe")
    }

    @Test
    fun username_specialOnlyLast() {
        assertThat(Calculations.generateBaseUsername("John", "@#$")).isEqualTo("john")
    }

    @Test
    fun username_specialOnlyBoth() {
        assertThat(Calculations.generateBaseUsername("@#$", "%^&")).isEqualTo("")
    }

    @Test
    fun username_mixedCase() {
        assertThat(Calculations.generateBaseUsername("jOhN", "dOe")).isEqualTo("john.doe")
    }

    @Test
    fun username_numbersOnly() {
        assertThat(Calculations.generateBaseUsername("123", "456")).isEqualTo("123.456")
    }

    @Test
    fun username_trailingSpaces() {
        assertThat(Calculations.generateBaseUsername("John ", " Doe")).isEqualTo("john.doe")
    }

    @Test
    fun username_onlySpaces() {
        assertThat(Calculations.generateBaseUsername("   ", "   ")).isEqualTo("")
    }

    @Test
    fun username_mixedAlphanumericSpecial() {
        assertThat(Calculations.generateBaseUsername("John@123", "Doe#456")).isEqualTo("john123.doe456")
    }

    @Test
    fun username_containsDot() {
        val result = Calculations.generateBaseUsername("John", "Doe")
        assertThat(result).contains(".")
    }

    @Test
    fun username_emptyFirstWithValidLast() {
        assertThat(Calculations.generateBaseUsername("", "Smith")).isEqualTo(".smith")
    }

    @Test
    fun username_dotsInName() {
        assertThat(Calculations.generateBaseUsername("John.Jr", "Doe")).isEqualTo("johnjr.doe")
    }

    @Test
    fun username_underscores() {
        assertThat(Calculations.generateBaseUsername("John_Doe", "Smith")).isEqualTo("johndoe.smith")
    }

    @Test
    fun username_longNames() {
        val longFirst = "A".repeat(100)
        val longLast = "B".repeat(100)
        assertThat(Calculations.generateBaseUsername(longFirst, longLast)).isEqualTo("${longFirst.lowercase()}.${longLast.lowercase()}")
    }

    // ─── ITEMIZED SPLIT TESTS ─────────────────────────────────────

    private fun mkItemized(
        items: List<Triple<String, Double, List<String>>>,
        tax: Double = 0.0,
        tip: Double = 0.0,
        taxMode: String = "proportional",
        tipMode: String = "proportional"
    ): ItemizedSplitData {
        return ItemizedSplitData(
            items = items.mapIndexed { i, (name, amount, assigned) ->
                BillItem(itemId = "item_$i", name = name, amount = amount, assignedTo = assigned)
            },
            taxAmount = tax,
            tipAmount = tip,
            taxSplitMode = taxMode,
            tipSplitMode = tipMode
        )
    }

    @Test
    fun itemized_emptyItems_returnsZeros() {
        val result = Calculations.calculateSplits(0.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = mkItemized(emptyList()))
        assertThat(result["u1"]?.amount).isEqualTo(0.0)
        assertThat(result["u2"]?.amount).isEqualTo(0.0)
    }

    @Test
    fun itemized_undefinedData_returnsZeros() {
        val result = Calculations.calculateSplits(0.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = null)
        assertThat(result["u1"]?.amount).isEqualTo(0.0)
        assertThat(result["u2"]?.amount).isEqualTo(0.0)
    }

    @Test
    fun itemized_singleItemTwoPeople() {
        val data = mkItemized(listOf(Triple("Pizza", 100.0, listOf("u1", "u2"))))
        val result = Calculations.calculateSplits(100.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isEqualTo(50.0)
        assertThat(result["u2"]?.amount).isEqualTo(50.0)
    }

    @Test
    fun itemized_assignedToSinglePerson() {
        val data = mkItemized(listOf(Triple("Beer", 60.0, listOf("u1"))))
        val result = Calculations.calculateSplits(60.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isEqualTo(60.0)
        assertThat(result["u2"]?.amount).isEqualTo(0.0)
    }

    @Test
    fun itemized_multipleItems() {
        val data = mkItemized(listOf(
            Triple("Pizza", 300.0, listOf("u1", "u2", "u3")),
            Triple("Beer", 100.0, listOf("u1")),
            Triple("Salad", 150.0, listOf("u2", "u3"))
        ))
        val result = Calculations.calculateSplits(550.0, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(200.0)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(175.0)
        assertThat(result["u3"]?.amount).isWithin(0.01).of(175.0)
    }

    @Test
    fun itemized_proportionalTax() {
        val data = mkItemized(
            listOf(Triple("Burger", 200.0, listOf("u1")), Triple("Salad", 100.0, listOf("u2"))),
            tax = 30.0
        )
        val result = Calculations.calculateSplits(330.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(220.0)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(110.0)
    }

    @Test
    fun itemized_equalTax() {
        val data = mkItemized(
            listOf(Triple("Burger", 200.0, listOf("u1")), Triple("Salad", 100.0, listOf("u2"))),
            tax = 30.0, taxMode = "equal"
        )
        val result = Calculations.calculateSplits(330.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(215.0)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(115.0)
    }

    @Test
    fun itemized_proportionalTip() {
        val data = mkItemized(
            listOf(Triple("Burger", 200.0, listOf("u1")), Triple("Salad", 100.0, listOf("u2"))),
            tip = 15.0
        )
        val result = Calculations.calculateSplits(315.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(210.0)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(105.0)
    }

    @Test
    fun itemized_equalTip() {
        val data = mkItemized(
            listOf(Triple("Burger", 200.0, listOf("u1")), Triple("Salad", 100.0, listOf("u2"))),
            tip = 15.0, tipMode = "equal"
        )
        val result = Calculations.calculateSplits(315.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(207.5)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(107.5)
    }

    @Test
    fun itemized_taxAndTipTogether() {
        val data = mkItemized(
            listOf(Triple("Pizza", 400.0, listOf("u1", "u2")), Triple("Beer", 100.0, listOf("u3"))),
            tax = 50.0, tip = 30.0
        )
        val result = Calculations.calculateSplits(580.0, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(232.0)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(232.0)
        assertThat(result["u3"]?.amount).isWithin(0.01).of(116.0)
    }

    @Test
    fun itemized_skipsUnassignedItems() {
        val data = mkItemized(listOf(
            Triple("Pizza", 100.0, listOf("u1")),
            Triple("Unassigned", 50.0, emptyList())
        ))
        val result = Calculations.calculateSplits(100.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isEqualTo(100.0)
        assertThat(result["u2"]?.amount).isEqualTo(0.0)
    }

    @Test
    fun itemized_sumEqualsGrandTotal() {
        val data = mkItemized(
            listOf(Triple("A", 120.0, listOf("u1", "u2")), Triple("B", 80.0, listOf("u3"))),
            tax = 20.0, tip = 10.0
        )
        val result = Calculations.calculateSplits(230.0, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        val total = result.values.sumOf { it.amount }
        assertThat(total).isWithin(0.01).of(230.0)
    }

    @Test
    fun itemized_allItemsAllMembers_equalsEqualSplit() {
        val data = mkItemized(listOf(Triple("Dinner", 300.0, listOf("u1", "u2", "u3"))))
        val result = Calculations.calculateSplits(300.0, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        assertThat(result["u1"]?.amount).isEqualTo(100.0)
        assertThat(result["u2"]?.amount).isEqualTo(100.0)
        assertThat(result["u3"]?.amount).isEqualTo(100.0)
    }

    @Test
    fun itemized_zeroItemsTotalWithTax_noCrash() {
        val data = mkItemized(listOf(Triple("A", 100.0, emptyList())), tax = 30.0)
        val result = Calculations.calculateSplits(0.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isEqualTo(0.0)
        assertThat(result["u2"]?.amount).isEqualTo(0.0)
    }

    @Test
    fun itemized_ignoresAssignedToUidNotInMemberUids() {
        val data = mkItemized(listOf(Triple("A", 100.0, listOf("u1", "uX"))))
        val result = Calculations.calculateSplits(100.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isEqualTo(50.0)
        assertThat(result["u2"]?.amount).isEqualTo(50.0)
    }

    @Test
    fun itemized_memberWithNoItemsGetsZero() {
        val data = mkItemized(listOf(
            Triple("A", 100.0, listOf("u1")),
            Triple("B", 200.0, listOf("u2"))
        ))
        val result = Calculations.calculateSplits(300.0, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        assertThat(result["u3"]?.amount).isEqualTo(0.0)
    }

    @Test
    fun itemized_proportionalTax3MembersUnequal() {
        val data = mkItemized(listOf(
            Triple("A", 300.0, listOf("u1")),
            Triple("B", 100.0, listOf("u2")),
            Triple("C", 100.0, listOf("u3"))
        ), tax = 50.0)
        val result = Calculations.calculateSplits(550.0, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(330.0)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(110.0)
        assertThat(result["u3"]?.amount).isWithin(0.01).of(110.0)
    }

    @Test
    fun itemized_equalTaxSkipsMembersWithNoItems() {
        val data = mkItemized(listOf(
            Triple("A", 200.0, listOf("u1")),
            Triple("B", 100.0, listOf("u2"))
        ), tax = 30.0, taxMode = "equal")
        val result = Calculations.calculateSplits(330.0, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(215.0)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(115.0)
        assertThat(result["u3"]?.amount).isWithin(0.01).of(0.0)
    }

    @Test
    fun itemized_proportionalTipBasedOnItemsPlusTax() {
        val data = mkItemized(listOf(
            Triple("A", 200.0, listOf("u1")),
            Triple("B", 100.0, listOf("u2"))
        ), tax = 30.0, tip = 15.0)
        val result = Calculations.calculateSplits(345.0, SplitType.ITEMIZED, listOf("u1", "u2"), itemizedData = data)
        assertThat(result["u1"]?.amount).isWithin(0.01).of(230.0)
        assertThat(result["u2"]?.amount).isWithin(0.01).of(115.0)
    }

    @Test
    fun itemized_sumEqualsGrandTotalWithTaxAndTip() {
        val data = mkItemized(listOf(
            Triple("A", 120.0, listOf("u1", "u2")),
            Triple("B", 80.0, listOf("u3")),
            Triple("C", 50.0, listOf("u1"))
        ), tax = 20.0, tip = 10.0)
        val grandTotal = 250.0 + 20.0 + 10.0
        val result = Calculations.calculateSplits(grandTotal, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        val sum = result.values.sumOf { it.amount }
        assertThat(sum).isWithin(0.01).of(grandTotal)
    }

    @Test
    fun itemized_manyItemsWithVaryingAssignments() {
        val items = (0 until 10).map { i ->
            Triple("Item$i", (i + 1) * 10.0, if (i % 2 == 0) listOf("u1", "u2") else listOf("u2", "u3"))
        }
        val data = mkItemized(items, tax = 25.0, tip = 15.0)
        val itemsTotal = items.sumOf { it.second }
        val grandTotal = itemsTotal + 25.0 + 15.0
        val result = Calculations.calculateSplits(grandTotal, SplitType.ITEMIZED, listOf("u1", "u2", "u3"), itemizedData = data)
        val sum = result.values.sumOf { it.amount }
        assertThat(sum).isWithin(0.01).of(grandTotal)
    }
}
