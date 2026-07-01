package com.charles.crowdtransit.app.ui.screens.stop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.data.repository.DirectionsRepository
import com.charles.crowdtransit.app.ui.components.ReviewCard
import com.charles.crowdtransit.app.ui.components.StarRating
import com.charles.crowdtransit.app.ui.components.TransitBadge
import com.charles.crowdtransit.app.ui.theme.OnPrimary
import com.charles.crowdtransit.app.ui.theme.OnSecondary
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.RatingGold
import com.charles.crowdtransit.app.ui.theme.Secondary
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceDark
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    stopId: String,
    onBack: () -> Unit,
    onRouteClick: (String) -> Unit,
    onRateClick: () -> Unit,
    viewModel: StopDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stop = uiState.stop
    val context = LocalContext.current
    val directionsRepo = remember { DirectionsRepository() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stop?.name ?: "Stop", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRateClick,
                icon = { Icon(Icons.Default.Star, "Rate") },
                text = { Text("Write a Review") },
                containerColor = Secondary,
                contentColor = OnSecondary,
            )
        },
        containerColor = SurfaceDark,
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        if (stop == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Stop not found", color = OnSurfaceSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stop.name, style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                    val location = listOf(stop.city, stop.state).filter { it.isNotBlank() }.joinToString(", ")
                    if (location.isNotBlank()) {
                        Text(
                            location,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceSecondary,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stop.transitTypes.isEmpty()) {
                            TransitBadge(type = "transit", label = "Transit")
                        } else {
                            stop.transitTypes.forEach { type ->
                                TransitBadge(type = type, label = type.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "%.1f".format(stop.averageRating),
                            style = MaterialTheme.typography.headlineSmall,
                            color = RatingGold,
                        )
                        StarRating(rating = stop.averageRating)
                        Text(
                            "(${stop.ratingCount} reviews)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceSecondary,
                        )
                    }

                    if (stop.desc.isNotEmpty()) {
                        Text(
                            text = stop.desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceSecondary,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                directionsRepo.openDirectionsToStop(context, stop.lat, stop.lng, stop.name)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            Icon(Icons.Default.Directions, "Directions", tint = OnPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Get Directions", color = OnPrimary)
                        }
                        Button(
                            onClick = onRateClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                        ) {
                            Icon(Icons.Default.Edit, "Edit", tint = OnSurface)
                            Spacer(Modifier.width(8.dp))
                            Text("Update Review", color = OnSurface)
                        }
                    }

                    if (stop.crowdsourced) {
                        Text(
                            "Community-added stop",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceSecondary,
                        )
                    }

                    Divider(color = SurfaceElevated)
                }
            }

            item {
                Text(
                    "Community Reviews",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            items(uiState.comments, key = { it.commentId }) { comment ->
                ReviewCard(
                    comment = comment,
                    onHelpfulClick = { viewModel.markHelpful(comment.commentId) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            if (uiState.comments.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No reviews yet. Be the first!", color = OnSurfaceSecondary)
                    }
                }
            }
        }
    }
}
