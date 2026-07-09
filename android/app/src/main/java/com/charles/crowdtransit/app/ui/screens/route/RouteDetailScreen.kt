package com.charles.crowdtransit.app.ui.screens.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ui.components.StarRating
import com.charles.crowdtransit.app.ui.components.TransitBadge
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.SurfaceDark
import com.charles.crowdtransit.model.RouteStopSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeId: String,
    onBack: () -> Unit,
    onStopClick: (String) -> Unit = {},
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var resolvingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(routeId) {
        viewModel.loadRoute(routeId)
    }

    val title = uiState.routeWithStops?.longName ?: uiState.route?.longName ?: "Route Details"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = OnSurface,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(padding).fillMaxSize()
                )
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            uiState.routeWithStops != null -> {
                val route = uiState.routeWithStops!!
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TransitBadge(type = route.transitType, label = route.shortName.ifBlank { route.longName })
                            Text(
                                text = route.longName.ifBlank { route.shortName },
                                style = MaterialTheme.typography.headlineMedium,
                                color = OnSurface,
                            )
                            if (route.agencyName.isNotBlank()) {
                                Text(
                                    text = "Service Provider: ${route.agencyName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceSecondary,
                                )
                            }
                            Text(
                                text = "${route.stops.size} stops",
                                style = MaterialTheme.typography.labelLarge,
                                color = OnSurface,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    itemsIndexed(route.stops) { index, stop ->
                        RouteStopRow(
                            index = index,
                            stop = stop,
                            color = parseHexColorOrNull(route.color) ?: OnSurfaceSecondary,
                            isResolving = resolvingIndex == index,
                            onClick = {
                                resolvingIndex = index
                                coroutineScope.launch {
                                    val resolvedId = viewModel.resolveStopId(stop)
                                    resolvingIndex = null
                                    if (resolvedId != null) onStopClick(resolvedId)
                                }
                            },
                        )
                    }
                }
            }
            uiState.route != null -> {
                val route = uiState.route!!
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TransitBadge(type = route.type, label = route.shortName)

                    Text(
                        text = route.longName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = OnSurface,
                    )

                    StarRating(rating = route.averageRating, starSize = 24.dp)
                    Text(
                        text = "( reviews)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceSecondary,
                    )
                }
            }
        }
    }
}

private fun parseHexColorOrNull(hex: String): Color? {
    if (hex.isBlank()) return null
    val normalized = if (hex.startsWith("#")) hex else "#$hex"
    return try {
        Color(android.graphics.Color.parseColor(normalized))
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun RouteStopRow(
    index: Int,
    stop: RouteStopSummary,
    color: Color,
    isResolving: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(10.dp).background(color, CircleShape),
        )
        Text(
            text = stop.name.ifBlank { "Stop ${index + 1}" },
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurface,
            modifier = Modifier.weight(1f),
        )
        if (isResolving) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}
