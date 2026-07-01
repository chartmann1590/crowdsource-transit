import type { Stop } from '../types/transit';

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

function tlStopToStop(tl: TransitlandStopItem): Stop {
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
  return data.stops.map(tlStopToStop);
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
