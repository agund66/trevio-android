package com.trevio.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.core.designsystem.components.TrevioHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsViewScreen(
    navController: androidx.navigation.NavHostController
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TrevioHeader(
            title = stringResource(R.string.profile_terms_conditions),
            onBack = { navController.popBackStack() }
        )

        // ── Gradient intro banner ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.terms_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }

        // ── Scrollable terms cards ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isDark = isSystemInDarkTheme()
            TermsSectionCard(
                icon = Icons.Default.VerifiedUser,
                iconColor = if (isDark) TrevioSecondaryDarkTheme else TrevioSecondary,
                title = stringResource(R.string.terms_acceptance_title),
                body = stringResource(R.string.terms_acceptance_body)
            )
            TermsSectionCard(
                icon = Icons.Default.Lock,
                iconColor = if (isDark) BalancePositiveDark else BalancePositive,
                title = stringResource(R.string.terms_privacy_title),
                body = stringResource(R.string.terms_privacy_body)
            )
            TermsSectionCard(
                icon = Icons.Default.Payments,
                iconColor = if (isDark) TrevioWarningDarkTheme else TrevioWarning,
                title = stringResource(R.string.terms_financial_title),
                body = stringResource(R.string.terms_financial_body)
            )
            TermsSectionCard(
                icon = Icons.Default.Gavel,
                iconColor = if (isDark) CategoryShoppingDark else CategoryShopping,
                title = stringResource(R.string.terms_conduct_title),
                body = stringResource(R.string.terms_conduct_body)
            )
            TermsSectionCard(
                icon = Icons.Default.PersonOff,
                iconColor = if (isDark) BalanceNegativeDark else BalanceNegative,
                title = stringResource(R.string.terms_termination_title),
                body = stringResource(R.string.terms_termination_body)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TermsSectionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
