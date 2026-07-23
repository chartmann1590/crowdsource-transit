import { ref, get, onValue } from 'firebase/database';
import { database } from './config';
import type { Stop, TransitType } from '../types/transit';
import {
  searchStops as tlSearchStops,
  getNearbyStops as tlGetNearbyStops,
  getStopByOnestopId,
  getRoutesNear,
} from '../api/transitland';
import { haversineDistance } from '../utils/distance';

const MAX_TRANSIT_TYPE_LOOKUPS = 15;

function statsFrom(snapshot: Record<string, unknown> | null): {
  ratingSum: number;
  ratingCount: number;
  commentCount: number;
} {
  return {
    ratingSum: (snapshot?.ratingSum as number) ?? 0,
    ratingCount: (snapshot?.ratingCount as number) ?? 0,
    commentCount: (snapshot?.commentCount as number) ?? 0,
  };
}

const FIREBASE_TIMEOUT_MS = 5000;

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms)),
  ]);
}

export async function getStopStats(stopId: string) {
  const snap = await withTimeout(get(ref(database, `stopStats/${stopId}`)), FIREBASE_TIMEOUT_MS);
  return statsFrom(snap.val() as Record<string, unknown> | null);
}

async function transitTypesFor(lat: number, lng: number): Promise<TransitType[]> {
  try {
    const types = await withTimeout(
      getRoutesNear(lat, lng) as unknown as Promise<TransitType[]>,
      FIREBASE_TIMEOUT_MS,
    );
    return types;
  } catch {
    return [];
  }
}

export async function enrichedStop(tlStop: Stop): Promise<Stop> {
  const statsPromise = getStopStats(tlStop.stopId).catch(
    () => ({ ratingSum: 0, ratingCount: 0, commentCount: 0 }),
  );
  const typesPromise = transitTypesFor(tlStop.lat, tlStop.lng).catch(
    () => [] as TransitType[],
  );
  const [stats, types] = await Promise.all([statsPromise, typesPromise]);
  return {
    ...tlStop,
    ...stats,
    transitTypes: types,
    verified: true,
  };
}

export function observeStopStats(
  stopId: string,
  onUpdate: (stop: Stop | null) => void,
): () => void {
  let active = true;
  let firebaseUnsub: (() => void) | null = null;

  const isTransitlandId = /^[rsdf]-/.test(stopId);

  async function fetchStopData(): Promise<Stop | null> {
    if (isTransitlandId) {
      const tlStop = await getStopByOnestopId(stopId);
      if (tlStop) return tlStop;
    }
    // Fallback/Default for crowdsourced / non-Transitland stop
    const snap = await get(ref(database, `stops/${stopId}`));
    if (snap.exists()) {
      return snap.val() as Stop;
    }
    return null;
  }

  fetchStopData().then((baseStop) => {
    if (!active) return;
    if (!baseStop) {
      onUpdate(null);
      return;
    }
    const stopRef = ref(database, `stopStats/${stopId}`);
    firebaseUnsub = onValue(stopRef, async (snap) => {
      if (!active) return;
      const stats = statsFrom(snap.val() as Record<string, unknown> | null);
      const types = baseStop.transitTypes && baseStop.transitTypes.length > 0
        ? baseStop.transitTypes
        : await transitTypesFor(baseStop.lat, baseStop.lng);
      if (active) {
        onUpdate({ ...baseStop, ...stats, transitTypes: types });
      }
    });
  }).catch(() => {
    if (active) onUpdate(null);
  });

  return () => {
    active = false;
    firebaseUnsub?.();
  };
}

export async function getNearbyStops(
  lat: number,
  lng: number,
  radiusKm: number = 5,
  maxResults = 50,
): Promise<Stop[]> {
  try {
    const radiusMeters = Math.min(Math.round(radiusKm * 1000), 10000);
    const tlStops = await tlGetNearbyStops(lat, lng, radiusMeters, maxResults);

    const sorted = tlStops.sort((a, b) =>
      haversineDistance(lat, lng, a.lat, a.lng) - haversineDistance(lat, lng, b.lat, b.lng)
    );

    const toEnrich = sorted.slice(0, MAX_TRANSIT_TYPE_LOOKUPS);
    const enriched = await Promise.all(
      toEnrich.map((stop) => enrichedStop(stop).catch(() => stop)),
    );
    return enriched;
  } catch (err) {
    console.error('getNearbyStops failed:', err);
    return [];
  }
}

export async function searchStops(query_: string, near?: { lat: number; lng: number }): Promise<Stop[]> {
  const tlStops = await tlSearchStops(query_, near);
  const enriched = await Promise.all(
    tlStops.map((stop) => enrichedStop(stop).catch(() => stop)),
  );
  return enriched;
}

export async function getStop(stopId: string): Promise<Stop | null> {
  const isTransitlandId = /^[rsdf]-/.test(stopId);
  let baseStop: Stop | null = null;
  if (isTransitlandId) {
    baseStop = await getStopByOnestopId(stopId);
  }
  if (!baseStop) {
    const snap = await get(ref(database, `stops/${stopId}`));
    if (snap.exists()) {
      baseStop = snap.val() as Stop;
    }
  }
  if (!baseStop) return null;
  return enrichedStop(baseStop);
}
