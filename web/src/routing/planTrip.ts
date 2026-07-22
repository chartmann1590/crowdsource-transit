import { fetchWalkRoute } from '../api/ors';
import type { TripPlan } from '../types/itinerary';
import { isTransitLeg, isWalkLeg } from '../types/itinerary';
import type { GtfsDataSource } from './dataSource';
import { planTrips } from './router';
import { TransitlandDataSource } from './transitlandSource';
import type { PlanRequest } from './types';

/**
 * Orchestrator: run the pure router, then refine the winning itineraries' walking legs
 * through the ORS proxy (street-following polylines + step instructions). ORS failures
 * leave the straight-line estimates in place — the plan always succeeds if the router did.
 */

const sharedSource = new TransitlandDataSource();

function toIso(ms: number): string {
  return new Date(Math.round(ms / 1000) * 1000).toISOString().replace(/\.\d{3}Z$/, 'Z');
}

async function refineWalkLegs(plan: TripPlan): Promise<void> {
  for (let i = 0; i < plan.legs.length; i++) {
    const leg = plan.legs[i];
    if (!isWalkLeg(leg)) continue;
    const walk = await fetchWalkRoute([leg.from, leg.to]);
    if (!walk) continue;
    leg.dist_m = walk.distanceM;
    leg.poly = walk.poly;
    if (walk.steps.length > 0) leg.steps = walk.steps;

    const next = plan.legs[i + 1];
    const prev = plan.legs[i - 1];
    if (next && isTransitLeg(next)) {
      // Walk that ends at a boarding: arrive when the vehicle departs, leave just in time.
      const boardMs = Date.parse(next.board.dep_utc);
      leg.arr = toIso(boardMs);
      leg.dep = toIso(boardMs - walk.durationSec * 1000);
    } else if (prev && isTransitLeg(prev)) {
      // Final walk: start when the vehicle arrives.
      const alightMs = Date.parse(prev.alight.arr_utc);
      leg.dep = toIso(alightMs);
      leg.arr = toIso(alightMs + walk.durationSec * 1000);
    }
  }
}

export async function planTrip(req: PlanRequest, source: GtfsDataSource = sharedSource): Promise<TripPlan[]> {
  const plans = await planTrips(source, req);
  await Promise.all(plans.map(refineWalkLegs));
  return plans;
}
