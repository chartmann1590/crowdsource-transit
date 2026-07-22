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
import com.charles.crowdtransit.app.data.trip.TripSessionHolder
import com.charles.crowdtransit.app.ui.components.MapLibreView
import com.charles.crowdtransit.app.ui.components.MapPolyline
import com.charles.crowdtransit.app.ui.components.TransitBadge
import com.charles.crowdtransit.app.util.PolylineCodec
import com.charles.crowdtransit.model.Leg
import com.charles.crowdtransit.model.TripPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ItineraryDetailViewModel @Inject constructor(
    session: TripSessionHolder,
) : ViewModel() {
    val plan = session.selectedPlan
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryDetailScreen(
    onBack: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$dep → $arr · $duration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MapLibreView(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                polylines = polylines,
                fitToPolylines = true,
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
