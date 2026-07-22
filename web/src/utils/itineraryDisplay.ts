import type { MapPolyline } from '../components/Map/MapView';
import { isTransitLeg, type TripPlan } from '../types/itinerary';
import { decodePolyline } from './polyline';

export const WALK_COLOR = '#5B6472';

export function formatLocalTime(iso: string | undefined): string {
  if (!iso) return '--:--';
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) return '--:--';
  return new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function planTimes(plan: TripPlan): { dep: string; arr: string; duration: string } {
  const first = plan.legs[0];
  const last = plan.legs[plan.legs.length - 1];
  const depIso = first ? (isTransitLeg(first) ? first.board.dep_utc : first.dep) : undefined;
  const arrIso = last ? (isTransitLeg(last) ? last.alight.arr_utc : last.arr) : undefined;
  let duration = '';
  if (depIso && arrIso) {
    const minutes = Math.round((Date.parse(arrIso) - Date.parse(depIso)) / 60_000);
    duration = minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes} min`;
  }
  return { dep: formatLocalTime(depIso), arr: formatLocalTime(arrIso), duration };
}

/** Map polylines for a plan: solid route-colored transit shapes + dashed walk lines. */
export function planPolylines(plan: TripPlan): MapPolyline[] {
  const lines: MapPolyline[] = [];
  for (const leg of plan.legs) {
    if (isTransitLeg(leg)) {
      const points = leg.shape_poly
        ? decodePolyline(leg.shape_poly)
        : leg.stops.map((s) => [s.lng, s.lat] as [number, number]);
      if (points.length >= 2) lines.push({ points, color: leg.route.color || '#00A862', dashed: false });
    } else {
      const points = leg.poly
        ? decodePolyline(leg.poly)
        : ([
            [leg.from.lng, leg.from.lat],
            [leg.to.lng, leg.to.lat],
          ] as [number, number][]);
      if (points.length >= 2) lines.push({ points, color: WALK_COLOR, dashed: true });
    }
  }
  return lines;
}
