import type { Stop, TransitType } from '../types/transit';

const TRANSITLAND_BASE = 'https://api.transit.land/api/v2/rest';
const API_KEY = import.meta.env.VITE_TRANSITLAND_API_KEY as string;

interface TransitlandStopResponse {
  stops: TransitlandStopItem[];
}

interface TransitlandStopItem {
  onestop_id: string;
  stop_id?: string;
  stop_name?: string;
  stop_desc?: string;
  geometry?: {
    type: string;
    coordinates: number[];
  };
  place?: {
    adm0_name?: string;
    adm1_name?: string;
  };
  feed_version?: {
    feed?: {
      onestop_id?: string;
    };
  };
  route_stops?: {
    route?: {
      onestop_id?: string;
      route_id?: string;
      route_short_name?: string;
      route_long_name?: string;
      route_type?: number;
      route_color?: string;
      route_text_color?: string;
      agency?: {
        onestop_id?: string;
        agency_name?: string;
        agency_id?: string;
      };
    };
  }[];
}

interface TransitlandRoutesResponse {
  routes: TransitlandRouteItem[];
}

interface TransitlandRouteItem {
  route_type?: number;
}

// === Route detail (route + its ordered stops) ===

interface TransitlandFullRoutesResponse {
  routes: TransitlandFullRouteItem[];
}

interface TransitlandFullRouteItem {
  onestop_id?: string;
  route_id?: string;
  route_short_name?: string;
  route_long_name?: string;
  route_type?: number;
  route_color?: string;
  route_text_color?: string;
  agency?: { onestop_id?: string; agency_name?: string; agency_id?: string };
  route_stops?: {
    stop?: {
      stop_id?: string;
      stop_name?: string;
      geometry?: { type: string; coordinates: number[] };
    };
  }[];
}

export interface RouteStopSummary {
  gtfsStopId: string;
  name: string;
  lat: number;
  lng: number;
}

export interface RouteWithStops {
  onestopId: string;
  routeId: string;
  shortName: string;
  longName: string;
  transitType: TransitType | 'transit';
  color: string;
  textColor: string;
  agencyName: string;
  stops: RouteStopSummary[];
}

// === Departures endpoint (Stop Departures — Transitland v2 REST) ===

interface TransitlandDeparturesResponse {
  stops: TransitlandDepartureStopItem[];
  meta?: { after?: number };
}

interface TransitlandDepartureStopItem {
  onestop_id?: string;
  stop_name?: string;
  departures?: TransitlandDepartureItem[];
}

interface TransitlandTimeInfo {
  scheduled?: string;
  scheduled_utc?: string;
  estimated?: string;
  estimated_utc?: string;
}

export interface TransitlandDepartureItem {
  // Transitland nests route info under `trip.route`, not as a sibling of `trip`.
  trip: TransitlandTripDto;
  arrival?: TransitlandTimeInfo;
  departure?: TransitlandTimeInfo;
  arrival_time?: string;
  departure_time?: string;
  stop_headsign?: string;
  interpolated?: number;
  // Transitland returns this as a plain string (e.g. "STATIC"), not an array.
  schedule_relationship?: string;
  trip_short_name?: string;
  /** Board position of this stop within the trip's stop sequence. */
  stop_sequence?: number;
}

export interface TransitlandTripDto {
  trip_id?: string;
  trip_headsign?: string;
  onestop_id?: string;
  trip_short_name?: string;
  service?: TransitlandServiceDto;
  route?: TransitlandRouteInfoDto;
  /** Transitland internal integer trip id — the /routes/{key}/trips/{id} path key. */
  id?: number;
}

interface TransitlandServiceDto {
  service_days?: number[];
  service_start_date?: string;
  service_end_date?: string;
  service_type?: string;
}

interface TransitlandRouteInfoDto {
  onestop_id?: string;
  route_id?: string;
  route_short_name?: string;
  route_long_name?: string;
  route_type?: number;
  route_color?: string;
  route_text_color?: string;
  agency?: { onestop_id?: string; agency_name?: string; agency_id?: string };
}

export interface ServedRoute {
  routeId: string;
  onestopId: string;
  shortName: string;
  longName: string;
  routeType: number | null;
  transitType: TransitType | 'transit';
  color: string;
  textColor: string;
  agencyName: string;
  nextDepartureTime: string | null;
}

export interface ServedDeparture {
  routeOnestopId: string;
  routeId: string;
  routeShortName: string;
  routeLongName: string;
  routeType: number | null;
  transitType: TransitType | 'transit';
  routeColor: string;
  headsign: string;
  departureTime: string;
  arrivalTime?: string;
  interpolated: boolean;
  isRealtime: boolean;
}

export function gtfsRouteTypeToTransitType(routeType: number): TransitType | 'transit' {
  switch (routeType) {
    case 0: return 'tram';
    case 1: return 'subway';
    case 2: return 'train';
    case 3: return 'bus';
    case 4: return 'ferry';
    default: return 'transit';
  }
}

export function tlStopToStop(tl: TransitlandStopItem, overrides?: Partial<Stop>): Stop {
  const coords = tl.geometry?.coordinates ?? [];
  const routeIds: Record<string, boolean> = {};
  const transitTypesSet = new Set<TransitType>();
  const agencyNamesSet = new Set<string>();

  if (tl.route_stops) {
    for (const rs of tl.route_stops) {
      if (rs.route) {
        const onestopId = rs.route.onestop_id;
        if (onestopId) {
          routeIds[onestopId] = true;
        }
        if (rs.route.route_type !== undefined) {
          const type = gtfsRouteTypeToTransitType(rs.route.route_type);
          if (type !== 'transit') {
            transitTypesSet.add(type);
          }
        }
        if (rs.route.agency?.agency_name) {
          agencyNamesSet.add(rs.route.agency.agency_name);
        }
      }
    }
  }

  return {
    stopId: tl.onestop_id,
    agencyId: '',
    name: tl.stop_name ?? tl.onestop_id,
    desc: tl.stop_desc ?? '',
    lat: coords[1] ?? 0,
    lng: coords[0] ?? 0,
    code: tl.stop_id ?? '',
    country: tl.place?.adm0_name ?? '',
    state: tl.place?.adm1_name ?? '',
    city: '',
    transitTypes: Array.from(transitTypesSet),
    routeIds,
    agencyNames: Array.from(agencyNamesSet),
    ratingSum: 0,
    ratingCount: 0,
    commentCount: 0,
    features: {
      shelter: false,
      bench: false,
      lighting: false,
      elevator: false,
      escalator: false,
      ticketMachine: false,
      bikeParking: false,
      parking: false,
    },
    crowdsourced: false,
    verified: false,
    addedBy: null,
    addedAt: 0,
    lastUpdated: 0,
    active: true,
    route_stops: tl.route_stops,
    ...overrides,
  };
}

async function fetchTransitland(path: string, params: Record<string, string>): Promise<Stop[]> {
  const url = new URL(`${TRANSITLAND_BASE}${path}`);
  url.searchParams.set('apikey', API_KEY);
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value);
  }
  const res = await fetch(url.toString());
  if (!res.ok) {
    throw new Error(`Transitland API error: ${res.status} ${res.statusText}`);
  }
  const data: TransitlandStopResponse = await res.json();
  // Transitland can return the same onestop_id more than once (e.g. across duplicate
  // feed entries); dedupe since stopId is used as a React list key downstream.
  const seen = new Set<string>();
  const stops: Stop[] = [];
  for (const s of data.stops) {
    if (seen.has(s.onestop_id)) continue;
    seen.add(s.onestop_id);
    stops.push(tlStopToStop(s));
  }
  return stops;
}

export async function getRoutesNear(
  lat: number,
  lng: number,
  radiusMeters: number = 80,
  limit: number = 10,
): Promise<TransitType[]> {
  const url = new URL(`${TRANSITLAND_BASE}/routes`);
  url.searchParams.set('apikey', API_KEY);
  url.searchParams.set('lat', String(lat));
  url.searchParams.set('lon', String(lng));
  url.searchParams.set('radius', String(Math.round(radiusMeters)));
  url.searchParams.set('limit', String(limit));
  try {
    const res = await fetch(url.toString());
    if (!res.ok) return [];
    const data: TransitlandRoutesResponse = await res.json();
    return data.routes
      .map((r) => gtfsRouteTypeToTransitType(r.route_type ?? -1))
      .filter((t): t is TransitType => t !== 'transit')
      .filter((t, i, arr) => arr.indexOf(t) === i);
  } catch {
    return [];
  }
}

const SEARCH_BIAS_RADIUS_M = 50_000;

/**
 * Text search, optionally biased toward a location. Transitland's `search` matches
 * stop names anywhere (e.g. "Schenectady" can hit Brooklyn's "Schenectady Ave" ahead of
 * the actual city of Schenectady, NY), so when a bias point is known we first try a
 * radius-scoped search and only fall back to the unscoped one if that finds nothing —
 * preserving the ability to search for a stop far from the bias point.
 */
export async function searchStops(query: string, near?: { lat: number; lng: number }): Promise<Stop[]> {
  if (near) {
    const biased = await fetchTransitland('/stops', {
      search: query,
      lat: String(near.lat),
      lon: String(near.lng),
      radius: String(SEARCH_BIAS_RADIUS_M),
      limit: '20',
    });
    if (biased.length > 0) return biased;
  }
  return fetchTransitland('/stops', { search: query, limit: '20' });
}

export async function getNearbyStops(
  lat: number,
  lng: number,
  radiusMeters: number = 5000,
  limit: number = 50,
): Promise<Stop[]> {
  return fetchTransitland('/stops', {
    lat: String(lat),
    lon: String(lng),
    radius: String(Math.round(radiusMeters)),
    limit: String(limit),
  });
}

export async function getStopByOnestopId(onestopId: string): Promise<Stop | null> {
  const stops = await fetchTransitland('/stops', { onestop_id: onestopId });
  return stops[0] ?? null;
}

const MAX_DEPARTURES_LIMIT = 200;
const FULL_DAY_SECONDS = 86400;

export function normalizeColor(hex: string | undefined): string {
  if (!hex) return '';
  const trimmed = hex.trim();
  if (!trimmed) return '';
  return trimmed.startsWith('#') ? trimmed : `#${trimmed}`;
}

export function routeColorOrDefault(transitType: TransitType | 'transit', color?: string): string {
  const c = normalizeColor(color);
  if (c) return c;
  switch (transitType) {
    case 'bus': return '#00A862';
    case 'train': return '#2563EB';
    case 'subway': return '#8B2FC9';
    case 'ferry': return '#0891B2';
    case 'tram': return '#EA7317';
    default: return '#00A862';
  }
}

function secondsToHHMMSS(totalSeconds: number): string {
  const secondsInDay = 86400;
  const wrapped = ((totalSeconds % secondsInDay) + secondsInDay) % secondsInDay;
  const hours = Math.floor(wrapped / 3600);
  const minutes = Math.floor((wrapped % 3600) / 60);
  const seconds = Math.floor(wrapped % 60);
  const hh = String(hours).padStart(2, '0');
  const mm = String(minutes).padStart(2, '0');
  const ss = String(seconds).padStart(2, '0');
  return `${hh}:${mm}:${ss}`;
}

function parseGtfsTime(raw: string | undefined): number | null {
  if (!raw) return null;
  const parts = raw.split(':').map((p) => parseInt(p, 10));
  if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) return null;
  return parts[0] * 3600 + parts[1] * 60 + parts[2];
}

// Transitland provides an absolute UTC instant for each scheduled/estimated time
// (`scheduled_utc`/`estimated_utc`), computed server-side from the stop's own
// timezone. Comparing against this (rather than the browser's local wall clock)
// keeps "next departure"/"upcoming" correct for stops outside the user's timezone.
function departureUtcMs(t?: TransitlandTimeInfo): number | null {
  const iso = t?.estimated_utc ?? t?.scheduled_utc;
  if (!iso) return null;
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? null : ms;
}

export interface RouteAndScheduleResult {
  routes: ServedRoute[];
  upcoming: ServedDeparture[];
}

export class TransitlandRateLimitError extends Error {
  constructor() {
    super('Transitland rate limit exceeded');
    this.name = 'TransitlandRateLimitError';
  }
}

/**
 * Fetch scheduled + real-time departures for a stop for the current service day,
 * then derive (a) the deduplicated list of routes serving this stop and
 * (b) the upcoming departures filtered to times at or after "now".
 *
 * Uses Transitland v2 REST endpoint:
 *   GET /stops/{stop_key}/departures?relative_date=TODAY&next=86400
 */
export async function getRoutesAndScheduleServingStop(
  onestopStopId: string,
  options?: { upcomingWindowSeconds?: number; maxUpcoming?: number },
): Promise<RouteAndScheduleResult> {
  const upcomingWindow = options?.upcomingWindowSeconds ?? FULL_DAY_SECONDS;
  const maxUpcoming = options?.maxUpcoming ?? 25;

  const url = new URL(`${TRANSITLAND_BASE}/stops/${encodeURIComponent(onestopStopId)}/departures`);
  url.searchParams.set('apikey', API_KEY);
  url.searchParams.set('relative_date', 'TODAY');
  url.searchParams.set('next', String(FULL_DAY_SECONDS));
  url.searchParams.set('limit', String(MAX_DEPARTURES_LIMIT));
  url.searchParams.set('include_alerts', 'false');

  const res = await fetch(url.toString());
  if (res.status === 429 || res.status === 403) {
    throw new TransitlandRateLimitError();
  }
  if (!res.ok) {
    throw new Error(`Transitland departures API error: ${res.status} ${res.statusText}`);
  }
  const data: TransitlandDeparturesResponse = await res.json();

  const departures = data.stops?.flatMap((s) => s.departures ?? []) ?? [];

  const routesMap = new Map<string, ServedRoute>();
  const nextDepartureUtcMs = new Map<string, number>();
  const upcoming: { departure: ServedDeparture; utcMs: number }[] = [];

  const nowMs = Date.now();
  const upcomingCutoffMs = nowMs + upcomingWindow * 1000;

  for (const dep of departures) {
    const route = dep.trip?.route;
    const onestopId = route?.onestop_id ?? '';
    if (!onestopId) continue;

    const transitType = gtfsRouteTypeToTransitType(route?.route_type ?? -1);
    const color = routeColorOrDefault(transitType, route?.route_color);
    const textColor = normalizeColor(route?.route_text_color) || '#FFFFFF';
    const agencyName = route?.agency?.agency_name ?? '';

    if (!routesMap.has(onestopId)) {
      routesMap.set(onestopId, {
        routeId: route?.route_id ?? '',
        onestopId,
        shortName: route?.route_short_name ?? '',
        longName: route?.route_long_name ?? '',
        routeType: route?.route_type ?? null,
        transitType,
        color,
        textColor,
        agencyName,
        nextDepartureTime: null,
      });
    }
    const routeEntry = routesMap.get(onestopId)!;

    // depUtcMs is the authoritative instant (from the stop's own timezone) used for
    // filtering/ordering; depSec (local wall-clock HH:MM:SS) is display-only.
    const depUtcMs = departureUtcMs(dep.departure) ?? departureUtcMs(dep.arrival);
    const depSec = parseGtfsTime(dep.departure_time ?? dep.arrival_time);
    if (depUtcMs !== null && depSec !== null) {
      if (depUtcMs >= nowMs) {
        const currentNextMs = nextDepartureUtcMs.get(onestopId);
        if (currentNextMs === undefined || depUtcMs < currentNextMs) {
          nextDepartureUtcMs.set(onestopId, depUtcMs);
          routeEntry.nextDepartureTime = secondsToHHMMSS(depSec);
        }
      }

      if (depUtcMs >= nowMs && depUtcMs <= upcomingCutoffMs) {
        upcoming.push({
          utcMs: depUtcMs,
          departure: {
            routeOnestopId: onestopId,
            routeId: route?.route_id ?? '',
            routeShortName: route?.route_short_name ?? '',
            routeLongName: route?.route_long_name ?? '',
            routeType: route?.route_type ?? null,
            transitType,
            routeColor: color,
            headsign: dep.trip?.trip_headsign ?? dep.stop_headsign ?? '',
            departureTime: secondsToHHMMSS(depSec),
            arrivalTime: dep.arrival_time ? secondsToHHMMSS(parseGtfsTime(dep.arrival_time)!) : undefined,
            interpolated: dep.interpolated === 1,
            isRealtime: dep.schedule_relationship != null && dep.schedule_relationship !== 'STATIC',
          },
        });
      }
    }
  }

  upcoming.sort((a, b) => a.utcMs - b.utcMs);

  return {
    routes: Array.from(routesMap.values()).sort((a, b) => {
      const aNext = nextDepartureUtcMs.get(a.onestopId) ?? Infinity;
      const bNext = nextDepartureUtcMs.get(b.onestopId) ?? Infinity;
      if (aNext !== bNext) return aNext - bNext;
      return (a.shortName || a.longName).localeCompare(b.shortName || b.longName);
    }),
    upcoming: upcoming.slice(0, maxUpcoming).map((u) => u.departure),
  };
}

export function getStaticRoutesAndAgencies(stop: Stop): { routes: ServedRoute[]; upcoming: ServedDeparture[] } {
  const routesMap = new Map<string, ServedRoute>();

  if (stop.route_stops) {
    for (const rs of stop.route_stops) {
      if (rs.route) {
        const onestopId = rs.route.onestop_id ?? '';
        if (!onestopId) continue;
        const transitType = gtfsRouteTypeToTransitType(rs.route.route_type ?? -1);
        const color = routeColorOrDefault(transitType, rs.route.route_color);
        const textColor = normalizeColor(rs.route.route_text_color) || '#FFFFFF';
        const agencyName = rs.route.agency?.agency_name ?? '';
        
        routesMap.set(onestopId, {
          routeId: rs.route.route_id ?? '',
          onestopId,
          shortName: rs.route.route_short_name ?? '',
          longName: rs.route.route_long_name ?? '',
          routeType: rs.route.route_type ?? null,
          transitType,
          color,
          textColor,
          agencyName,
          nextDepartureTime: null,
        });
      }
    }
  }

  return {
    routes: Array.from(routesMap.values()).sort((a, b) =>
      (a.shortName || a.longName).localeCompare(b.shortName || b.longName)
    ),
    upcoming: [],
  };
}

export async function getRouteWithStops(onestopId: string): Promise<RouteWithStops | null> {
  const url = new URL(`${TRANSITLAND_BASE}/routes/${encodeURIComponent(onestopId)}`);
  url.searchParams.set('apikey', API_KEY);
  const res = await fetch(url.toString());
  if (!res.ok) {
    throw new Error(`Transitland route API error: ${res.status} ${res.statusText}`);
  }
  const data: TransitlandFullRoutesResponse = await res.json();
  const r = data.routes?.[0];
  if (!r) return null;

  const transitType = gtfsRouteTypeToTransitType(r.route_type ?? -1);
  const stops: RouteStopSummary[] = (r.route_stops ?? [])
    .map((rs) => rs.stop)
    .filter((s): s is NonNullable<typeof s> => s != null)
    .map((s) => ({
      gtfsStopId: s.stop_id ?? '',
      name: s.stop_name ?? '',
      lat: s.geometry?.coordinates?.[1] ?? 0,
      lng: s.geometry?.coordinates?.[0] ?? 0,
    }));

  return {
    onestopId: r.onestop_id ?? '',
    routeId: r.route_id ?? '',
    shortName: r.route_short_name ?? '',
    longName: r.route_long_name ?? '',
    transitType,
    color: routeColorOrDefault(transitType, r.route_color),
    textColor: normalizeColor(r.route_text_color) || '#FFFFFF',
    agencyName: r.agency?.agency_name ?? '',
    stops,
  };
}

// === Raw fetchers for the trip-planning router (web/src/routing/transitlandSource.ts) ===

export interface TransitlandTripStopTimeItem {
  arrival_time?: string;
  departure_time?: string;
  stop_sequence?: number;
  stop?: {
    id?: number;
    stop_id?: string;
    stop_name?: string;
    geometry?: { type: string; coordinates: number[] };
  };
}

export interface TransitlandTripDetailItem {
  id?: number;
  trip_id?: string;
  trip_headsign?: string;
  stop_times?: TransitlandTripStopTimeItem[];
  shape?: {
    // include_geometry=true: LineString whose points may carry a 3rd elevation element
    geometry?: { type: string; coordinates: number[][] };
  };
}

/** GET /stops/{stop_key}/departures — stop_key is a onestop_id or integer stop id. */
export async function fetchStopDeparturesRaw(
  stopKey: string,
  nextSeconds: number,
  limit: number = MAX_DEPARTURES_LIMIT,
): Promise<TransitlandDepartureItem[]> {
  const url = new URL(`${TRANSITLAND_BASE}/stops/${encodeURIComponent(stopKey)}/departures`);
  url.searchParams.set('apikey', API_KEY);
  url.searchParams.set('relative_date', 'TODAY');
  url.searchParams.set('next', String(Math.min(FULL_DAY_SECONDS, Math.max(60, Math.round(nextSeconds)))));
  url.searchParams.set('limit', String(limit));
  url.searchParams.set('include_alerts', 'false');
  const res = await fetch(url.toString());
  if (res.status === 429 || res.status === 403) throw new TransitlandRateLimitError();
  if (!res.ok) throw new Error(`Transitland departures API error: ${res.status} ${res.statusText}`);
  const data: TransitlandDeparturesResponse = await res.json();
  return data.stops?.flatMap((s) => s.departures ?? []) ?? [];
}

/** GET /routes/{route_key}/trips/{trip_int_id}?include_geometry=true — null if unavailable. */
export async function fetchTripDetailRaw(
  routeOnestopId: string,
  tripIntId: number,
): Promise<TransitlandTripDetailItem | null> {
  const url = new URL(
    `${TRANSITLAND_BASE}/routes/${encodeURIComponent(routeOnestopId)}/trips/${tripIntId}`,
  );
  url.searchParams.set('apikey', API_KEY);
  url.searchParams.set('include_geometry', 'true');
  const res = await fetch(url.toString());
  if (res.status === 429 || res.status === 403) throw new TransitlandRateLimitError();
  if (!res.ok) return null;
  const data: { trips?: TransitlandTripDetailItem[] } = await res.json();
  return data.trips?.[0] ?? null;
}

/**
 * Resolves a Transitland stop's onestop_id from its coordinates. Route detail only gives
 * stop name/lat/lng (no onestop_id), so this does a tight-radius nearby lookup to find the
 * matching stop when the user clicks into it from a route's stop list.
 */
export async function resolveStopIdNear(lat: number, lng: number): Promise<string | null> {
  const url = new URL(`${TRANSITLAND_BASE}/stops`);
  url.searchParams.set('apikey', API_KEY);
  url.searchParams.set('lat', String(lat));
  url.searchParams.set('lon', String(lng));
  url.searchParams.set('radius', '50');
  url.searchParams.set('limit', '1');
  try {
    const res = await fetch(url.toString());
    if (!res.ok) return null;
    const data: TransitlandStopResponse = await res.json();
    return data.stops?.[0]?.onestop_id ?? null;
  } catch {
    return null;
  }
}

