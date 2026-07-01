package com.charles.crowdtransit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.SurfaceCard
import com.charles.crowdtransit.app.ui.theme.TransitBus
import com.charles.crowdtransit.app.ui.theme.TransitFerry
import com.charles.crowdtransit.app.ui.theme.TransitSubway
import com.charles.crowdtransit.app.ui.theme.TransitTrain
import com.charles.crowdtransit.app.ui.theme.TransitTram
import com.charles.crowdtransit.model.Stop

private fun accentColorFor(transitTypes: List<String>) = when (transitTypes.firstOrNull()) {
    "bus" -> TransitBus
    "train" -> TransitTrain
    "subway" -> TransitSubway
    "ferry" -> TransitFerry
    "tram" -> TransitTram
    else -> Primary
}

private fun formatDistance(meters: Float, useImperial: Boolean): String {
    if (useImperial) {
        val feet = meters * 3.28084f
        return if (feet < 5280f) {
            "${feet.toInt()} ft"
        } else {
            String.format("%.1f mi", feet / 5280f)
        }
    }
    return if (meters < 1000f) {
        "${meters.toInt()} m"
    } else {
        String.format("%.1f km", meters / 1000f)
    }
}

@Composable
fun StopCard(
    stop: Stop,
    distanceMeters: Float? = null,
    useImperial: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = if (compact) 10.dp else 16.dp
    val gap = if (compact) 4.dp else 8.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 16.dp else 24.dp))
            .background(SurfaceCard)
            .clickable { onClick() },
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (compact) 4.dp else 6.dp)
                .background(accentColorFor(stop.transitTypes)),
        )
        Column(
            modifier = Modifier.padding(pad),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.name,
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        maxLines = if (compact) 1 else 2,
                    )
                    if (!compact && stop.city.isNotBlank()) {
                        Text(
                            text = listOf(stop.city, stop.state).filter { it.isNotBlank() }.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceSecondary,
                        )
                    }
                }
                if (distanceMeters != null) {
                    Text(
                        text = formatDistance(distanceMeters, useImperial),
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                        color = Primary,
                    )
                }
            }

            if (!compact) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stop.transitTypes.isEmpty()) {
                        item { TransitBadge(type = "transit", label = "Transit") }
                    } else {
                        items(stop.transitTypes) { type ->
                            TransitBadge(type = type, label = type.replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StarRating(rating = stop.averageRating, starSize = 14.dp)
                    Text(
                        text = "(${stop.ratingCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceSecondary,
                    )
                }
            }
        }
    }
}
