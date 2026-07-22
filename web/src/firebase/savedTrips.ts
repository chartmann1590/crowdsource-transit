import { get, onValue, push, ref, remove, set } from 'firebase/database';
import type { TripPlan } from '../types/itinerary';
import { decodeTripPlan, encodeTripPlan } from '../utils/tripCodec';
import { auth, database } from './config';

/**
 * Saved trips live at savedTrips/{uid}/{tripId} as { plan: <encoded blob>, createdAt,
 * fromName, toName } — the blob is the same wire format as share links, so app and web
 * read each other's saves. Client-side cap keeps RTDB usage Spark-safe.
 */

export const MAX_SAVED_TRIPS = 30;

export interface SavedTrip {
  tripId: string;
  fromName: string;
  toName: string;
  createdAt: number;
  /** Encoded blob (decode lazily — lists don't need the full plan). */
  plan: string;
}

export async function saveTrip(plan: TripPlan): Promise<string> {
  const user = auth.currentUser;
  if (!user) throw new Error('Not authenticated');

  const listRef = ref(database, `savedTrips/${user.uid}`);
  const existing = await get(listRef);
  if (existing.exists() && Object.keys(existing.val() as object).length >= MAX_SAVED_TRIPS) {
    throw new Error(`Saved trip limit reached (${MAX_SAVED_TRIPS}). Delete one first.`);
  }

  const blob = await encodeTripPlan(plan);
  const tripRef = push(listRef);
  await set(tripRef, {
    plan: blob,
    createdAt: Date.now(),
    fromName: plan.from.name,
    toName: plan.to.name,
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

export async function decodeSavedTrip(trip: SavedTrip): Promise<TripPlan> {
  return decodeTripPlan(trip.plan);
}
