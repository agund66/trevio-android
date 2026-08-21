package com.trevio.android.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.trevio.android.R
import com.trevio.android.core.designsystem.theme.*
import kotlinx.coroutines.delay

private data class SplitMethod(val label: String, val detail: String)

private data class CategoryBar(val label: String, val pct: Float, val color: Color)

@Composable
fun SplitReceiptMockup() {
    MockupCard {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TrevioPrimaryLight.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    tint = TrevioPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.mockup_sample_dinner_title), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Text(stringResource(R.string.mockup_sample_dinner_group, 4), fontSize = 9.sp, color = Color(0xFF64748B))
            }
        }
        Spacer(Modifier.height(12.dp))
        // Amount
        Text(stringResource(R.string.mockup_sample_amount_3200), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Text(stringResource(R.string.mockup_total_bill), fontSize = 9.sp, color = Color(0xFF64748B))
        Spacer(Modifier.height(12.dp))
        // Split chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(TrevioPrimary.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(stringResource(R.string.mockup_split_equally) + " · " + stringResource(R.string.mockup_sample_per_person), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TrevioPrimary)
        }
        Spacer(Modifier.height(12.dp))
        // Avatars
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                stringResource(R.string.mockup_sample_avatar_a),
                stringResource(R.string.mockup_sample_avatar_r),
                stringResource(R.string.mockup_sample_avatar_s),
                stringResource(R.string.mockup_sample_avatar_m)
            ).forEach { initial ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(TrevioPrimaryLight, TrevioPrimary))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initial, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SplitMethodsMockup() {
    var index by remember { mutableIntStateOf(0) }
    val methodEqualLabel = stringResource(R.string.mockup_sample_method_equal)
    val methodEqualDetail = stringResource(R.string.mockup_sample_method_equal_detail)
    val methodExactLabel = stringResource(R.string.mockup_sample_method_exact)
    val methodExactDetail = stringResource(R.string.mockup_sample_method_exact_detail)
    val methodPercentLabel = stringResource(R.string.mockup_sample_method_percent)
    val methodPercentDetail = stringResource(R.string.mockup_sample_method_percent_detail)
    val methodSharesLabel = stringResource(R.string.mockup_sample_method_shares)
    val methodSharesDetail = stringResource(R.string.mockup_sample_method_shares_detail)
    val methods = remember(methodEqualLabel, methodEqualDetail, methodExactLabel, methodExactDetail, methodPercentLabel, methodPercentDetail, methodSharesLabel, methodSharesDetail) {
        listOf(
            SplitMethod(methodEqualLabel, methodEqualDetail),
            SplitMethod(methodExactLabel, methodExactDetail),
            SplitMethod(methodPercentLabel, methodPercentDetail),
            SplitMethod(methodSharesLabel, methodSharesDetail)
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            index = (index + 1) % methods.size
        }
    }

    MockupCard {
        Text(stringResource(R.string.mockup_split_method), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
        Text(stringResource(R.string.mockup_split_method_hint), fontSize = 9.sp, color = Color(0xFF64748B))
        Spacer(Modifier.height(12.dp))
        // Method chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            methods.forEachIndexed { i, method ->
                val isSelected = i == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) TrevioPrimary.copy(alpha = 0.15f)
                            else Color(0xFFF1F5F9)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        method.label,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) TrevioPrimary else Color(0xFF94A3B8)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Animated detail using AnimatedContent
        Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    slideInHorizontally { it / 2 } + fadeIn() togetherWith
                    slideOutHorizontally { -it / 2 } + fadeOut()
                },
                label = "method-detail"
            ) { idx ->
                val method = methods[idx]
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(TrevioPrimary.copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(method.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TrevioPrimary)
                        Text(method.detail, fontSize = 9.sp, color = TrevioPrimary.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
fun SettlementMockup() {
    val nameAarav = stringResource(R.string.mockup_sample_name_aarav)
    val nameRiya = stringResource(R.string.mockup_sample_name_riya)
    val nameSahil = stringResource(R.string.mockup_sample_name_sahil)
    val amount450 = stringResource(R.string.mockup_sample_amount_450)
    val amount200 = stringResource(R.string.mockup_sample_amount_200)
    val balances = remember(nameAarav, nameRiya, nameSahil, amount450, amount200) {
        listOf(
            Triple(nameAarav, nameRiya, amount450),
            Triple(nameSahil, nameAarav, amount200)
        )
    }

    MockupCard {
        Text(stringResource(R.string.mockup_simplified_balances), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
        Spacer(Modifier.height(12.dp))
        // Balance rows
        balances.forEachIndexed { i, (from, to, amount) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(from, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.padding(horizontal = 4.dp).size(12.dp))
                Text(to, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
                Spacer(Modifier.weight(1f))
                Text(amount, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TrevioPrimary)
            }
            if (i < balances.size - 1) {
                Spacer(Modifier.height(8.dp))
            }
        }
        // UPI chip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(TrevioPrimary, Color(0xFF0F766E))))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mockup_settle_via_upi), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun BudgetInsightsMockup() {
    val infiniteTransition = rememberInfiniteTransition(label = "budget")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    val catFood = stringResource(R.string.mockup_sample_category_food)
    val catTravel = stringResource(R.string.mockup_sample_category_travel)
    val catShopping = stringResource(R.string.mockup_sample_category_shopping)
    val categories = remember(catFood, catTravel, catShopping) {
        listOf(
            CategoryBar(catFood, 0.65f, Color(0xFFF59E0B)),
            CategoryBar(catTravel, 0.4f, Color(0xFF6366F1)),
            CategoryBar(catShopping, 0.25f, Color(0xFFEC4899))
        )
    }

    MockupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.mockup_monthly_budget), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Text(stringResource(R.string.mockup_sample_budget_amount), fontSize = 9.sp, color = Color(0xFF64748B))
            }
            // Streak badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF97316).copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.mockup_sample_streak_count), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
            }
        }
        Spacer(Modifier.height(12.dp))
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFE2E8F0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(TrevioPrimaryLight, TrevioPrimary)))
            )
        }
        Spacer(Modifier.height(12.dp))
        // Category bars
        categories.forEach { cat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cat.label, fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.width(56.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF1F5F9))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(cat.pct)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(cat.color)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // Insight chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(TrevioPrimary.copy(alpha = 0.08f))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = TrevioPrimary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.mockup_you_saved), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TrevioPrimary)
            }
        }
    }
}

@Composable
private fun MockupCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}
