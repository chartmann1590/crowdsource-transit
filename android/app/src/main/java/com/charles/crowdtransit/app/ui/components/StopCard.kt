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

@Composable
fun StopCard(
    stop: Stop,
    distanceMeters: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceCard)
            .clickable { onClick() },
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(6.dp)
                .background(accentColorFor(stop.transitTypes)),
        )
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        maxLines = 2,
                    )
                    if (stop.city.isNotBlank()) {
                        Text(
                            text = listOf(stop.city, stop.state).filter { it.isNotBlank() }.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceSecondary,
                        )
                    }
                }
                if (distanceMeters != null) {
                    val distText = if (distanceMeters < 1000) {
                        "${distanceMeters.toInt()}m"
                    } else {
                        String.format("%.1fkm", distanceMeters / 1000f)
                    }
                    Text(
                        text = distText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(stop.transitTypes) { type ->
                    TransitBadge(type = type, label = type.replaceFirstChar { it.uppercase() })
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
