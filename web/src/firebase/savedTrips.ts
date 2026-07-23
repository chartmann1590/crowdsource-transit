import { get, onValue, push, ref, remove, set } from 'firebase/database';
import type { TripPlan } from '../types/itinerary';
import { auth, database } from './config';

/**
 * Saved trips live at savedTrips/{uid}/{tripId}: origin + destination only (no
 * departure/arrival times or legs). A saved trip is a place-to-place shortcut, not a
 * frozen schedule snapshot — schedules change day to day, so opening a saved trip
 * always re-plans from the current time to show accurate, up-to-date options (Android
 * twin: data/repository/SavedTripRepository.kt). Client-side cap keeps RTDB Spark-safe.
 */

export const MAX_SAVED_TRIPS = 30;

export interface SavedTrip {
  tripId: string;
  fromName: string;
  fromLat: number;
  fromLng: number;
  toName: string;
  toLat: number;
  toLng: number;
  createdAt: number;
}

/** Saves the trip's origin/destination only — never its computed times. */
export async function saveTrip(plan: TripPlan): Promise<string> {
  const user = auth.currentUser;
  if (!user) throw new Error('Not authenticated');

  const listRef = ref(database, `savedTrips/${user.uid}`);
  const existing = await get(listRef);
  if (existing.exists() && Object.keys(existing.val() as object).length >= MAX_SAVED_TRIPS) {
    throw new Error(`Saved trip limit reached (${MAX_SAVED_TRIPS}). Delete one first.`);
  }

  const tripRef = push(listRef);
  await set(tripRef, {
    createdAt: Date.now(),
    fromName: plan.from.name,
    fromLat: plan.from.lat,
    fromLng: plan.from.lng,
    toName: plan.to.name,
    toLat: plan.to.lat,
    toLng: plan.to.lng,
  });
  return tripRef.key!;
}

export async function deleteTrip(tripId: string): Promise<void> {
  const user = auth.currentUser;
  if (!user) throw new Error('Not authenticated');
  await remove(ref(database, `savedTrips/${user.uid}/${tripId}`));
}

export function observeSavedTrips(uid: string, callback: (trips: SavedTrip[]) => void): () => void {
  const listRef = ref(database, `savedTrips/${uid}`);
  return onValue(listRef, (snap) => {
    const val = (snap.val() ?? {}) as Record<string, Omit<SavedTrip, 'tripId'>>;
    const trips = Object.entries(val)
      .map(([tripId, t]) => ({ tripId, ...t }))
      .sort((a, b) => b.createdAt - a.createdAt);
    callback(trips);
  });
}
