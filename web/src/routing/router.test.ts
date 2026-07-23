import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { isTransitLeg, isWalkLeg, type TransitLeg } from '../types/itinerary';
import { haversineDistance } from '../utils/distance';
import type { GtfsDataSource } from './dataSource';
import { planTrips } from './router';
import type { DepartureOption, RouteMeta, StopCandidate, TripDetails } from './types';

const fixturesDir = join(dirname(fileURLToPath(import.meta.url)), '../../../docs/routing/fixtures');

interface NetworkFixture {
  stops: { key: string; name: string; lat: number; lng: number }[];
  routes: (RouteMeta & { onestopId: string })[];
  trips: {
    tripIntId: number;
    route: string;
    gtfsTripId: string;
    headsign: string;
    startOffsetMin: number;
    stops: { key: string; min: number }[];
  }[];
  staleDepartures: { stopKey: string; route: string; tripIntId: number; depUtc: string }[];
}

const network: NetworkFixture = JSON.parse(readFileSync(join(fixturesDir, 'network.json'), 'utf8'));

/** In-memory GtfsDataSource over network.json; trip times anchored to `baseMs`. */
class FixtureDataSource implements GtfsDataSource {
  readonly calls = { stopsNear: 0, departures: 0, tripDetails: 0 };
  private readonly baseMs: number;

  constructor(baseMs: number) {
    this.baseMs = baseMs;
  }

  private route(id: string): RouteMeta {
    const r = network.routes.find((r) => r.onestopId === id);
    if (!r) throw new Error(`unknown route ${id}`);
    return r;
  }

  private stop(key: string) {
    const s = network.stops.find((s) => s.key === key);
    if (!s) throw new Error(`unknown stop ${key}`);
    return s;
  }

  async stopsNear(lat: number, lng: number, radiusM: number, limit: number): Promise<StopCandidate[]> {
    this.calls.stopsNear++;
    return network.stops
      .filter((s) => haversineDistance(lat, lng, s.lat, s.lng) * 1000 <= radiusM)
      .slice(0, limit)
      .map((s) => ({ key: s.key, name: s.name, lat: s.lat, lng: s.lng }));
  }

  async departures(stopKey: string, _notBeforeUtcMs: number, _windowSec: number): Promise<DepartureOption[]> {
    this.calls.departures++;
    const out: DepartureOption[] = [];
    for (const trip of network.trips) {
      const idx = trip.stops.findIndex((s) => s.key === stopKey);
      if (idx < 0 || idx === trip.stops.length - 1) continue;
      out.push({
        route: this.route(trip.route),
        headsign: trip.headsign,
        tripIntId: trip.tripIntId,
        boardStopSequence: idx,
        depUtcMs: this.baseMs + (trip.startOffsetMin + trip.stops[idx].min) * 60_000,
      });
    }
    for (const stale of network.staleDepartures) {
      if (stale.stopKey !== stopKey) continue;
      out.push({
        route: this.route(stale.route),
        headsign: 'Ghost',
        tripIntId: stale.tripIntId,
        boardStopSequence: 0,
        depUtcMs: Date.parse(stale.depUtc),
      });
    }
    return out;
  }

  async tripDetails(_routeOnestopId: string, tripIntId: number): Promise<TripDetails | null> {
    this.calls.tripDetails++;
    const trip = network.trips.find((t) => t.tripIntId === tripIntId);
    if (!trip) return null; // stale/unknown trips are unfetchable, like expired feeds
    const daySec = 8 * 3600;
    return {
      gtfsTripId: trip.gtfsTripId,
      headsign: trip.headsign,
      stopTimes: trip.stops.map((s, i) => {
        const stop = this.stop(s.key);
        const sec = daySec + (trip.startOffsetMin + s.min) * 60;
        return {
          seq: i,
          gtfsStopId: s.key,
          stopKey: s.key,
          name: stop.name,
          lat: stop.lat,
          lng: stop.lng,
          arrivalSec: sec,
          departureSec: sec,
        };
      }),
    };
  }
}

const origin1 = { name: 'Origin', lat: 40.6996, lng: -74.0 };
const dest1 = { name: 'Destination', lat: 40.7154, lng: -74.0 };
const origin2 = { name: 'Origin2', lat: 40.7996, lng: -74.1 };
const dest2 = { name: 'Destination2', lat: 40.8204, lng: -74.1 };
// Pin the candidate radius so the synthetic geometry is deterministic and independent of the
// production DEFAULT_CANDIDATE_RADIUS_M (which is validated live, not against this fixture).
const RADIUS = 800;

describe('planTrips', () => {
  it('finds the direct ride with the full stop sequence', async () => {
    const base = Math.floor(Date.now() / 1000) * 1000;
    const source = new FixtureDataSource(base);
    const plans = await planTrips(source, { from: origin1, to: dest1, departAtMs: base, maxWalkToStopM: RADIUS });

    expect(plans.length).toBeGreaterThan(0);
    const plan = plans[0];
    const transit = plan.legs.filter(isTransitLeg);
    expect(transit).toHaveLength(1);
    const leg = transit[0] as TransitLeg;
    expect(leg.route.onestop_id).toBe('r-test-red');
    expect(leg.stops.map((s) => s.id)).toEqual(['A', 'B', 'C', 'D']);
    expect(leg.board.stop_id).toBe('A');
    expect(leg.alight.stop_id).toBe('D');
    // walk → ride → walk
    expect(plan.legs).toHaveLength(3);
    expect(isWalkLeg(plan.legs[0]) && isWalkLeg(plan.legs[2])).toBe(true);
    // ride timing: board at +12 min, alight at +27 min
    expect(Date.parse(leg.board.dep_utc)).toBe(base + 12 * 60_000);
    expect(Date.parse(leg.alight.arr_utc)).toBe(base + 27 * 60_000);
  });

  it('rejects the wrong-direction trip and the stale departure', async () => {
    const base = Math.floor(Date.now() / 1000) * 1000;
    const source = new FixtureDataSource(base);
    const plans = await planTrips(source, { from: origin1, to: dest1, departAtMs: base, maxWalkToStopM: RADIUS });

    for (const plan of plans) {
      for (const leg of plan.legs.filter(isTransitLeg)) {
        expect(leg.route.onestop_id).not.toBe('r-test-wrong');
        expect(leg.route.onestop_id).not.toBe('r-test-stale');
      }
    }
  });

  it('skips dead (zero-departure) stops so they cannot crowd out the served boarding stop', async () => {
    const base = Math.floor(Date.now() / 1000) * 1000;
    const source = new FixtureDataSource(base);
    // DEAD1..DEAD5 are the five nearest stops to origin1 and have no departures. Naive
    // nearest-5 selection would fill every candidate slot with them and find no route; the
    // service-aware selection must skip them and board the served stop A.
    const plans = await planTrips(source, { from: origin1, to: dest1, departAtMs: base, maxWalkToStopM: RADIUS });

    expect(plans.length).toBeGreaterThan(0);
    const leg = plans[0].legs.filter(isTransitLeg)[0] as TransitLeg;
    expect(leg.board.stop_id).toBe('A');
    expect(leg.route.onestop_id).toBe('r-test-red');
  });

  it('finds the one-transfer itinerary via the shared stop', async () => {
    const base = Math.floor(Date.now() / 1000) * 1000;
    const source = new FixtureDataSource(base);
    const plans = await planTrips(source, { from: origin2, to: dest2, departAtMs: base, maxWalkToStopM: RADIUS });

    expect(plans.length).toBeGreaterThan(0);
    const transit = plans[0].legs.filter(isTransitLeg);
    expect(transit.map((l) => l.route.onestop_id)).toEqual(['r-test-green', 'r-test-blue']);
    // Transfer respects the 120 s minimum: green arrives X at +15, blue departs X at +20.
    expect(Date.parse(transit[1].board.dep_utc) - Date.parse(transit[0].alight.arr_utc)).toBeGreaterThanOrEqual(120_000);
    // legs: walk, ride, walk(transfer, zero-distance same stop), ride, walk
    expect(plans[0].legs).toHaveLength(5);
  });

  it('arrive-by search finds an itinerary landing at or before the target', async () => {
    const base = Math.floor(Date.now() / 1000) * 1000;
    const source = new FixtureDataSource(base);
    // Direct ride boards at +12 min, alights at +27 min (see the earlier direct-ride test).
    const arriveByMs = base + 30 * 60_000;
    const plans = await planTrips(source, { from: origin1, to: dest1, arriveByMs, maxWalkToStopM: RADIUS });

    expect(plans.length).toBeGreaterThan(0);
    for (const plan of plans) {
      const lastLeg = plan.legs[plan.legs.length - 1];
      expect(Date.parse((lastLeg as { arr: string }).arr)).toBeLessThanOrEqual(arriveByMs);
    }
  });

  it('arrive-by search finds nothing when even the earliest ride misses the target', async () => {
    const base = Math.floor(Date.now() / 1000) * 1000;
    const source = new FixtureDataSource(base);
    // The direct ride can't alight before +27 min, so a target before boarding is unreachable
    // within the bounded lookback attempts.
    const plans = await planTrips(source, { from: origin1, to: dest1, arriveByMs: base + 5 * 60_000, maxWalkToStopM: RADIUS });
    expect(plans).toEqual([]);
  });

  it('returns no plans when no stops are near the origin', async () => {
    const base = Math.floor(Date.now() / 1000) * 1000;
    const source = new FixtureDataSource(base);
    const plans = await planTrips(source, {
      from: { name: 'Nowhere', lat: 10, lng: 10 },
      to: dest1,
      departAtMs: base,
      maxWalkToStopM: RADIUS,
    });
    expect(plans).toEqual([]);
  });
});
