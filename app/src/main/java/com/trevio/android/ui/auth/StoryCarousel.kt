package com.trevio.android.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.core.designsystem.theme.*
import kotlinx.coroutines.delay

data class StoryChapterData(
    val title: String,
    val description: String,
    val mockup: @Composable () -> Unit
)

@Composable
fun StoryCarousel(
    chapters: List<StoryChapterData>,
    modifier: Modifier = Modifier,
    intervalMs: Long = 5000
) {
    var index by remember { mutableIntStateOf(0) }
    val isDark = isSystemInDarkTheme()

    // Guard against empty chapters — would cause ArithmeticException
    if (chapters.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(340.dp))
        return
    }

    LaunchedEffect(chapters.size) {
        if (chapters.size <= 1) return@LaunchedEffect
        while (true) {
            delay(intervalMs)
            index = (index + 1) % chapters.size
        }
    }

    val titleColor = if (isDark) Color.White else Color(0xFF0F172A)
    val descColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF475569)
    val progressBg = if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFFE2E8F0)

    Column(modifier = modifier) {
        // Story viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TrevioPrimary.copy(alpha = 0.3f),
                            TrevioPrimaryDark.copy(alpha = 0.5f)
                        )
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Animated chapter content
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    slideInHorizontally { it / 2 } + fadeIn() togetherWith
                    slideOutHorizontally { -it / 2 } + fadeOut()
                },
                label = "chapter"
            ) { chapterIndex ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(modifier = Modifier.padding(bottom = 16.dp, start = 16.dp, end = 16.dp)) {
                        chapters[chapterIndex].mockup()
                    }
                }
            }
        }

        // Chapter text
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "text"
        ) { chapterIndex ->
            Column(modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp)) {
                Text(
                    chapters[chapterIndex].title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    chapters[chapterIndex].description,
                    fontSize = 13.sp,
                    color = descColor,
                    lineHeight = 18.sp
                )
            }
        }

        // Progress bars
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chapters.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(progressBg)
                ) {
                    if (i <= index) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(50))
                                .background(TrevioPrimaryLight)
                        )
                    }
                }
            }
        }
    }
}
