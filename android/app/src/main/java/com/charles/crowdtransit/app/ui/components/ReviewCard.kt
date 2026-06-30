package com.charles.crowdtransit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.theme.OnPrimary
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.SurfaceCard
import com.charles.crowdtransit.model.Comment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReviewCard(
    comment: Comment,
    onHelpfulClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateStr = remember(comment.createdAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(comment.createdAt))
    }

    Column(
        modifier = modifier
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (comment.isAnonymous) "?" else comment.avatarInitials,
                    color = OnPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column {
                Text(
                    text = if (comment.isAnonymous) "Anonymous Rider" else comment.displayName,
                    style = if (comment.isAnonymous) MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                    else MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                )
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = OnSurfaceSecondary)
            }
            Spacer(Modifier.weight(1f))
            if (comment.rating > 0) {
                StarRating(rating = comment.rating.toFloat(), starSize = 14.dp)
            }
        }

        Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = OnSurface)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (comment.transitType.isNotEmpty()) {
                TransitBadge(type = comment.transitType)
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onHelpfulClick) {
                Icon(Icons.Outlined.ThumbUp, "Helpful", tint = OnSurfaceSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Helpful (${comment.helpfulCount})",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceSecondary,
                )
            }
        }
    }
}
