package com.charles.crowdtransit.app.ui.screens.stop

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.data.repository.DirectionsRepository
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
    viewModel: StopDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stop = uiState.stop
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val directionsRepo = remember { DirectionsRepository() }
    var fullscreenPhotoIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffectClearBurst(uiState.pointsBurst, viewModel::clearPointsBurst)

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
