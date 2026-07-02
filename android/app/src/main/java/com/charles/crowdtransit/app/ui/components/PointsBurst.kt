package com.charles.crowdtransit.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.theme.Accent
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.TransitTrain
import com.charles.crowdtransit.app.ui.theme.TransitTram
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    val angle: Float,
    val distance: Float,
    val color: Color,
    val size: Float,
)

/**
 * Lightweight Compose-canvas confetti burst shown briefly when the user earns points, plus a
 * "+N" label and a haptic tick. No new dependency — pure Canvas particle animation.
 */
@Composable
fun PointsBurst(
    points: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val particles = remember(visible) {
        if (!visible) emptyList() else List(18) {
            Particle(
                angle = Random.nextFloat() * 360f,
                distance = 60f + Random.nextFloat() * 60f,
                color = listOf(Primary, Accent, TransitTrain, TransitTram).random(),
                size = 4f + Random.nextFloat() * 5f,
            )
        }
    }

    LaunchedEffect(visible) {
        if (visible) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val transition = rememberInfiniteTransition(label = "burst")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "burstProgress",
        )

        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                particles.forEach { p ->
                    val rad = Math.toRadians(p.angle.toDouble())
                    val dist = p.distance * progress
                    val alpha = (1f - progress).coerceIn(0f, 1f)
                    val offset = Offset(
                        center.x + (cos(rad) * dist).toFloat(),
                        center.y + (sin(rad) * dist).toFloat(),
                    )
                    drawCircle(color = p.color.copy(alpha = alpha), radius = p.size, center = offset)
                }
            }
            Text(
                text = "+$points",
                style = MaterialTheme.typography.headlineMedium,
                color = Primary,
            )
        }
    }
}
