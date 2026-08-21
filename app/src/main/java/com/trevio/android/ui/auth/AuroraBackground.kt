package com.trevio.android.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.trevio.android.core.designsystem.theme.*

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Infinite transitions for blob movement
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")

    val x1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x1"
    )
    val y1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 21000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y1"
    )
    val x2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x2"
    )
    val y2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y2"
    )

    val baseColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val blob1Color = if (isDark) TrevioPrimaryDarkTheme.copy(alpha = 0.3f) else TrevioPrimary.copy(alpha = 0.35f)
    val blob2Color = if (isDark) TrevioSecondaryDarkTheme.copy(alpha = 0.2f) else TrevioSecondary.copy(alpha = 0.25f)
    val blob3Color = if (isDark) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF34D399).copy(alpha = 0.2f)

    // Blob sizes scale with screen — smaller on compact devices
    val blobSize1 = (screenWidth.value * 0.75f).dp
    val blobSize2 = (screenWidth.value * 0.65f).dp
    val blobSize3 = (screenWidth.value * 0.55f).dp
    val blurRadius = (screenWidth.value * 0.22f).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        // Blob 1 — top-left, teal
        Box(
            modifier = Modifier
                .offset(
                    x = (x1 * 80 - 40).dp,
                    y = (y1 * 60 - 30).dp
                )
                .size(blobSize1)
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(blob1Color, CircleShape)
        )

        // Blob 2 — bottom-right, indigo
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth - blobSize2 + (x2 * 60 - 30).dp,
                    y = screenHeight - blobSize2 + (y2 * 50 - 25).dp
                )
                .size(blobSize2)
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(blob2Color, CircleShape)
        )

        // Blob 3 — center, emerald
        Box(
            modifier = Modifier
                .offset(
                    x = (screenWidth - blobSize3) / 2 + (y1 * 50 - 25).dp,
                    y = (screenHeight - blobSize3) / 2 + (x2 * 40 - 20).dp
                )
                .size(blobSize3)
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(blob3Color, CircleShape)
        )
    }
}
