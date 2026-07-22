package com.charles.crowdtransit.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.TransitBus
import com.charles.crowdtransit.app.ui.theme.TransitFerry
import com.charles.crowdtransit.app.ui.theme.TransitSubway
import com.charles.crowdtransit.app.ui.theme.TransitTrain
import com.charles.crowdtransit.app.ui.theme.TransitTram
import com.charles.crowdtransit.model.Stop
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.sin

private const val OSM_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val STOPS_SOURCE = "stops-source"
private const val STOPS_LAYER = "stops-layer"
private const val USER_SOURCE = "user-source"
private const val USER_LAYER = "user-layer"
private const val ACTIVE_SOURCE = "active-halo-source"
private const val ACTIVE_HALO_LAYER = "active-halo-layer"
private const val ITINERARY_SOURCE = "itinerary-source"
private const val ITINERARY_TRANSIT_LAYER = "itinerary-transit-layer"
private const val ITINERARY_WALK_LAYER = "itinerary-walk-layer"

/** A polyline to draw on the map (itinerary legs). Dashed = walking, solid = transit. */
data class MapPolyline(
    /** lng/lat pairs in travel order. */
    val points: List<Pair<Double, Double>>,
    val colorHex: String,
    val dashed: Boolean,
)

private fun hexFromColor(color: androidx.compose.ui.graphics.Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun modeColorHex(transitTypes: List<String>): String = when (transitTypes.firstOrNull()) {
    "bus" -> hexFromColor(TransitBus)
    "train" -> hexFromColor(TransitTrain)
    "subway" -> hexFromColor(TransitSubway)
    "ferry" -> hexFromColor(TransitFerry)
    "tram" -> hexFromColor(TransitTram)
    else -> hexFromColor(Primary)
}

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    stops: List<Stop> = emptyList(),
    userLat: Double? = null,
    userLng: Double? = null,
    activeStopIds: Set<String> = emptySet(),
    polylines: List<MapPolyline> = emptyList(),
    fitToPolylines: Boolean = false,
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
                        PropertyFactory.circleRadius(11f),
                        PropertyFactory.circleStrokeWidth(2.5f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        // Vivid mode-color palette, data-driven from each feature's "color" property.
                        PropertyFactory.circleColor(Expression.get("color")),
                    )
                }
            )
        }

        if (style.getSource(ACTIVE_SOURCE) == null) {
            style.addSource(GeoJsonSource(ACTIVE_SOURCE))
            // Pulsing halo behind markers with recent (last 90 min) check-ins/reports.
            style.addLayerBelow(
                CircleLayer(ACTIVE_HALO_LAYER, ACTIVE_SOURCE).apply {
                    setProperties(
                        PropertyFactory.circleRadius(18f),
                        PropertyFactory.circleColor(hexFromColor(Primary)),
                        PropertyFactory.circleOpacity(0.35f),
                    )
                },
                STOPS_LAYER,
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

        val features = stops.map { stop ->
            Feature.fromGeometry(
                Point.fromLngLat(stop.lng, stop.lat),
                com.google.gson.JsonObject().apply {
                    addProperty("stopId", stop.stopId)
                    addProperty("color", modeColorHex(stop.transitTypes))
                },
            )
        }
        val fc = FeatureCollection.fromFeatures(features)
        val source = style.getSourceAs<GeoJsonSource>(STOPS_SOURCE)
        source?.setGeoJson(fc)

        val userSource = style.getSourceAs<GeoJsonSource>(USER_SOURCE)
        if (userLat != null && userLng != null) {
            val userFeature = Feature.fromGeometry(Point.fromLngLat(userLng, userLat))
            userSource?.setGeoJson(userFeature)
            if (!hasCenteredOnUser) {
                hasCenteredOnUser = true
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLng), 13.5),
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

    // Itinerary polylines: solid per-route-color transit lines + dashed walk lines,
    // inserted below the stops layer so markers stay tappable/visible.
    LaunchedEffect(styleReady, polylines, fitToPolylines) {
        if (!styleReady) return@LaunchedEffect
        val map = mapboxMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect

        if (style.getSource(ITINERARY_SOURCE) == null) {
            style.addSource(GeoJsonSource(ITINERARY_SOURCE))
            style.addLayerBelow(
                LineLayer(ITINERARY_TRANSIT_LAYER, ITINERARY_SOURCE).apply {
                    setProperties(
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineColor(Expression.get("color")),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round"),
                    )
                    setFilter(Expression.eq(Expression.get("dashed"), Expression.literal(false)))
                },
                STOPS_LAYER,
            )
            style.addLayerBelow(
                LineLayer(ITINERARY_WALK_LAYER, ITINERARY_SOURCE).apply {
                    setProperties(
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineColor(Expression.get("color")),
                        PropertyFactory.lineDasharray(arrayOf(0.8f, 1.6f)),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round"),
                    )
                    setFilter(Expression.eq(Expression.get("dashed"), Expression.literal(true)))
                },
                STOPS_LAYER,
            )
        }

        val lineFeatures = polylines
            .filter { it.points.size >= 2 }
            .map { line ->
                Feature.fromGeometry(
                    LineString.fromLngLats(line.points.map { (lng, lat) -> Point.fromLngLat(lng, lat) }),
                    com.google.gson.JsonObject().apply {
                        addProperty("color", line.colorHex)
                        addProperty("dashed", line.dashed)
                    },
                )
            }
        style.getSourceAs<GeoJsonSource>(ITINERARY_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))

        if (fitToPolylines && polylines.any { it.points.size >= 2 }) {
            val builder = LatLngBounds.Builder()
            polylines.forEach { line -> line.points.forEach { (lng, lat) -> builder.include(LatLng(lat, lng)) } }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80))
        }
    }

    // Refresh which stops show a halo whenever the active set or stop list changes.
    LaunchedEffect(styleReady, stops, activeStopIds) {
        if (!styleReady) return@LaunchedEffect
        val style = mapboxMap?.style ?: return@LaunchedEffect
        val activeSource = style.getSourceAs<GeoJsonSource>(ACTIVE_SOURCE) ?: return@LaunchedEffect
        val activeFeatures = stops.filter { activeStopIds.contains(it.stopId) }
            .map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }
        activeSource.setGeoJson(FeatureCollection.fromFeatures(activeFeatures))
    }

    // Gentle pulse animation on the halo layer (radius/opacity breathing).
    LaunchedEffect(styleReady, activeStopIds) {
        if (!styleReady || activeStopIds.isEmpty()) return@LaunchedEffect
        val style = mapboxMap?.style ?: return@LaunchedEffect
        val haloLayer = style.getLayerAs<CircleLayer>(ACTIVE_HALO_LAYER) ?: return@LaunchedEffect
        var t = 0.0
        while (true) {
            val pulse = (sin(t) + 1f) / 2f // 0..1
            haloLayer.setProperties(
                PropertyFactory.circleRadius(16f + pulse.toFloat() * 10f),
                PropertyFactory.circleOpacity(0.15f + pulse.toFloat() * 0.3f),
            )
            t += 0.35
            delay(120)
        }
    }
}
