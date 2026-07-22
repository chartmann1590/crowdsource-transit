import {
  fetchStopDeparturesRaw,
  fetchTripDetailRaw,
  getNearbyStops,
  gtfsRouteTypeToTransitType,
  routeColorOrDefault,
} from '../api/transitland';
import type { GtfsDataSource } from './dataSource';
import type { DepartureOption, StopCandidate, TripDetails, TripStopTime } from './types';

/**
 * Live Transitland-backed GtfsDataSource with in-memory TTL caches
 * (docs/routing/router-spec.md: departures 5 min, trips 24 h, stopsNear 10 min).
 * Rate limiting surfaces as TransitlandRateLimitError from the api layer, which the
 * planTrip orchestrator maps to the router's RateLimitedError semantics.
 */

const STOPS_TTL_MS = 10 * 60 * 1000;
const DEPARTURES_TTL_MS = 5 * 60 * 1000;
const TRIP_TTL_MS = 24 * 3600 * 1000;

class TtlCache<V> {
  private readonly map = new Map<string, { value: V; expiresAt: number }>();
  private readonly ttlMs: number;
  private readonly maxEntries: number;

  constructor(ttlMs: number, maxEntries = 200) {
    this.ttlMs = ttlMs;
    this.maxEntries = maxEntries;
  }

  get(key: string): V | undefined {
    const hit = this.map.get(key);
    if (!hit) return undefined;
    if (Date.now() > hit.expiresAt) {
      this.map.delete(key);
      return undefined;
    }
    return hit.value;
  }

  set(key: string, value: V): void {
    if (this.map.size >= this.maxEntries) {
      const oldest = this.map.keys().next().value;
      if (oldest !== undefined) this.map.delete(oldest);
    }
    this.map.set(key, { value, expiresAt: Date.now() + this.ttlMs });
  }
}

function parseGtfsSeconds(raw: string | undefined): number | null {
  if (!raw) return null;
  const parts = raw.split(':').map((p) => parseInt(p, 10));
  if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) return null;
  return parts[0] * 3600 + parts[1] * 60 + parts[2];
}

export class TransitlandDataSource implements GtfsDataSource {
  private readonly stopsCache = new TtlCache<StopCandidate[]>(STOPS_TTL_MS);
  private readonly depsCache = new TtlCache<DepartureOption[]>(DEPARTURES_TTL_MS);
  private readonly tripCache = new TtlCache<TripDetails | null>(TRIP_TTL_MS, 100);

  async stopsNear(lat: number, lng: number, radiusM: number, limit: number): Promise<StopCandidate[]> {
    const key = `${lat.toFixed(4)},${lng.toFixed(4)},${radiusM},${limit}`;
    const cached = this.stopsCache.get(key);
    if (cached) return cached;
    const stops = await getNearbyStops(lat, lng, radiusM, limit);
    const result = stops.map((s) => ({ key: s.stopId, name: s.name, lat: s.lat, lng: s.lng }));
    this.stopsCache.set(key, result);
    return result;
  }

  async departures(stopKey: string, notBeforeUtcMs: number, windowSec: number): Promise<DepartureOption[]> {
    // Transitland's `next` window starts at "now"; extend it to cover a future notBefore
    // (the router filters by notBefore afterwards). Bucket the cache key so nearby
    // notBefore values share one fetch.
    const nextSec = Math.max(60, Math.ceil((notBeforeUtcMs - Date.now()) / 1000)) + windowSec;
    const bucket = Math.floor(nextSec / 1800);
    const key = `${stopKey}:${bucket}`;
    const cached = this.depsCache.get(key);
    if (cached) return cached;

    const raw = await fetchStopDeparturesRaw(stopKey, nextSec);
    const result: DepartureOption[] = [];
    for (const dep of raw) {
      const route = dep.trip?.route;
      const tripIntId = dep.trip?.id;
      const seq = dep.stop_sequence;
      const iso = dep.departure?.estimated_utc ?? dep.departure?.scheduled_utc;
      if (!route?.onestop_id || tripIntId == null || seq == null || !iso) continue;
      const depUtcMs = Date.parse(iso);
      if (Number.isNaN(depUtcMs)) continue;
      const transitType = gtfsRouteTypeToTransitType(route.route_type ?? -1);
      result.push({
        route: {
          onestopId: route.onestop_id,
          shortName: route.route_short_name ?? '',
          longName: route.route_long_name ?? '',
          color: routeColorOrDefault(transitType, route.route_color),
          agency: route.agency?.agency_name ?? '',
          mode: transitType,
        },
        headsign: dep.trip?.trip_headsign ?? dep.stop_headsign ?? '',
        tripIntId,
        boardStopSequence: seq,
        depUtcMs,
      });
    }
    this.depsCache.set(key, result);
    return result;
  }

  async tripDetails(routeOnestopId: string, tripIntId: number): Promise<TripDetails | null> {
    const key = `${routeOnestopId}/${tripIntId}`;
    const cached = this.tripCache.get(key);
    if (cached !== undefined) return cached;

    const raw = await fetchTripDetailRaw(routeOnestopId, tripIntId);
    let result: TripDetails | null = null;
    if (raw?.stop_times?.length) {
      const stopTimes: TripStopTime[] = [];
      for (const st of raw.stop_times) {
        const arrivalSec = parseGtfsSeconds(st.arrival_time) ?? parseGtfsSeconds(st.departure_time);
        const departureSec = parseGtfsSeconds(st.departure_time) ?? arrivalSec;
        const coords = st.stop?.geometry?.coordinates;
        if (arrivalSec == null || departureSec == null || st.stop_sequence == null || !coords || st.stop?.id == null) {
          continue;
        }
        stopTimes.push({
          seq: st.stop_sequence,
          gtfsStopId: st.stop.stop_id ?? '',
          stopKey: String(st.stop.id),
          name: st.stop.stop_name ?? '',
          lat: coords[1],
          lng: coords[0],
          arrivalSec,
          departureSec,
        });
      }
      stopTimes.sort((a, b) => a.seq - b.seq);
      if (stopTimes.length >= 2) {
        const shapeCoords = raw.shape?.geometry?.coordinates;
        result = {
          gtfsTripId: raw.trip_id ?? '',
          headsign: raw.trip_headsign ?? '',
          stopTimes,
          // Strip any 3rd (elevation) element from shape points.
          shape: shapeCoords?.length ? shapeCoords.map((c) => [c[0], c[1]] as [number, number]) : undefined,
        };
      }
    }
    this.tripCache.set(key, result);
    return result;
  }
}
