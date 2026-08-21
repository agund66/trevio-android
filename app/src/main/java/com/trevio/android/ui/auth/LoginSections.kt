package com.trevio.android.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import com.trevio.android.core.designsystem.theme.*

// ─── Use Cases Section ───────────────────────────────────────────

private data class UseCaseItem(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun UseCasesSection() {
    val isDark = isSystemInDarkTheme()
    val useCases = remember {
        listOf(
            UseCaseItem(R.string.usecase_trips_title, R.string.usecase_trips_desc, Icons.Default.Flight, TrevioPrimary),
            UseCaseItem(R.string.usecase_household_title, R.string.usecase_household_desc, Icons.Default.Home, Color(0xFF6366F1)),
            UseCaseItem(R.string.usecase_turf_title, R.string.usecase_turf_desc, Icons.Default.EmojiEvents, Color(0xFFF59E0B)),
            UseCaseItem(R.string.usecase_roommates_title, R.string.usecase_roommates_desc, Icons.Default.Group, Color(0xFFEC4899)),
            UseCaseItem(R.string.usecase_events_title, R.string.usecase_events_desc, Icons.Default.Celebration, Color(0xFF8B5CF6)),
            UseCaseItem(R.string.usecase_daily_title, R.string.usecase_daily_desc, Icons.Default.LocalCafe, Color(0xFF06B6D4))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.usecases_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color(0xFF0F172A),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.usecases_subtitle),
            fontSize = 14.sp,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(24.dp))

        useCases.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    UseCaseCard(item, isDark, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun UseCaseCard(item: UseCaseItem, isDark: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (isDark) Color(0xFF1E293B).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0).copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(item.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(item.titleRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(item.descRes),
                fontSize = 11.sp,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
                lineHeight = 15.sp
            )
        }
    }
}

// ─── Feature Highlights Banner ───────────────────────────────────

@Composable
fun StatsBanner() {
    val highlights = listOf(
        Triple(R.string.feature_split_methods_title, Icons.Default.CallSplit, TrevioPrimary),
        Triple(R.string.feature_settlement_title, Icons.Default.Smartphone, Color(0xFF6366F1)),
        Triple(R.string.feature_budgets_title, Icons.Default.AccountBalanceWallet, Color(0xFFF59E0B)),
        Triple(R.string.feature_recurring_title, Icons.Default.Repeat, Color(0xFFEC4899))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(TrevioPrimary, TrevioPrimaryDark)))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            highlights.forEach { (labelRes, icon, _) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(labelRes),
                        fontSize = 9.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(70.dp)
                    )
                }
            }
        }
    }
}

// ─── How It Works ────────────────────────────────────────────────

private data class HowItWorksStep(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun HowItWorksSection() {
    val isDark = isSystemInDarkTheme()
    val steps = listOf(
        HowItWorksStep(R.string.howitworks_step1_title, R.string.howitworks_step1_desc, Icons.Default.Login, TrevioPrimary),
        HowItWorksStep(R.string.howitworks_step2_title, R.string.howitworks_step2_desc, Icons.Default.Group, Color(0xFF6366F1)),
        HowItWorksStep(R.string.howitworks_step3_title, R.string.howitworks_step3_desc, Icons.Default.CheckCircle, Color(0xFF10B981))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.howitworks_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color(0xFF0F172A),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.howitworks_subtitle),
            fontSize = 14.sp,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(24.dp))

        steps.forEachIndexed { i, step ->
            HowItWorksStepRow(step, isDark)
            if (i < steps.size - 1) {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun HowItWorksStepRow(step: HowItWorksStep, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(step.color, step.color.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(step.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(step.titleRes), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A))
            Spacer(Modifier.height(2.dp))
            Text(stringResource(step.descRes), fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B), lineHeight = 16.sp)
        }
    }
}

// ─── CTA Section ─────────────────────────────────────────────────

@Composable
fun CTASection(onSignIn: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF1E293B).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.cta_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.cta_subtitle),
                fontSize = 13.sp,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrevioPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.cta_button), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
