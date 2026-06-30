package com.charles.crowdtransit.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
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
    Row(modifier = modifier) {
        for (i in 1..maxStars) {
            val filled = i <= rating
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = " star",
                tint = if (filled) RatingGold else OnSurfaceSecondary.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(starSize)
                    .then(
                        if (interactive && onRatingChange != null) {
                            Modifier.clickable { onRatingChange(i) }
                        } else Modifier
                    ),
            )
        }
    }
}
