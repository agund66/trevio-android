package com.trevio.android.core.designsystem.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import com.trevio.android.core.designsystem.theme.BalanceNegative
import com.trevio.android.core.designsystem.theme.BalanceNeutral
import com.trevio.android.core.designsystem.theme.BalancePositive
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.core.designsystem.theme.TrevioBorderDark
import com.trevio.android.util.AppConstants
import com.trevio.android.util.CurrencyConverter

@Composable
fun BalanceChip(
    balance: Double,
    currency: String = AppConstants.BASE_CURRENCY,
    modifier: Modifier = Modifier
) {
    val color = when {
        balance > 0.01 -> if (isSystemInDarkTheme()) Color(0xFF4ADE80) else BalancePositive
        balance < -0.01 -> if (isSystemInDarkTheme()) Color(0xFFF87171) else BalanceNegative
        else -> BalanceNeutral
    }
    val text = when {
        balance > 0.01 -> stringResource(R.string.balance_youll_get, CurrencyConverter.formatCurrency(balance, currency))
        balance < -0.01 -> stringResource(R.string.balance_youll_pay, CurrencyConverter.formatCurrency(-balance, currency))
        else -> stringResource(R.string.balance_settled_up)
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MemberAvatar(
    name: String,
    photoURL: String = "",
    size: Int = 40,
    modifier: Modifier = Modifier
) {
    if (photoURL.isNotEmpty()) {
        coil.compose.AsyncImage(
            model = photoURL,
            contentDescription = name,
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size * 0.4).sp
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * A shimmering placeholder box used in skeleton loading states.
 * Provides better perceived performance than a spinner — the user
 * sees the approximate layout before data arrives.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp
) {
    // Pulse alpha animation for a subtle shimmer effect
    val transition = androidx.compose.animation.core.rememberInfiniteTransition()
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val baseColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(baseColor.copy(alpha = pulseAlpha))
    )
}

/**
 * Skeleton placeholder for a list item with an avatar, title, and subtitle.
 */
@Composable
fun ListItemSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            modifier = Modifier.size(40.dp),
            cornerRadius = 20.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth(0.6f).height(16.dp)
            )
            Spacer(Modifier.height(8.dp))
            ShimmerBox(
                modifier = Modifier.fillMaxWidth(0.4f).height(12.dp)
            )
        }
    }
}

/**
 * Skeleton placeholder for a card with a title and rows.
 */
@Composable
fun CardSkeleton(modifier: Modifier = Modifier, rowCount: Int = 3) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp)
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(20.dp))
        Spacer(Modifier.height(16.dp))
        repeat(rowCount) {
            ListItemSkeleton()
            if (it < rowCount - 1) Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun TrevioHeader(
    title: String,
    gradient: Brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        if (content != null) {
            content()
        }
    }
}

@Composable
fun TrevioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (isSystemInDarkTheme()) TrevioBorderDark else TrevioBorder
    val baseModifier = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = baseModifier,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(content = content)
        }
    } else {
        Card(
            modifier = baseModifier,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(content = content)
        }
    }
}

