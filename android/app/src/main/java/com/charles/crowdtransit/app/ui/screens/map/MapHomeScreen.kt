package com.charles.crowdtransit.app.ui.screens.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ui.components.MapLibreView
import com.charles.crowdtransit.app.ui.components.SearchBar
import com.charles.crowdtransit.app.ui.components.StopCard
import com.charles.crowdtransit.app.ui.theme.Error
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated
import kotlinx.coroutines.flow.catch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHomeScreen(
    onStopClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAddStopClick: () -> Unit,
    viewModel: MapHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberBottomSheetScaffoldState()
    val context = LocalContext.current
    val isExpanded by remember {
        derivedStateOf { sheetState.bottomSheetState.currentValue == SheetValue.Expanded }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            locationFlow(context)
                .catch { }
                .collect { location ->
                    viewModel.onLocationUpdate(location.lat, location.lng)
                }
        }
    }

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 120.dp,
        sheetContainerColor = SurfaceElevated,
        sheetContent = {
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .padding(12.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    "Nearby Stops",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = Primary)
                } else if (uiState.error != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = "Couldn't load stops: ${uiState.error}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Error,
                        )
                    }
                } else if (uiState.nearbyStops.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = OnSurfaceSecondary)
                        Text(
                            text = if (uiState.userLat == null) "Waiting for location..." else "No transit stops found within 5km",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceSecondary,
                        )
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.nearbyStops, key = { it.stopId }) { stop ->
                            StopCard(
                                stop = stop,
                                distanceMeters = uiState.distances[stop.stopId],
                                useImperial = uiState.useImperialUnits,
                                compact = !isExpanded,
                                onClick = { onStopClick(stop.stopId) },
                                modifier = Modifier.width(if (isExpanded) 240.dp else 170.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                stops = uiState.nearbyStops,
                userLat = uiState.userLat,
                userLng = uiState.userLng,
                onStopPinClick = onStopClick,
                onLocationUpdate = viewModel::onLocationUpdate,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SearchBar(
                    onClickSearch = onSearchClick,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(SurfaceElevated, CircleShape),
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "Profile")
                }
            }

            FloatingActionButton(
                onClick = onAddStopClick,
                containerColor = Primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 120.dp)
                    .size(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Stop")
            }
        }
    }
}
