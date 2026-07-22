/**
 * Shared itinerary interchange format, v1. Mirrors docs/routing/itinerary-spec.md and the
 * Kotlin models in android .../model/ItineraryModels.kt — both implementations are verified
 * against the same golden fixtures in docs/routing/fixtures/.
 */

export const TRIP_PLAN_VERSION = 1;

export interface Place {
  name: string;
  lat: number;
  lng: number;
}

export interface WalkStep {
  text: string;
  dist_m: number;
  /** Maneuver point (where this instruction applies), for plotting on a map. */
  lat: number;
  lng: number;
}

export interface WalkLeg {
  t: 'w';
  from: Place;
  to: Place;
  dep: string; // ISO-8601 UTC
  arr: string;
  dist_m: number;
  poly?: string; // encoded polyline, precision 5
  steps?: WalkStep[];
}

export interface RouteRef {
  onestop_id: string;
  short: string;
  long: string;
  color: string;
  agency: string;
}

export interface TripRef {
  trip_id: string;
  headsign: string;
}

export interface BoardPoint {
  stop_id: string;
  name: string;
  lat: number;
  lng: number;
  dep_utc: string;
}

export interface AlightPoint {
  stop_id: string;
  name: string;
  lat: number;
  lng: number;
  arr_utc: string;
}

export interface TripStop {
  id: string;
  name: string;
  lat: number;
  lng: number;
  arr_utc: string;
}

export interface TransitLeg {
  t: 'r';
  mode: string; // bus | subway | train | tram | ferry | transit
  route: RouteRef;
  trip: TripRef;
  board: BoardPoint;
  alight: AlightPoint;
  /** EVERY stop from board to alight inclusive, in trip order (the correctness guarantee). */
  stops: TripStop[];
  shape_poly?: string;
}

export type Leg = WalkLeg | TransitLeg;

export interface TripPlan {
  v: number;
  planned_at: string;
  from: Place;
  to: Place;
  legs: Leg[];
}

export function isWalkLeg(leg: Leg): leg is WalkLeg {
  return leg.t === 'w';
}

export function isTransitLeg(leg: Leg): leg is TransitLeg {
  return leg.t === 'r';
}
