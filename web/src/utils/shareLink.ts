import { isTransitLeg, type TripPlan } from '../types/itinerary';
import { decodePolyline, encodePolyline } from './polyline';
import { encodeTripPlan } from './tripCodec';

/**
 * Share-link building (docs/routing/itinerary-spec.md): payload rides in the URL hash
 * (#d=...) so it never reaches server logs, and must survive the GitHub Pages 404 shim.
 * Payloads are slimmed — walk polylines/steps dropped, shapes simplified — targeting
 * <2000 chars; shapes are dropped entirely if still over (stop-to-stop straight
 * segments still follow the true stop sequence).
 */

const TARGET_LINK_CHARS = 2000;
const SIMPLIFY_TOLERANCE_M = 20;

export function shareBaseUrl(): string {
  // e.g. https://chartmann1590.github.io/crowdsource-transit
  const base = import.meta.env.BASE_URL.replace(/\/$/, '');
  return `${window.location.origin}${base}`;
}

/** Perpendicular-distance polyline simplification (Douglas-Peucker), tolerance in ~metres. */
export function simplifyPoints(points: [number, number][], toleranceM: number): [number, number][] {
  if (points.length <= 2) return points;
  const degTolerance = toleranceM / 111_000; // rough metres→degrees at mid latitudes

  const keep = new Array<boolean>(points.length).fill(false);
  keep[0] = keep[points.length - 1] = true;

  const stack: [number, number][] = [[0, points.length - 1]];
  while (stack.length) {
    const [start, end] = stack.pop()!;
    let maxDist = 0;
    let maxIdx = -1;
    const [x1, y1] = points[start];
    const [x2, y2] = points[end];
    const dx = x2 - x1;
    const dy = y2 - y1;
    const lenSq = dx * dx + dy * dy;
    for (let i = start + 1; i < end; i++) {
      const [px, py] = points[i];
      let dist: number;
      if (lenSq === 0) {
        dist = Math.hypot(px - x1, py - y1);
      } else {
        const t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        dist = Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
      }
      if (dist > maxDist) {
        maxDist = dist;
        maxIdx = i;
      }
    }
    if (maxIdx >= 0 && maxDist > degTolerance) {
      keep[maxIdx] = true;
      stack.push([start, maxIdx], [maxIdx, end]);
    }
  }
  return points.filter((_, i) => keep[i]);
}

function slimPlan(plan: TripPlan, dropShapes: boolean): TripPlan {
  return {
    ...plan,
    legs: plan.legs.map((leg) => {
      if (isTransitLeg(leg)) {
        if (dropShapes || !leg.shape_poly) {
          const { shape_poly: _unused, ...rest } = leg;
          return rest;
        }
        return {
          ...leg,
          shape_poly: encodePolyline(simplifyPoints(decodePolyline(leg.shape_poly), SIMPLIFY_TOLERANCE_M)),
        };
      }
      const { poly: _p, steps: _s, ...rest } = leg;
      return rest;
    }),
  };
}

export async function buildShareUrl(plan: TripPlan): Promise<string> {
  let blob = await encodeTripPlan(slimPlan(plan, false));
  if (blob.length > TARGET_LINK_CHARS) {
    blob = await encodeTripPlan(slimPlan(plan, true));
  }
  return `${shareBaseUrl()}/trip#d=${blob}`;
}
