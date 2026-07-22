package com.charles.crowdtransit.app.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.navigation.NavigationSessionRepository
import com.charles.crowdtransit.app.data.repository.SavedTripRepository
import com.charles.crowdtransit.app.data.trip.ItineraryTextFormatter
import com.charles.crowdtransit.app.data.trip.TripSessionHolder
import com.charles.crowdtransit.app.service.NavigationService
import com.charles.crowdtransit.app.ui.components.MapLibreView
import com.charles.crowdtransit.app.ui.components.MapPolyline
import com.charles.crowdtransit.app.ui.components.WalkStepMarker
import com.charles.crowdtransit.app.ui.components.TransitBadge
import com.charles.crowdtransit.app.util.PolylineCodec
import com.charles.crowdtransit.model.Leg
import com.charles.crowdtransit.model.TripPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ItineraryDetailViewModel @Inject constructor(
    session: TripSessionHolder,
    private val savedTrips: SavedTripRepository,
    private val formatter: ItineraryTextFormatter,
    private val navSession: NavigationSessionRepository,
) : ViewModel() {
    val plan = session.selectedPlan

    fun startNavigationSession(plan: TripPlan) {
        navSession.start(plan)
    }

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus

    val canSave: Boolean get() = savedTrips.isSignedIn

    fun shareText(plan: TripPlan): String = formatter.toText(plan, formatter.shareUrl(plan))

    fun shareUrl(plan: TripPlan): String = formatter.shareUrl(plan)

    fun save(plan: TripPlan) {
        viewModelScope.launch {
            _saveStatus.value = try {
                savedTrips.saveTrip(plan)
                "Trip saved!"
            } catch (e: Exception) {
                e.message ?: "Couldn't save the trip."
            }
            delay(2500)
            _saveStatus.value = null
        }
    }
}

private const val WALK_COLOR = "#5B6472"

/** Map polylines for a plan: solid route-colored transit shapes + dashed walk lines. */
internal fun planPolylines(plan: TripPlan): List<MapPolyline> = plan.legs.mapNotNull { leg ->
    if (leg.isTransit) {
        val points = leg.shapePoly?.let { poly ->
            PolylineCodec.decode(poly).map { it.lng to it.lat }
        } ?: leg.stops?.map { it.lng to it.lat }.orEmpty()
        points.takeIf { it.size >= 2 }?.let {
            MapPolyline(points = it, colorHex = leg.route?.color?.ifBlank { "#00A862" } ?: "#00A862", dashed = false)
        }
    } else {
        val points = leg.poly?.let { poly ->
            PolylineCodec.decode(poly).map { it.lng to it.lat }
        } ?: listOfNotNull(
            leg.from?.let { it.lng to it.lat },
            leg.to?.let { it.lng to it.lat },
        )
        points.takeIf { it.size >= 2 }?.let {
            MapPolyline(points = it, colorHex = WALK_COLOR, dashed = true)
        }
    }
}

/** Turn-by-turn maneuver markers for every walking leg in the plan. */
internal fun planWalkStepMarkers(plan: TripPlan): List<WalkStepMarker> =
    plan.legs.filter { it.isWalk }.flatMap { it.steps.orEmpty() }
        .map { WalkStepMarker(lat = it.lat, lng = it.lng) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryDetailScreen(
    onBack: () -> Unit,
    onStartNavigation: () -> Unit = {},
    viewModel: ItineraryDetailViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val currentPlan = plan
    if (currentPlan == null) {
        // Session lost (process death) — nothing to show.
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Itinerary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }) { padding ->
            Text("This itinerary is no longer available.", Modifier.padding(padding).padding(16.dp))
        }
        return
    }

    val (dep, arr, duration) = planTimes(currentPlan)
    val polylines = remember(currentPlan) { planPolylines(currentPlan) }
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$dep → $arr · $duration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.canSave) {
                        IconButton(onClick = { viewModel.save(currentPlan) }) {
                            Icon(Icons.Default.StarBorder, contentDescription = "Save trip")
                        }
                    }
                    IconButton(onClick = {
                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, viewModel.shareText(currentPlan))
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(sendIntent, "Share trip"),
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share trip")
                    }
                },
            )
        },
    ) { padding ->
        var showDisclosure by remember { mutableStateOf(false) }
        val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { _ ->
            // Navigation works without the notification; start either way.
            viewModel.startNavigationSession(currentPlan)
            NavigationService.start(context)
            onStartNavigation()
        }

        if (showDisclosure) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDisclosure = false },
                title = { Text("Location during navigation") },
                text = {
                    Text(
                        "CrowdTransit uses your precise location while navigating — including " +
                            "with the app in the background via an ongoing notification — to " +
                            "follow your trip, tell you when to get off, and warn you if you go " +
                            "off route. Location tracking stops when navigation ends.",
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showDisclosure = false
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.startNavigationSession(currentPlan)
                            NavigationService.start(context)
                            onStartNavigation()
                        }
                    }) { Text("Start") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDisclosure = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.material3.Button(
                onClick = { showDisclosure = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Start navigation")
            }
            saveStatus?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            MapLibreView(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                polylines = polylines,
                fitToPolylines = true,
                walkStepMarkers = remember(currentPlan) { planWalkStepMarkers(currentPlan) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(currentPlan.legs) { leg ->
                    if (leg.isWalk) WalkLegCard(leg) else TransitLegCard(leg)
                }
            }
        }
    }
}

@Composable
private fun WalkLegCard(leg: Leg) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = "Walk",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Walk to ${leg.to?.name.orEmpty().ifBlank { "destination" }}",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatLocalTime(leg.dep)}–${formatLocalTime(leg.arr)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${leg.distM ?: 0} m",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            leg.steps?.forEach { step ->
                Text("• ${step.text} (${step.distM} m)", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TransitLegCard(leg: Leg) {
    var stopsExpanded by remember { mutableStateOf(false) }
    val stops = leg.stops.orEmpty()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransitBadge(
                    type = leg.mode ?: "transit",
                    label = leg.route?.short?.ifBlank { leg.route.long } ?: "",
                )
                Text(
                    "→ ${leg.trip?.headsign.orEmpty()}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
            Text(
                "Board at ${leg.board?.name} — departs ${formatLocalTime(leg.board?.depUtc)}",
                fontSize = 13.sp,
            )
            if (stops.size > 2) {
                TextButton(onClick = { stopsExpanded = !stopsExpanded }) {
                    Text(
                        if (stopsExpanded) "Hide stops" else "Ride ${stops.size - 1} stops — show all",
                        fontSize = 13.sp,
                    )
                }
                if (stopsExpanded) {
                    stops.drop(1).dropLast(1).forEach { stop ->
                        Text(
                            "· ${stop.name}  ${formatLocalTime(stop.arrUtc)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Get off at ${leg.alight?.name} — arrives ${formatLocalTime(leg.alight?.arrUtc)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${leg.route?.agency.orEmpty()}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
