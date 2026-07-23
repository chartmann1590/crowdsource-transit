/** Router-internal types shared by the search core and its data sources (docs/routing/router-spec.md). */

export interface LatLng {
  lat: number;
  lng: number;
}

export interface PlanRequest {
  from: LatLng & { name: string };
  to: LatLng & { name: string };
  /** Epoch ms; defaults to "now". Mutually exclusive with arriveByMs. */
  departAtMs?: number;
  /** Epoch ms; if set (and departAtMs unset), search backward for the itinerary
   * arriving as close as possible to, but not after, this time. */
  arriveByMs?: number;
}

export interface StopCandidate {
  /** Key accepted by GtfsDataSource.departures (Transitland: onestop_id or integer id string). */
  key: string;
  name: string;
  lat: number;
  lng: number;
}

export interface RouteMeta {
  onestopId: string;
  shortName: string;
  longName: string;
  color: string;
  agency: string;
  /** Itinerary mode string: bus | subway | train | tram | ferry | transit */
  mode: string;
}

export interface DepartureOption {
  route: RouteMeta;
  headsign: string;
  /** Transitland internal integer trip id — the /routes/{key}/trips/{id} path key. */
  tripIntId: number;
  /** Position of this stop within the trip's stop sequence. */
  boardStopSequence: number;
  depUtcMs: number;
}

export interface TripStopTime {
  seq: number;
  gtfsStopId: string;
  /** Transitland integer stop id as string; usable as a StopCandidate.key for transfers. */
  stopKey: string;
  name: string;
  lat: number;
  lng: number;
  /** Seconds since local service-day midnight; may exceed 86400. */
  arrivalSec: number;
  departureSec: number;
}

export interface TripDetails {
  gtfsTripId: string;
  headsign: string;
  stopTimes: TripStopTime[];
  /** [lng, lat] pairs (any elevation element stripped). */
  shape?: [number, number][];
}

export class RateLimitedError extends Error {
  constructor() {
    super('transit data rate limit exceeded');
    this.name = 'RateLimitedError';
  }
}
