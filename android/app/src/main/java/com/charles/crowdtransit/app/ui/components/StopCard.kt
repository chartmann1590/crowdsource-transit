package com.charles.crowdtransit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.SurfaceCard
import com.charles.crowdtransit.model.Stop

@Composable
fun StopCard(
    stop: Stop,
    distanceMeters: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
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
                Text(
                    text = ", ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceSecondary,
                )
            }
            if (distanceMeters != null) {
                val distText = if (distanceMeters < 1000) {
                    "m"
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
                TransitBadge(type = type)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StarRating(rating = stop.averageRating, starSize = 14.dp)
            Text(
                text = "()",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceSecondary,
            )
        }
    }
}
