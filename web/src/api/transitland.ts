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
}

interface TransitlandRoutesResponse {
  routes: TransitlandRouteItem[];
}

interface TransitlandRouteItem {
  route_type?: number;
}

function gtfsRouteTypeToTransitType(routeType: number): TransitType | 'transit' {
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
    transitTypes: [],
    routeIds: {},
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
  return data.stops.map((s) => tlStopToStop(s));
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

export async function searchStops(query: string): Promise<Stop[]> {
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
