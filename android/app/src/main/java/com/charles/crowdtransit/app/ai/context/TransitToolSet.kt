package com.charles.crowdtransit.app.ai.context

import com.charles.crowdtransit.app.data.location.RiderLocationProvider
import com.charles.crowdtransit.app.data.navigation.NavigationSessionRepository
import com.charles.crowdtransit.app.data.preferences.UserPreferencesStore
import com.charles.crowdtransit.app.data.remote.OrsApi
import com.charles.crowdtransit.app.data.remote.OrsGeocodeRequest
import com.charles.crowdtransit.app.data.repository.StopRepository
import com.charles.crowdtransit.app.data.trip.ItineraryTextFormatter
import com.charles.crowdtransit.app.data.trip.TripSessionHolder
import com.charles.crowdtransit.app.domain.routing.PlanRequest
import com.charles.crowdtransit.app.domain.routing.PlanTripUseCase
import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hopper's tool surface — deliberately small (4 tools), since a 2B model's tool-calling
 * reliability degrades fast as the surface grows. Every method is synchronous: LiteRT-LM's
 * reflection-based dispatch (ReflectionTool) calls plain KFunctions, not suspend ones, so
 * runBlocking bridges to this app's existing suspend repositories — safe here because tool
 * execution already blocks the model's generation loop until the tool returns.
 *
 * This is one of exactly two files allowed to import com.google.ai.edge.litertlm.* — the
 * other is LiteRtAssistantEngine.kt. See AssistantEngineFactory for why that isolation
 * matters on a minSdk-26 app (the runtime itself requires API 31).
 */
@Singleton
class TransitToolSet @Inject constructor(
    private val planTripUseCase: PlanTripUseCase,
    private val stopRepository: StopRepository,
    private val navigationSession: NavigationSessionRepository,
    private val tripSession: TripSessionHolder,
    private val itineraryTextFormatter: ItineraryTextFormatter,
    private val userPreferences: UserPreferencesStore,
    private val orsApi: OrsApi,
    private val riderLocationProvider: RiderLocationProvider,
) : ToolSet {

    private data class ResolvedPlace(val name: String, val lat: Double, val lng: Double)

    /**
     * The rider's current fix for tool use: NavigationSessionRepository's live fix while
     * a trip is actively being navigated (freshest, GPS-driven), falling back to
     * [RiderLocationProvider]'s cached last-known location the rest of the time — which
     * is when planTrip/findNearbyStops are actually called in practice, since asking
     * Hopper to plan a *new* trip only makes sense before navigation starts.
     */
    private suspend fun currentFix(): Pair<Double, Double>? =
        navigationSession.lastFix.value ?: riderLocationProvider.lastFix()

    /**
     * `stopRepository.searchStops` only matches GTFS stop/station names (e.g. "Crossgates
     * Mall Transit Center") — it was never able to resolve a plain street address like
     * "1703 Foster Ave, Schenectady, NY", so `planTrip` always failed on real addresses
     * even though PlanTripUseCase itself only needs coordinates, not an actual stop.
     * Falls back to the ORS geocode proxy (same Worker/key as the walking-directions
     * lookup) when the stop search comes up empty.
     */
    private suspend fun resolvePlace(query: String, bias: Pair<Double, Double>?): ResolvedPlace? {
        if (query.trim().lowercase() in SELF_LOCATION_PHRASES) {
            return bias?.let { ResolvedPlace("your current location", it.first, it.second) }
        }
        stopRepository.searchStops(query, bias).firstOrNull()?.let {
            Log.d(TAG, "resolvePlace(\"$query\") -> stop \"${it.name}\" (${it.lat}, ${it.lng})")
            return ResolvedPlace(it.name, it.lat, it.lng)
        }
        return try {
            val candidate = orsApi.geocode(OrsGeocodeRequest(query)).features.firstOrNull()
            if (candidate == null) {
                Log.d(TAG, "resolvePlace(\"$query\") -> no stop match and geocode returned no features")
                return null
            }
            val coords = candidate.geometry?.coordinates?.takeIf { it.size >= 2 }
            if (coords == null) {
                Log.d(TAG, "resolvePlace(\"$query\") -> geocode candidate had no usable coordinates")
                return null
            }
            Log.d(TAG, "resolvePlace(\"$query\") -> geocoded \"${candidate.properties?.label}\" (${coords[1]}, ${coords[0]})")
            ResolvedPlace(candidate.properties?.label ?: query, lat = coords[1], lng = coords[0])
        } catch (e: Exception) {
            Log.d(TAG, "resolvePlace(\"$query\") -> geocode call failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    @Tool(
        description = "Plan a transit trip between two named places (street addresses, landmarks, " +
            "or transit stops). Pass \"current location\" for from or to when the rider means " +
            "where they are right now (e.g. \"from here\", \"near me\") — never ask them for an " +
            "address in that case. Returns a short human-readable summary of the best itinerary found.",
    )
    fun planTrip(
        @ToolParam(description = "Starting place name, address, or \"current location\"") from: String,
        @ToolParam(description = "Destination place name, address, or \"current location\"") to: String,
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        try {
            val bias = currentFix()
            val fromPlace = resolvePlace(from, bias)
                ?: return@runBlocking mapOf("error" to "couldn't find a place named \"$from\"")
            val toPlace = resolvePlace(to, bias)
                ?: return@runBlocking mapOf("error" to "couldn't find a place named \"$to\"")

            val plans = planTripUseCase(
                PlanRequest(
                    fromName = fromPlace.name,
                    fromLat = fromPlace.lat,
                    fromLng = fromPlace.lng,
                    toName = toPlace.name,
                    toLat = toPlace.lat,
                    toLng = toPlace.lng,
                ),
            )
            Log.d(TAG, "planTrip: ${fromPlace.name} -> ${toPlace.name} returned ${plans.size} candidate plan(s)")
            val best = plans.firstOrNull()
                ?: return@runBlocking mapOf("error" to "no transit route found between those places")

            tripSession.selectPlan(best)
            val useImperial = userPreferences.useImperialUnits.first()
            mapOf("summary" to itineraryTextFormatter.toText(best, shareUrl = null, useImperial = useImperial))
        } catch (e: Exception) {
            Log.d(TAG, "planTrip failed: ${e.javaClass.simpleName}: ${e.message}")
            mapOf("error" to (e.message ?: "trip planning failed"))
        }
    }

    @Tool(description = "Find transit stops near the rider's current location.")
    fun findNearbyStops(
        @ToolParam(description = "Search radius in meters, default 800") radiusMeters: Int,
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val fix = currentFix()
            ?: return@runBlocking mapOf("error" to "the rider's current location isn't available right now")
        try {
            val stops = stopRepository.getStopsNearby(fix.first, fix.second, radiusMeters.coerceAtLeast(50) / 1000.0)
            mapOf(
                "stops" to stops.take(8).map { s ->
                    mapOf("name" to s.name, "transitTypes" to s.transitTypes)
                },
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "couldn't look up nearby stops"))
        }
    }

    @Tool(description = "Get upcoming departures at a named transit stop.")
    fun nextDepartures(
        @ToolParam(description = "Stop name") stopName: String,
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        try {
            val bias = currentFix()
            val stop = stopRepository.searchStops(stopName, bias).firstOrNull()
                ?: return@runBlocking mapOf("error" to "couldn't find a stop named \"$stopName\"")
            val schedule = stopRepository.getRoutesAndScheduleForStop(stop.stopId)
            mapOf(
                "stop" to stop.name,
                "departures" to schedule.upcoming.take(5).map { d ->
                    mapOf(
                        "route" to d.routeShortName.ifBlank { d.routeLongName },
                        "headsign" to d.headsign,
                        "departureTime" to d.departureTime,
                        "realtime" to d.isRealtime,
                    )
                },
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "couldn't look up departures"))
        }
    }

    @Tool(description = "Get the rider's current status if they're actively navigating a planned trip.")
    fun currentTripStatus(): Map<String, Any> {
        val state = navigationSession.state.value
            ?: return mapOf("status" to "not currently navigating a trip")
        return mapOf(
            "instruction" to state.instruction,
            "nextInstruction" to (state.nextInstruction ?: ""),
            "distanceToNextMeters" to state.distanceToNextM,
            "offRoute" to state.offRoute,
            "arrived" to state.arrived,
        )
    }

    private companion object {
        const val TAG = "TransitToolSet"
        val SELF_LOCATION_PHRASES = setOf(
            "current location", "my current location", "here", "my location",
            "where i am", "where i'm at",
        )
    }
}
