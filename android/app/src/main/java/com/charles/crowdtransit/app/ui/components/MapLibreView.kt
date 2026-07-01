package com.charles.crowdtransit.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.model.Stop
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val OSM_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val STOPS_SOURCE = "stops-source"
private const val STOPS_LAYER = "stops-layer"
private const val USER_SOURCE = "user-source"
private const val USER_LAYER = "user-layer"

private fun hexFromColor(color: androidx.compose.ui.graphics.Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    stops: List<Stop> = emptyList(),
    userLat: Double? = null,
    userLng: Double? = null,
    onStopPinClick: (String) -> Unit = {},
    onLocationUpdate: (Double, Double) -> Unit = { _, _ -> },
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapboxMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }

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
                        styleReady = true
                    }
                }
                mv.onResume()
            }
        },
    )

    LaunchedEffect(styleReady, stops, userLat, userLng) {
        if (!styleReady) return@LaunchedEffect
        val map = mapboxMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect

        if (style.getSource(STOPS_SOURCE) == null) {
            style.addSource(GeoJsonSource(STOPS_SOURCE))
            style.addLayer(
                CircleLayer(STOPS_LAYER, STOPS_SOURCE).apply {
                    setProperties(
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleStrokeWidth(2.5f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleColor(hexFromColor(Primary)),
                    )
                }
            )
        }

        if (style.getSource(USER_SOURCE) == null) {
            style.addSource(GeoJsonSource(USER_SOURCE))
            style.addLayer(
                CircleLayer(USER_LAYER, USER_SOURCE).apply {
                    setProperties(
                        PropertyFactory.circleRadius(10f),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleColor("#4285F4"),
                    )
                }
            )
        }

        val featuresArr = JSONArray()
        for (stop in stops) {
            featuresArr.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply {
                        put(stop.lng)
                        put(stop.lat)
                    })
                })
            })
        }
        val fc = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", featuresArr)
        }
        val source = style.getSourceAs<GeoJsonSource>(STOPS_SOURCE)
        source?.setGeoJson(fc.toString())

        val userSource = style.getSourceAs<GeoJsonSource>(USER_SOURCE)
        if (userLat != null && userLng != null) {
            val userGeoJson = JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply {
                        put(userLng)
                        put(userLat)
                    })
                })
            }
            userSource?.setGeoJson(userGeoJson.toString())
            if (!hasCenteredOnUser) {
                hasCenteredOnUser = true
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLng), 15.0),
                )
            }
        } else if (!hasCenteredOnUser && stops.isNotEmpty()) {
            hasCenteredOnUser = true
            val avgLat = stops.map { it.lat }.average()
            val avgLng = stops.map { it.lng }.average()
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(avgLat, avgLng), 10.0),
            )
        }
    }
}
