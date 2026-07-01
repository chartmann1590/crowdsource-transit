import { ref, get } from 'firebase/database';
import { database } from './config';
import type { Stop } from '../types/transit';
import {
  searchStops as tlSearchStops,
  getNearbyStops as tlGetNearbyStops,
  getStopByOnestopId,
} from '../api/transitland';

export async function getStop(stopId: string): Promise<Stop | null> {
  const snap = await get(ref(database, `stops/${stopId}`));
  const fb = snap.val() as Stop | null;
  if (fb) return fb;
  return getStopByOnestopId(stopId);
}

export async function getNearbyStops(
  lat: number,
  lng: number,
  maxResults = 50,
  _latDelta?: number,
  _lngDelta?: number,
): Promise<Stop[]> {
  return tlGetNearbyStops(lat, lng, 5000, maxResults);
}

export async function searchStops(query_: string): Promise<Stop[]> {
  return tlSearchStops(query_);
}
