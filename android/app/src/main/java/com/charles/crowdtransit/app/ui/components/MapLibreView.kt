package com.charles.crowdtransit.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.charles.crowdtransit.model.Stop
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.annotations.MarkerOptions
import androidx.compose.runtime.MutableState

private const val OSM_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    stops: List<Stop> = emptyList(),
    userLat: Double? = null,
    userLng: Double? = null,
    onStopPinClick: (String) -> Unit = {},
    onLocationUpdate: (Double, Double) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapboxMap by remember { mutableStateOf<MapLibreMap?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).also { mv ->
                mapView = mv
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapboxMap = map
                    map.setStyle(Style.Builder().fromUri(OSM_STYLE)) {
                        map.uiSettings.isAttributionEnabled = false
                        map.uiSettings.isLogoEnabled = false
                    }
                }
                mv.onResume()
            }
        },
        update = { mv ->
            val map = mapboxMap ?: return@AndroidView
            if (map.style == null) return@AndroidView

            map.clear()

            stops.forEach { stop ->
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(stop.lat, stop.lng))
                        .title(stop.name)
                        .snippet(stop.stopId)
                )
            }

            if (userLat != null && userLng != null) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLng), 12.0),
                )
            } else if (stops.isNotEmpty()) {
                val avgLat = stops.map { it.lat }.average()
                val avgLng = stops.map { it.lng }.average()
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(avgLat, avgLng), 10.0),
                )
            }
        }
    )
}
