package com.charles.crowdtransit.app.ui.assistant

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.theme.Accent
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.PrimaryDark
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hopper — CrowdTransit's friendly on-device transit-shuttle character, drawn entirely in
 * Compose Canvas (no bitmap/vector assets, no new dependency). Faux-3D through layered
 * gradients, a key light, a rim light, and a contact shadow rather than true perspective —
 * see docs/superpowers/specs (Hopper mascot decision) for why. Palette is drawn strictly
 * from ui/theme/Color.kt so it reads as native to "Sunny Transit".
 *
 * Designed to hold up from a 40dp FAB to a 160dp chat header: the silhouette (rounded body
 * + wraparound windscreen face) is what carries recognizability at small sizes; the
 * gradient/light detail is what carries charm at large sizes.
 */
@Composable
fun HopperMascot(
    state: MascotState,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val infinite = rememberInfiniteTransition(label = "hopper")

    // Idle breathing bob — always running so the character never looks frozen, even when
    // another animation (talk/think) is layered on top.
    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breathe",
    )

    // Thinking wobble: a slow side-to-side rock.
    val thinkWobble by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "thinkWobble",
    )

    // Talking bob: faster, smaller bounce so streaming replies feel lively.
    val talkBob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse),
        label = "talkBob",
    )

    // Alert pulse: a gentle accent-tinted scale pop.
    val alertPulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "alertPulse",
    )

    // Blink: an occasional quick close, independent of the other loops.
    var blink by remember { mutableFloatStateOf(0f) }
    val blinkAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = blink,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "blink",
    )
    LaunchedEffect(state) {
        if (state == MascotState.Sleeping) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2200, 4800))
            blink = 1f
            delay(90)
            blink = 0f
        }
    }

    // Listening lean: eases toward a forward tilt while Listening, and back otherwise.
    val leanTarget = if (state == MascotState.Listening) 1f else 0f
    val lean by androidx.compose.animation.core.animateFloatAsState(
        targetValue = leanTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "lean",
    )

    val bobPx = when (state) {
        MascotState.Idle, MascotState.Listening -> sin(breathe * Math.PI).toFloat() * 0.03f
        MascotState.Talking -> sin(talkBob * Math.PI).toFloat() * 0.05f
        MascotState.Thinking -> sin(breathe * Math.PI).toFloat() * 0.02f
        MascotState.Alert -> sin(breathe * Math.PI).toFloat() * 0.02f
        MascotState.Sleeping -> 0f
    }
    val wobbleDeg = if (state == MascotState.Thinking) thinkWobble * 4f else 0f
    val scalePulse = if (state == MascotState.Alert) 1f + alertPulse * 0.06f else 1f
    val eyeCloseAmount = when {
        state == MascotState.Sleeping -> 1f
        else -> blinkAnim
    }

    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            drawHopper(
                bob = bobPx,
                wobbleDeg = wobbleDeg,
                scalePulse = scalePulse,
                lean = lean,
                eyeClose = eyeCloseAmount,
                talking = state == MascotState.Talking,
                thinking = state == MascotState.Thinking,
                sleeping = state == MascotState.Sleeping,
                alert = state == MascotState.Alert,
                talkPhase = talkBob,
            )
        }
    }
}

private fun DrawScope.drawHopper(
    bob: Float,
    wobbleDeg: Float,
    scalePulse: Float,
    lean: Float,
    eyeClose: Float,
    talking: Boolean,
    thinking: Boolean,
    sleeping: Boolean,
    alert: Boolean,
    talkPhase: Float,
) {
    val w = this.size.width
    val h = this.size.height
    val cx = w / 2f
    val cy = h / 2f

    rotate(degrees = wobbleDeg, pivot = Offset(cx, cy)) {
        translate(left = 0f, top = bob * h + lean * h * 0.02f) {
            scale(scale = scalePulse, pivot = Offset(cx, cy)) {
                // --- Contact shadow: grounds the character; shrinks slightly as it "lifts" on bob.
                val shadowWidth = w * 0.62f * (1f - kotlin.math.abs(bob) * 1.4f).coerceIn(0.8f, 1f)
                drawOval(
                    color = OnSurface.copy(alpha = 0.14f),
                    topLeft = Offset(cx - shadowWidth / 2f, h * 0.86f),
                    size = Size(shadowWidth, h * 0.07f),
                )

                val bodyWidth = w * 0.74f
                val bodyHeight = h * 0.62f
                val bodyTop = h * 0.20f
                val bodyLeft = cx - bodyWidth / 2f
                val bodyRect = RoundRect(
                    left = bodyLeft,
                    top = bodyTop,
                    right = bodyLeft + bodyWidth,
                    bottom = bodyTop + bodyHeight,
                    cornerRadius = CornerRadius(bodyWidth * 0.34f, bodyWidth * 0.34f),
                )

                // --- Body: vertical gradient for volume + a top-left radial key light.
                val bodyPath = androidx.compose.ui.graphics.Path().apply { addRoundRect(bodyRect) }
                drawPath(
                    path = bodyPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Primary, PrimaryDark),
                        startY = bodyTop,
                        endY = bodyTop + bodyHeight,
                    ),
                )
                drawPath(
                    path = bodyPath,
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(bodyLeft + bodyWidth * 0.32f, bodyTop + bodyHeight * 0.22f),
                        radius = bodyWidth * 0.55f,
                    ),
                )
                // Rim light, bottom-right, in the Accent tone — keeps Hopper feeling warm/sunny.
                drawPath(
                    path = bodyPath,
                    brush = Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(bodyLeft + bodyWidth * 0.88f, bodyTop + bodyHeight * 0.86f),
                        radius = bodyWidth * 0.5f,
                    ),
                )

                // --- Wheels (small, grounded, read as "shuttle" even at FAB size).
                val wheelY = bodyTop + bodyHeight * 0.98f
                val wheelR = bodyWidth * 0.09f
                listOf(bodyLeft + bodyWidth * 0.22f, bodyLeft + bodyWidth * 0.78f).forEach { wx ->
                    drawCircle(color = OnSurface.copy(alpha = 0.85f), radius = wheelR, center = Offset(wx, wheelY))
                    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = wheelR * 0.35f, center = Offset(wx, wheelY))
                }

                // --- Windscreen / face: a big glossy rounded panel.
                val faceWidth = bodyWidth * 0.78f
                val faceHeight = bodyHeight * 0.56f
                val faceLeft = cx - faceWidth / 2f
                val faceTop = bodyTop + bodyHeight * 0.12f
                val faceRect = RoundRect(
                    left = faceLeft,
                    top = faceTop,
                    right = faceLeft + faceWidth,
                    bottom = faceTop + faceHeight,
                    cornerRadius = CornerRadius(faceWidth * 0.32f, faceWidth * 0.32f),
                )
                val facePath = androidx.compose.ui.graphics.Path().apply { addRoundRect(faceRect) }
                drawPath(path = facePath, color = Color(0xFFEFFBF5))
                drawPath(
                    path = facePath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        start = Offset(faceLeft, faceTop),
                        end = Offset(faceLeft + faceWidth * 0.6f, faceTop + faceHeight * 0.6f),
                    ),
                )

                // --- Eyes: two rounded dots with catchlights, tracking the lean slightly.
                val eyeY = faceTop + faceHeight * 0.46f
                val eyeDx = faceWidth * 0.20f
                val eyeR = faceWidth * (if (sleeping) 0.07f else 0.085f)
                val eyeOffsetX = lean * faceWidth * 0.03f
                listOf(cx - eyeDx + eyeOffsetX, cx + eyeDx + eyeOffsetX).forEach { ex ->
                    val openHeight = (eyeR * 2f) * (1f - eyeClose).coerceIn(0.08f, 1f)
                    drawOval(
                        color = OnSurface,
                        topLeft = Offset(ex - eyeR, eyeY - openHeight / 2f),
                        size = Size(eyeR * 2f, openHeight),
                    )
                    if (eyeClose < 0.7f) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = eyeR * 0.28f,
                            center = Offset(ex - eyeR * 0.25f, eyeY - openHeight * 0.22f),
                        )
                    }
                }

                if (sleeping) {
                    // Sleepy "Z" drift above the head.
                    val zPath = androidx.compose.ui.graphics.Path().apply {
                        val zx = cx + faceWidth * 0.30f
                        val zy = faceTop - faceHeight * 0.22f
                        val s = faceWidth * 0.08f
                        moveTo(zx - s, zy - s)
                        lineTo(zx + s, zy - s)
                        lineTo(zx - s, zy + s)
                        lineTo(zx + s, zy + s)
                    }
                    drawPath(path = zPath, color = OnSurface.copy(alpha = 0.45f), style = Stroke(width = faceWidth * 0.025f))
                }

                // --- Mouth: expressive path — small arc idle, open oval while talking,
                // flat dashes while thinking, soft smile+brow lift while alert.
                val mouthY = faceTop + faceHeight * 0.72f
                val mouthWidth = faceWidth * 0.30f
                when {
                    talking -> {
                        val openAmount = (0.3f + 0.7f * kotlin.math.abs(sin(talkPhase * Math.PI)).toFloat())
                        drawOval(
                            color = PrimaryDark,
                            topLeft = Offset(cx - mouthWidth * 0.28f, mouthY - mouthWidth * 0.22f * openAmount),
                            size = Size(mouthWidth * 0.56f, mouthWidth * 0.44f * openAmount),
                        )
                    }

                    thinking -> {
                        val dotR = mouthWidth * 0.06f
                        listOf(-1, 0, 1).forEach { i ->
                            drawCircle(
                                color = OnSurface.copy(alpha = 0.7f),
                                radius = dotR,
                                center = Offset(cx + i * dotR * 3.2f, mouthY),
                            )
                        }
                    }

                    else -> {
                        val smileUp = if (alert) mouthWidth * 0.22f else mouthWidth * 0.14f
                        val mouthPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(cx - mouthWidth / 2f, mouthY)
                            quadraticTo(cx, mouthY + smileUp, cx + mouthWidth / 2f, mouthY)
                        }
                        drawPath(
                            path = mouthPath,
                            color = OnSurface.copy(alpha = 0.75f),
                            style = Stroke(width = faceWidth * 0.028f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                    }
                }
            }
        }
    }
}
