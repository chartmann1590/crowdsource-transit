import type { DepartureOption, StopCandidate, TripDetails } from './types';

/**
 * All I/O the router needs, pluggable per docs/routing/router-spec.md.
 * Implementations: TransitlandDataSource (live API) and test fixtures.
 * Methods throw RateLimitedError to abort the whole search when throttled.
 */
export interface GtfsDataSource {
  stopsNear(lat: number, lng: number, radiusM: number, limit: number): Promise<StopCandidate[]>;
  departures(stopKey: string, notBeforeUtcMs: number, windowSec: number): Promise<DepartureOption[]>;
  /** null when the trip can't be fetched (e.g. frequency-based synthetic trips) — caller skips it. */
  tripDetails(routeOnestopId: string, tripIntId: number): Promise<TripDetails | null>;
}
