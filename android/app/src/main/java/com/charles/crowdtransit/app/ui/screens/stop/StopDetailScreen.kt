package com.charles.crowdtransit.app.ui.screens.stop

import androidx.compose.runtime.LaunchedEffect
import com.charles.crowdtransit.app.data.ads.interstitialAdManager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.ui.window.Dialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ui.components.PointsBurst
import com.charles.crowdtransit.app.ui.components.ReviewCard
import com.charles.crowdtransit.app.ui.components.StarRating
import com.charles.crowdtransit.app.ui.components.TransitBadge
import com.charles.crowdtransit.app.ui.theme.AppBackground
import com.charles.crowdtransit.app.ui.theme.OnPrimary
import com.charles.crowdtransit.app.ui.theme.OnSecondary
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.PrimaryLight
import com.charles.crowdtransit.app.ui.theme.RatingGold
import com.charles.crowdtransit.app.ui.theme.Secondary
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated
import com.charles.crowdtransit.model.ActivityType
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private fun relativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60}h ago"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    stopId: String,
    onBack: () -> Unit,
    onRouteClick: (String) -> Unit,
    onRateClick: () -> Unit,
    onDirectionsClick: (name: String, lat: Double, lng: Double) -> Unit = { _, _, _ -> },
    viewModel: StopDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stop = uiState.stop
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var fullscreenPhotoIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffectClearBurst(uiState.pointsBurst, viewModel::clearPointsBurst)

    LaunchedEffect(stopId) {
        (context as? android.app.Activity)?.let { activity ->
            context.interstitialAdManager().maybeShow(activity)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            bitmap?.let { viewModel.uploadPhoto(it) }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.uploadPhoto(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            containerColor = AppBackground,
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
                        if (uiState.agencies.isNotEmpty()) {
                            Text(
                                text = "Service Provider: " + uiState.agencies.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
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
                                    onDirectionsClick(stop.name, stop.lat, stop.lng)
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
                    LiveActivitySection(
                        activityCount = uiState.activity.size,
                        latestEvent = uiState.activity.firstOrNull(),
                        checkedIn = uiState.hasCheckedInRecently,
                        onCheckIn = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.checkIn()
                        },
                        onQuickReport = { type ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.submitQuickReport(type)
                        },
                    )
                }

                if (shouldShowRoutesSection(stop, uiState)) {
                    item {
                        if (stop.crowdsourced && uiState.resolvedStopName != null) {
                            Text(
                                text = "Schedules linked from nearby stop: ${uiState.resolvedStopName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        RoutesServingStopSection(
                            routes = uiState.servedRoutes,
                            isLoading = uiState.isLoadingSchedule,
                            rateLimited = uiState.scheduleRateLimited,
                            onRouteClick = onRouteClick,
                        )
                        Divider(color = SurfaceElevated, modifier = Modifier.padding(top = 12.dp))
                    }
                }

                if (shouldShowDeparturesSection(stop, uiState)) {
                    item {
                        if (stop.crowdsourced && uiState.resolvedStopName != null) {
                            Text(
                                text = "Schedules linked from nearby stop: ${uiState.resolvedStopName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        UpcomingDeparturesSection(
                            departures = uiState.upcoming,
                            isLoading = uiState.isLoadingSchedule,
                            rateLimited = uiState.scheduleRateLimited,
                            onRouteClick = onRouteClick,
                        )
                        Divider(color = SurfaceElevated, modifier = Modifier.padding(top = 12.dp))
                    }
                }

                if (stop.crowdsourced) {
                    if (uiState.isLoadingSchedule) {
                        item {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                                Text("Finding nearby public transit stop…", style = MaterialTheme.typography.bodySmall, color = OnSurfaceSecondary)
                            }
                        }
                    } else if (uiState.resolvedStopId == null) {
                        item {
                            Text(
                                "Schedule unavailable: no nearby public transit stop found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                item {
                    PhotoStripSection(
                        photos = uiState.photos,
                        onPhotoClick = { index -> fullscreenPhotoIndex = index },
                        onCameraClick = { cameraLauncher.launch(null) },
                        onGalleryClick = { galleryLauncher.launch("image/*") },
                    )
                    Divider(color = SurfaceElevated, modifier = Modifier.padding(top = 12.dp))
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

        PointsBurst(
            points = uiState.pointsBurst ?: 0,
            visible = uiState.pointsBurst != null,
            modifier = Modifier.fillMaxSize(),
        )
    }

    val photoIndex = fullscreenPhotoIndex
    if (photoIndex != null && uiState.photos.isNotEmpty()) {
        FullscreenPhotoViewer(
            photos = uiState.photos,
            startIndex = photoIndex.coerceIn(0, uiState.photos.lastIndex),
            onDismiss = { fullscreenPhotoIndex = null },
        )
    }
}

@Composable
private fun LaunchedEffectClearBurst(pointsBurst: Int?, onClear: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(pointsBurst) {
        if (pointsBurst != null) {
            delay(1000)
            onClear()
        }
    }
}

@Composable
private fun LiveActivitySection(
    activityCount: Int,
    latestEvent: com.charles.crowdtransit.model.ActivityEvent?,
    checkedIn: Boolean,
    onCheckIn: () -> Unit,
    onQuickReport: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Live Activity", style = MaterialTheme.typography.titleMedium, color = OnSurface)

        val summary = if (activityCount == 0) {
            "No activity in the last 90 minutes"
        } else {
            val who = if (activityCount == 1) "1 person here" else "$activityCount people active here"
            val latest = latestEvent?.let {
                " · reported ${ActivityType.label(it.type)} ${relativeTime(it.timestamp)}"
            } ?: ""
            who + latest
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.PersonPin, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceSecondary)
        }

        Button(
            onClick = onCheckIn,
            enabled = !checkedIn,
            colors = ButtonDefaults.buttonColors(containerColor = if (checkedIn) SurfaceElevated else Primary),
        ) {
            Text(if (checkedIn) "You're checked in" else "I'm here", color = if (checkedIn) OnSurfaceSecondary else OnPrimary)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ActivityType.quickReportTypes) { type ->
                AssistChip(
                    onClick = { onQuickReport(type) },
                    label = { Text(ActivityType.label(type)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = PrimaryLight,
                        labelColor = OnSecondary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PhotoStripSection(
    photos: List<com.charles.crowdtransit.model.StopPhoto>,
    onPhotoClick: (Int) -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Photos", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onCameraClick) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Take photo", tint = Primary)
                }
                IconButton(onClick = onGalleryClick) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Choose from gallery", tint = Primary)
                }
            }
        }

        if (photos.isEmpty()) {
            Text("No photos yet. Be the first to add one!", style = MaterialTheme.typography.bodySmall, color = OnSurfaceSecondary)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos.size) { index ->
                    val photo = photos[index]
                    val bitmap = remember(photo.photoId) { decodeBase64(photo.data) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Stop photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPhotoClick(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenPhotoViewer(
    photos: List<com.charles.crowdtransit.model.StopPhoto>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableStateOf(startIndex) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
        ) {
            val photo = photos.getOrNull(index)
            val bitmap = remember(photo?.photoId) { photo?.let { decodeBase64(it.data) } }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Stop photo fullscreen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                            if (index < photos.lastIndex) index++ else index = 0
                        },
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

private fun decodeBase64(data: String): Bitmap? = try {
    val bytes = Base64.decode(data, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (_: Exception) {
    null
}

private fun hasOnestopId(stop: com.charles.crowdtransit.model.Stop): Boolean {
    val sid = stop.stopId
    return !stop.crowdsourced &&
        (sid.startsWith("r-") || sid.startsWith("s-") || sid.startsWith("d-") || sid.startsWith("f-"))
}

private fun shouldShowRoutesSection(
    stop: com.charles.crowdtransit.model.Stop,
    uiState: StopDetailUiState,
): Boolean {
    return uiState.servedRoutes.isNotEmpty() || uiState.isLoadingSchedule || uiState.scheduleRateLimited
}

private fun shouldShowDeparturesSection(
    stop: com.charles.crowdtransit.model.Stop,
    uiState: StopDetailUiState,
): Boolean {
    val hasId = hasOnestopId(stop) || uiState.resolvedStopId != null
    return hasId && (uiState.upcoming.isNotEmpty() || uiState.isLoadingSchedule || uiState.scheduleRateLimited)
}

@Composable
private fun RoutesServingStopSection(
    routes: List<com.charles.crowdtransit.model.ServedRoute>,
    isLoading: Boolean,
    rateLimited: Boolean,
    onRouteClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Routes serving this stop", style = MaterialTheme.typography.titleMedium, color = OnSurface)

        when {
            isLoading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                Text("Loading routes…", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceSecondary)
            }
            rateLimited -> Text(
                "Schedule info temporarily unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSecondary,
            )
            routes.isEmpty() -> Text(
                "No route data found for this stop.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSecondary,
            )
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(routes, key = { it.onestopId }) { route ->
                    RoutePill(route = route, onClick = { onRouteClick(route.onestopId) })
                }
            }
        }
    }
}

@Composable
private fun RoutePill(
    route: com.charles.crowdtransit.model.ServedRoute,
    onClick: () -> Unit,
) {
    val bgColor = remember(route.color) { parseHexColorOrNull(route.color) ?: Primary }
    val textColor = remember(route.textColor) { parseHexColorOrNull(route.textColor) ?: androidx.compose.ui.graphics.Color.White }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            route.shortName.ifBlank { route.transitType.replaceFirstChar { it.uppercase() } },
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        if (route.longName.isNotBlank() || route.agencyName.isNotBlank()) {
            Text(
                route.longName.ifBlank { route.agencyName },
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        route.nextDepartureTime?.let { next ->
            Text(
                "Next ${next.take(5)}",
                color = textColor.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun UpcomingDeparturesSection(
    departures: List<com.charles.crowdtransit.model.ServedDeparture>,
    isLoading: Boolean,
    rateLimited: Boolean,
    onRouteClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Upcoming departures", style = MaterialTheme.typography.titleMedium, color = OnSurface)

        when {
            isLoading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                Text("Loading departures…", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceSecondary)
            }
            rateLimited -> Text(
                "Schedule info temporarily unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSecondary,
            )
            departures.isEmpty() -> Text(
                "No scheduled departures in the near future.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSecondary,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                departures.forEachIndexed { index, dep ->
                    DepartureRow(
                        departure = dep,
                        onClick = { onRouteClick(dep.routeOnestopId) },
                    )
                    if (index < departures.lastIndex) {
                        Divider(color = SurfaceElevated, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(
    departure: com.charles.crowdtransit.model.ServedDeparture,
    onClick: () -> Unit,
) {
    val badgeColor = remember(departure.routeColor) { parseHexColorOrNull(departure.routeColor) ?: Primary }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                departure.routeShortName.ifBlank { departure.transitType.replaceFirstChar { it.uppercase() } },
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            departure.departureTime.take(5),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp),
        )
        Text(
            departure.headsign.ifBlank { departure.routeLongName.ifBlank { "—" } },
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceSecondary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (departure.isRealtime) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFDE3730))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    "LIVE",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun parseHexColorOrNull(hex: String): androidx.compose.ui.graphics.Color? {
    if (hex.isBlank()) return null
    val normalized = if (hex.startsWith("#")) hex else "#$hex"
    return try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(normalized))
    } catch (_: Exception) {
        null
    }
}
