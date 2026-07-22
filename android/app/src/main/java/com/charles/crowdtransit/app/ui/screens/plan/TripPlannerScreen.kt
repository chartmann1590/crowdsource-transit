package com.charles.crowdtransit.app.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ui.components.TransitBadge
import com.charles.crowdtransit.app.ui.screens.map.hasLocationPermission
import com.charles.crowdtransit.app.ui.screens.map.locationFlow
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.model.Leg
import com.charles.crowdtransit.model.TripPlan
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

internal fun formatLocalTime(iso: String?): String =
    iso?.let { runCatching { timeFormatter.format(Instant.parse(it)) }.getOrNull() } ?: "--:--"

internal fun planTimes(plan: TripPlan): Triple<String, String, String> {
    val dep = plan.legs.firstOrNull()?.let { it.dep ?: it.board?.depUtc }
    val arr = plan.legs.lastOrNull()?.let { it.arr ?: it.alight?.arrUtc }
    val duration = runCatching {
        Duration.between(Instant.parse(dep!!), Instant.parse(arr!!))
    }.getOrNull()
    val durationText = duration?.let {
        val h = it.toHours()
        val m = it.toMinutes() % 60
        if (h > 0) "${h}h ${m}m" else "${m} min"
    } ?: ""
    return Triple(formatLocalTime(dep), formatLocalTime(arr), durationText)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlannerScreen(
    onBack: () -> Unit,
    onOpenItinerary: () -> Unit,
    viewModel: TripPlannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            locationFlow(context).collect { loc ->
                viewModel.onLocationUpdate(loc.lat, loc.lng)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan a trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EndpointRow(
                label = "From",
                place = uiState.origin,
                onClick = { viewModel.startEditing(true) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                EndpointRow(
                    label = "To",
                    place = uiState.destination,
                    onClick = { viewModel.startEditing(false) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::swapEndpoints) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Swap origin and destination")
                }
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(if (uiState.editingOrigin) "Search starting stop or place" else "Search destination stop or place")
                },
                singleLine = true,
            )

            androidx.compose.material3.Button(
                onClick = viewModel::plan,
                enabled = uiState.origin != null && uiState.destination != null && !uiState.planning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.planning) "Planning…" else "Find routes")
            }

            when {
                uiState.planning -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.error != null -> Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                uiState.planned && uiState.plans.isEmpty() -> Text(
                    "No transit routes found for this trip. Try different endpoints or a shorter distance.",
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.suggestions.isNotEmpty() || uiState.searching) {
                    item {
                        SuggestionMyLocation(
                            enabled = uiState.userLat != null,
                            onClick = viewModel::useMyLocation,
                        )
                    }
                    items(uiState.suggestions) { suggestion ->
                        SuggestionRow(suggestion) { viewModel.selectSuggestion(suggestion) }
                    }
                } else {
                    items(uiState.plans) { plan ->
                        PlanCard(plan) {
                            viewModel.openPlan(plan)
                            onOpenItinerary()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(
    label: String,
    place: PlannerPlace?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (place?.isMyLocation == true) Icons.Default.MyLocation else Icons.Default.Place,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("$label:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            place?.name ?: "Choose…",
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SuggestionMyLocation(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Use my location", fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SuggestionRow(suggestion: PlaceSuggestion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(suggestion.name, fontWeight = FontWeight.Medium)
        if (suggestion.detail.isNotBlank()) {
            Text(suggestion.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanCard(plan: TripPlan, onClick: () -> Unit) {
    val (dep, arr, duration) = planTimes(plan)
    val transitLegs = plan.legs.filter { it.isTransit }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$dep → $arr", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text(duration, fontWeight = FontWeight.Medium, color = Primary)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                transitLegs.forEachIndexed { i, leg: Leg ->
                    if (i > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TransitBadge(
                        type = leg.mode ?: "transit",
                        label = leg.route?.short?.ifBlank { leg.route.long } ?: "",
                    )
                }
                Spacer(Modifier.weight(1f))
                val transfers = (transitLegs.size - 1).coerceAtLeast(0)
                Text(
                    if (transfers == 0) "Direct" else "$transfers transfer${if (transfers > 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
