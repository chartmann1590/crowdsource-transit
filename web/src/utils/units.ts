/**
 * Distance units preference (persisted in localStorage) + formatter.
 * Defaults to imperial (miles/feet) — most of the user base is US transit riders.
 * Mirrors the Android util/DistanceFormat.kt thresholds: imperial shows feet under a mile,
 * else miles; metric shows metres under a kilometre, else km.
 */

export type DistanceUnit = 'imperial' | 'metric';

const STORAGE_KEY = 'distanceUnit';

export function getDistanceUnit(): DistanceUnit {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'metric' ? 'metric' : 'imperial';
  } catch {
    return 'imperial'; // no localStorage (SSR/tests)
  }
}

export function setDistanceUnit(unit: DistanceUnit): void {
  try {
    localStorage.setItem(STORAGE_KEY, unit);
  } catch {
    /* no-op without localStorage */
  }
}

export function formatDistance(meters: number, unit: DistanceUnit = getDistanceUnit()): string {
  if (unit === 'imperial') {
    const feet = meters * 3.28084;
    return feet < 5280 ? `${Math.round(feet)} ft` : `${(feet / 5280).toFixed(1)} mi`;
  }
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
}
