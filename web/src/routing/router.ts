import type { TransitLeg, TripPlan, TripStop, WalkLeg } from '../types/itinerary';
import { TRIP_PLAN_VERSION } from '../types/itinerary';
import { haversineDistance } from '../utils/distance';
import { encodePolyline } from '../utils/polyline';
import type { GtfsDataSource } from './dataSource';
import type { DepartureOption, PlanRequest, StopCandidate, TripDetails, TripStopTime } from './types';

/**
 * Bounded RAPTOR-style transit search per docs/routing/router-spec.md. Pure: all I/O via
 * the GtfsDataSource. Returns up to MAX_RESULTS itineraries whose transit legs are
 * contiguous slices of real trips (the correctness guarantee). Walking legs here are
 * straight-line estimates; planTrip refines the winners through the ORS proxy.
 */

// Default max walk from the trip endpoints to a boarding/alighting stop. In low-density
// areas the nearest stop that actually has service can be well over the old 800 m, so this
// is deliberately generous (~20 min walk). The user can override it per plan via
// PlanRequest.maxWalkToStopM.
export const DEFAULT_CANDIDATE_RADIUS_M = 1600;
export const MAX_CANDIDATES = 5;
// Cap on how many nearby stops we probe for departures when building origin candidates, so a
// cluster of dead (zero-service) stops or a large user-set radius can't blow the API budget.
const CANDIDATE_PROBE_LIMIT = 15;
const DEDUPE_M = 25;
const WINDOW_SEC = 7200;
const DEPS_PER_ROUND = 10;
const DEST_WALK_M = 600;
const TRANSFER_MIN_SEC = 120;
const TRANSFER_WALK_M = 300;
const FRONTIER = 6;
const FRONTIER_DEDUPE_M = 150;
// Every frontier stop gets the nearby-stop walk-transfer check, not just a subset — a
// bus's outbound and inbound directions are frequently different physical stop objects
// a few meters apart, so skipping this for lower-ranked frontier stops silently misses
// the correct-direction boarding right next to a stop we did reach.
const NEARBY_TRANSFER_FRONTIER = FRONTIER;
const MAX_TRANSFERS = 2;
export const WALK_SPEED_MPS = 1.33;
export const WALK_DETOUR = 1.3;
const MAX_RESULTS = 3;
const BOARD_MATCH_M = 100;
const STALE_DEPARTURE_MS = 36 * 3600 * 1000;

interface RideSegment {
  dep: DepartureOption;
  trip: TripDetails;
  boardIndex: number;
  alightIndex: number;
  boardDepUtcMs: number;
  /** Straight-line walk that precedes this ride (from previous alight stop or the origin). */
  walkFrom: { name: string; lat: number; lng: number };
  walkMeters: number;
}

interface Reached {
  stop: TripStopTime;
  arrUtcMs: number;
  path: RideSegment[];
}

function metersBetween(aLat: number, aLng: number, bLat: number, bLng: number): number {
  return haversineDistance(aLat, aLng, bLat, bLng) * 1000;
}

export function walkSeconds(meters: number): number {
  return Math.round((meters * WALK_DETOUR) / WALK_SPEED_MPS);
}

function stopTimeUtcMs(seg: { boardDepUtcMs: number; trip: TripDetails; boardIndex: number }, index: number): number {
  const board = seg.trip.stopTimes[seg.boardIndex];
  const st = seg.trip.stopTimes[index];
  return seg.boardDepUtcMs + (st.arrivalSec - board.departureSec) * 1000;
}

/**
 * Nearby stops within `radiusM`, distance-sorted (nearest first) and deduped at DEDUPE_M.
 * Proximity only — NOT truncated to MAX_CANDIDATES and NOT service-filtered. The origin
 * round picks served stops from this list (skipping dead ones); the destination side uses
 * it only as a non-empty reachability guard.
 */
async function nearbyStopsSorted(
  source: GtfsDataSource,
  lat: number,
  lng: number,
  radiusM: number,
): Promise<StopCandidate[]> {
  const stops = await source.stopsNear(lat, lng, radiusM, 30);
  const sorted = stops
    .map((s) => ({ s, d: metersBetween(lat, lng, s.lat, s.lng) }))
    .filter(({ d }) => d <= radiusM)
    .sort((a, b) => a.d - b.d);
  const kept: StopCandidate[] = [];
  for (const { s } of sorted) {
    if (kept.some((k) => metersBetween(k.lat, k.lng, s.lat, s.lng) < DEDUPE_M)) continue;
    kept.push(s);
  }
  return kept;
}

/** Board index = stop_sequence match, else nearest stop within BOARD_MATCH_M; -1 = unusable. */
function findBoardIndex(trip: TripDetails, dep: DepartureOption, at: { lat: number; lng: number }): number {
  const bySeq = trip.stopTimes.findIndex((st) => st.seq === dep.boardStopSequence);
  if (bySeq >= 0 && metersBetween(trip.stopTimes[bySeq].lat, trip.stopTimes[bySeq].lng, at.lat, at.lng) <= BOARD_MATCH_M) {
    return bySeq;
  }
  let best = -1;
  let bestD = BOARD_MATCH_M;
  trip.stopTimes.forEach((st, i) => {
    const d = metersBetween(st.lat, st.lng, at.lat, at.lng);
    if (d <= bestD) {
      bestD = d;
      best = i;
    }
  });
  return best;
}

function sliceShape(trip: TripDetails, boardIndex: number, alightIndex: number): string | undefined {
  const shape = trip.shape;
  if (!shape || shape.length < 2) return undefined;
  const nearestIndex = (lat: number, lng: number): number => {
    let best = 0;
    let bestD = Infinity;
    shape.forEach(([slng, slat], i) => {
      const d = metersBetween(lat, lng, slat, slng);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    });
    return best;
  };
  const b = trip.stopTimes[boardIndex];
  const a = trip.stopTimes[alightIndex];
  const from = nearestIndex(b.lat, b.lng);
  const to = nearestIndex(a.lat, a.lng);
  if (to <= from) return undefined;
  return encodePolyline(shape.slice(from, to + 1));
}

function toIso(ms: number): string {
  return new Date(Math.round(ms / 1000) * 1000).toISOString().replace(/\.\d{3}Z$/, 'Z');
}

function buildWalkLeg(
  from: { name: string; lat: number; lng: number },
  to: { name: string; lat: number; lng: number },
  depMs: number,
  meters: number,
): WalkLeg {
  return {
    t: 'w',
    from: { name: from.name, lat: from.lat, lng: from.lng },
    to: { name: to.name, lat: to.lat, lng: to.lng },
    dep: toIso(depMs),
    arr: toIso(depMs + walkSeconds(meters) * 1000),
    dist_m: Math.round(meters),
  };
}

function buildTransitLeg(seg: RideSegment): TransitLeg {
  const board = seg.trip.stopTimes[seg.boardIndex];
  const alight = seg.trip.stopTimes[seg.alightIndex];
  const stops: TripStop[] = seg.trip.stopTimes.slice(seg.boardIndex, seg.alightIndex + 1).map((st, i) => ({
    id: st.stopKey,
    name: st.name,
    lat: st.lat,
    lng: st.lng,
    arr_utc: toIso(stopTimeUtcMs(seg, seg.boardIndex + i)),
  }));
  return {
    t: 'r',
    mode: seg.dep.route.mode,
    route: {
      onestop_id: seg.dep.route.onestopId,
      short: seg.dep.route.shortName,
      long: seg.dep.route.longName,
      color: seg.dep.route.color,
      agency: seg.dep.route.agency,
    },
    trip: { trip_id: seg.trip.gtfsTripId, headsign: seg.dep.headsign || seg.trip.headsign },
    board: {
      stop_id: board.stopKey,
      name: board.name,
      lat: board.lat,
      lng: board.lng,
      dep_utc: toIso(seg.boardDepUtcMs),
    },
    alight: {
      stop_id: alight.stopKey,
      name: alight.name,
      lat: alight.lat,
      lng: alight.lng,
      arr_utc: toIso(stopTimeUtcMs(seg, seg.alightIndex)),
    },
    stops,
    shape_poly: sliceShape(seg.trip, seg.boardIndex, seg.alightIndex),
  };
}

function assemblePlan(req: PlanRequest, path: RideSegment[], finalWalkMeters: number, plannedAtMs: number): TripPlan {
  const legs: TripPlan['legs'] = [];
  for (const seg of path) {
    const board = seg.trip.stopTimes[seg.boardIndex];
    const walkDepMs = seg.boardDepUtcMs - walkSeconds(seg.walkMeters) * 1000;
    legs.push(buildWalkLeg(seg.walkFrom, { name: board.name, lat: board.lat, lng: board.lng }, walkDepMs, seg.walkMeters));
    legs.push(buildTransitLeg(seg));
  }
  const last = path[path.length - 1];
  const alight = last.trip.stopTimes[last.alightIndex];
  const alightMs = stopTimeUtcMs(last, last.alightIndex);
  legs.push(
    buildWalkLeg(
      { name: alight.name, lat: alight.lat, lng: alight.lng },
      { name: req.to.name, lat: req.to.lat, lng: req.to.lng },
      alightMs,
      finalWalkMeters,
    ),
  );
  return {
    v: TRIP_PLAN_VERSION,
    planned_at: toIso(plannedAtMs),
    from: { name: req.from.name, lat: req.from.lat, lng: req.from.lng },
    to: { name: req.to.name, lat: req.to.lat, lng: req.to.lng },
    legs,
  };
}

async function departuresFor(
  source: GtfsDataSource,
  stop: StopCandidate,
  notBeforeUtcMs: number,
  nowMs: number,
): Promise<DepartureOption[]> {
  const deps = await source.departures(stop.key, notBeforeUtcMs, WINDOW_SEC);
  const filtered = deps.filter(
    (d) =>
      d.depUtcMs >= notBeforeUtcMs &&
      d.depUtcMs <= notBeforeUtcMs + WINDOW_SEC * 1000 &&
      Math.abs(d.depUtcMs - nowMs) <= STALE_DEPARTURE_MS,
  );
  // Earliest departure per (route, headsign) bounds the trip-detail fetches.
  const earliest = new Map<string, DepartureOption>();
  for (const d of filtered) {
    const key = `${d.route.onestopId}|${d.headsign}`;
    const cur = earliest.get(key);
    if (!cur || d.depUtcMs < cur.depUtcMs) earliest.set(key, d);
  }
  return Array.from(earliest.values());
}

const ARRIVE_BY_WINDOW_MS = WINDOW_SEC * 1000;
const ARRIVE_BY_MAX_ATTEMPTS = 3;

/** Public entry point: dispatches to a depart-at search, or an arrive-by search that
 * walks the depart time backward from the target until it finds itineraries landing
 * at or before it (bounded to ARRIVE_BY_MAX_ATTEMPTS windows to cap API usage). */
export async function planTrips(source: GtfsDataSource, req: PlanRequest): Promise<TripPlan[]> {
  if (req.arriveByMs !== undefined) {
    for (let attempt = 0; attempt < ARRIVE_BY_MAX_ATTEMPTS; attempt++) {
      const departAtMs = req.arriveByMs - ARRIVE_BY_WINDOW_MS * (attempt + 1);
      const plans = await planTripsDepartAt(source, { ...req, departAtMs });
      const onTime = plans.filter((p) => Date.parse((p.legs[p.legs.length - 1] as WalkLeg).arr) <= req.arriveByMs!);
      if (onTime.length > 0) {
        onTime.sort(
          (a, b) =>
            Date.parse((b.legs[b.legs.length - 1] as WalkLeg).arr) -
            Date.parse((a.legs[a.legs.length - 1] as WalkLeg).arr),
        );
        return onTime.slice(0, MAX_RESULTS);
      }
    }
    return [];
  }
  return planTripsDepartAt(source, req);
}

async function planTripsDepartAt(source: GtfsDataSource, req: PlanRequest): Promise<TripPlan[]> {
  const nowMs = Date.now();
  const departAtMs = req.departAtMs ?? nowMs;
  const radiusM = req.maxWalkToStopM ?? DEFAULT_CANDIDATE_RADIUS_M;

  const [originStops, destCands] = await Promise.all([
    nearbyStopsSorted(source, req.from.lat, req.from.lng, radiusM),
    nearbyStopsSorted(source, req.to.lat, req.to.lng, radiusM),
  ]);
  // destCands is only a reachability guard: tryEmit matches the destination point directly,
  // so an alighting stop within DEST_WALK_M of it emits regardless of which stops are here.
  if (originStops.length === 0 || destCands.length === 0) return [];

  const results: { plan: TripPlan; arrMs: number; transfers: number; routeKey: string }[] = [];
  const bestArrival = new Map<string, number>(); // stopKey -> best known arrival

  const tryEmit = (path: RideSegment[]): void => {
    const last = path[path.length - 1];
    const alight = last.trip.stopTimes[last.alightIndex];
    const dDest = metersBetween(alight.lat, alight.lng, req.to.lat, req.to.lng);
    if (dDest > DEST_WALK_M) return;
    const plan = assemblePlan(req, path, dDest, nowMs);
    const lastLeg = plan.legs[plan.legs.length - 1];
    results.push({
      plan,
      arrMs: Date.parse((lastLeg as WalkLeg).arr),
      transfers: path.length - 1,
      routeKey: path.map((s) => s.dep.route.onestopId).join('>'),
    });
  };

  // Expand one boarding: fetch the trip, emit destination hits, collect reached stops.
  const expand = async (
    dep: DepartureOption,
    boardAt: { lat: number; lng: number },
    pathSoFar: RideSegment[],
    walkFrom: { name: string; lat: number; lng: number },
    walkMeters: number,
    reached: Reached[],
  ): Promise<void> => {
    if (pathSoFar.some((s) => s.dep.route.onestopId === dep.route.onestopId && s.dep.headsign === dep.headsign)) return;
    const trip = await source.tripDetails(dep.route.onestopId, dep.tripIntId);
    if (!trip) return;
    const boardIndex = findBoardIndex(trip, dep, boardAt);
    if (boardIndex < 0 || boardIndex >= trip.stopTimes.length - 1) return;
    const segBase = { dep, trip, boardIndex, boardDepUtcMs: dep.depUtcMs, walkFrom, walkMeters };
    for (let i = boardIndex + 1; i < trip.stopTimes.length; i++) {
      const seg: RideSegment = { ...segBase, alightIndex: i };
      const st = trip.stopTimes[i];
      const arrMs = stopTimeUtcMs(seg, i);
      tryEmit([...pathSoFar, seg]);
      const prev = bestArrival.get(st.stopKey);
      if (prev === undefined || arrMs < prev) {
        bestArrival.set(st.stopKey, arrMs);
        reached.push({ stop: st, arrUtcMs: arrMs, path: [...pathSoFar, seg] });
      }
    }
  };

  // Round 0: board near the origin. Walk the nearby stops nearest-first and keep the closest
  // MAX_CANDIDATES that actually have upcoming departures — a cluster of dead (zero-service)
  // stops must not crowd out a slightly-farther served stop, which is what made suburban
  // origins return no route. Bounded by CANDIDATE_PROBE_LIMIT so this stays API-cheap.
  let reached: Reached[] = [];
  const round0: { dep: DepartureOption; cand: StopCandidate; walkMeters: number }[] = [];
  let served = 0;
  let probes = 0;
  for (const cand of originStops) {
    if (served >= MAX_CANDIDATES || probes >= CANDIDATE_PROBE_LIMIT) break;
    probes++;
    const walkMeters = metersBetween(req.from.lat, req.from.lng, cand.lat, cand.lng);
    const deps = await departuresFor(source, cand, departAtMs + walkSeconds(walkMeters) * 1000, nowMs);
    if (deps.length === 0) continue; // dead stop — don't spend a candidate slot on it
    served++;
    for (const dep of deps) round0.push({ dep, cand, walkMeters });
  }
  round0.sort((a, b) => a.dep.depUtcMs - b.dep.depUtcMs);
  for (const { dep, cand, walkMeters } of round0.slice(0, DEPS_PER_ROUND)) {
    await expand(dep, cand, [], { name: req.from.name, lat: req.from.lat, lng: req.from.lng }, walkMeters, reached);
  }

  // Transfer rounds.
  for (let round = 0; round < MAX_TRANSFERS; round++) {
    const ranked = reached.sort(
      (a, b) =>
        a.arrUtcMs + walkSeconds(metersBetween(a.stop.lat, a.stop.lng, req.to.lat, req.to.lng)) * 1000 -
        (b.arrUtcMs + walkSeconds(metersBetween(b.stop.lat, b.stop.lng, req.to.lat, req.to.lng)) * 1000),
    );
    // A single boarding corridor reaches many stops a block apart, which used to fill
    // the whole frontier with near-duplicate locations and crowd out the one stop that
    // actually connects to a different route. Keep only the best-ranked reach within
    // each FRONTIER_DEDUPE_M cluster before taking the top FRONTIER.
    const frontier: Reached[] = [];
    for (const r of ranked) {
      if (frontier.length >= FRONTIER) break;
      if (frontier.some((f) => metersBetween(f.stop.lat, f.stop.lng, r.stop.lat, r.stop.lng) < FRONTIER_DEDUPE_M)) continue;
      frontier.push(r);
    }
    reached = [];
    for (const [fi, r] of frontier.entries()) {
      const boardStops: { stop: StopCandidate; walkMeters: number }[] = [
        { stop: { key: r.stop.stopKey, name: r.stop.name, lat: r.stop.lat, lng: r.stop.lng }, walkMeters: 0 },
      ];
      if (fi < NEARBY_TRANSFER_FRONTIER) {
        const nearby = await source.stopsNear(r.stop.lat, r.stop.lng, TRANSFER_WALK_M, 10);
        for (const n of nearby) {
          if (n.key === r.stop.stopKey) continue;
          const d = metersBetween(r.stop.lat, r.stop.lng, n.lat, n.lng);
          if (d <= TRANSFER_WALK_M) boardStops.push({ stop: n, walkMeters: d });
        }
      }
      const options: { dep: DepartureOption; stop: StopCandidate; walkMeters: number }[] = [];
      for (const { stop, walkMeters } of boardStops) {
        const notBefore = r.arrUtcMs + TRANSFER_MIN_SEC * 1000 + walkSeconds(walkMeters) * 1000;
        const deps = await departuresFor(source, stop, notBefore, nowMs);
        for (const dep of deps) options.push({ dep, stop, walkMeters });
      }
      options.sort((a, b) => a.dep.depUtcMs - b.dep.depUtcMs);
      for (const { dep, stop, walkMeters } of options.slice(0, DEPS_PER_ROUND)) {
        await expand(dep, stop, r.path, { name: r.stop.name, lat: r.stop.lat, lng: r.stop.lng }, walkMeters, reached);
      }
    }
    if (reached.length === 0) break;
  }

  results.sort((a, b) => a.arrMs - b.arrMs || a.transfers - b.transfers);
  const seen = new Set<string>();
  const out: TripPlan[] = [];
  for (const r of results) {
    if (seen.has(r.routeKey)) continue;
    seen.add(r.routeKey);
    out.push(r.plan);
    if (out.length >= MAX_RESULTS) break;
  }
  return out;
}
