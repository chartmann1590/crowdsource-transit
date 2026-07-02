package com.charles.crowdtransit.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.theme.RatingEmpty
import com.charles.crowdtransit.app.ui.theme.RatingGold

@Composable
fun StarRating(
    rating: Float,
    maxStars: Int = 5,
    starSize: Dp = 20.dp,
    interactive: Boolean = false,
    onRatingChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var poppedStar by remember { mutableIntStateOf(-1) }

    Row(modifier = modifier) {
        for (i in 1..maxStars) {
            val filled = i <= rating
            val scale by animateFloatAsState(
                targetValue = if (poppedStar == i) 1.3f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "starPop",
            )
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = " star",
                tint = if (filled) RatingGold else RatingEmpty,
                modifier = Modifier
                    .size(starSize)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .then(
                        if (interactive && onRatingChange != null) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                poppedStar = i
                                onRatingChange(i)
                            }
                        } else Modifier
                    ),
            )
        }
    }
}
