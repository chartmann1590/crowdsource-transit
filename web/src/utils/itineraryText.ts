import { isTransitLeg, type TripPlan } from '../types/itinerary';
import { formatLocalTime, planTimes } from './itineraryDisplay';
import { formatDistance, getDistanceUnit } from './units';

/**
 * Human-readable itinerary export for pasting into any app. Numbered steps + the share
 * link (passed in, since building it is async).
 */
export function itineraryToText(plan: TripPlan, shareUrl?: string): string {
  const { dep, arr, duration } = planTimes(plan);
  const lines: string[] = [];
  lines.push(`${plan.from.name || 'Origin'} → ${plan.to.name || 'Destination'}`);
  lines.push(`${dep} – ${arr} (${duration})`);
  lines.push('');

  let step = 1;
  for (const leg of plan.legs) {
    if (isTransitLeg(leg)) {
      const label = leg.route.short || leg.route.long || leg.mode;
      const rideStops = leg.stops.length - 1;
      lines.push(
        `${step}. Take ${leg.mode} ${label} toward ${leg.trip.headsign} from ${leg.board.name} ` +
          `(departs ${formatLocalTime(leg.board.dep_utc)})`,
      );
      lines.push(
        `   Ride ${rideStops} stop${rideStops === 1 ? '' : 's'}, get off at ${leg.alight.name} ` +
          `(arrives ${formatLocalTime(leg.alight.arr_utc)})`,
      );
    } else {
      lines.push(
        `${step}. Walk ${formatDistance(leg.dist_m, getDistanceUnit())} to ${leg.to.name || 'your destination'} ` +
          `(${formatLocalTime(leg.dep)}–${formatLocalTime(leg.arr)})`,
      );
    }
    step++;
  }

  if (shareUrl) {
    lines.push('');
    lines.push(`View this trip: ${shareUrl}`);
  }
  lines.push('');
  lines.push('Planned with CrowdTransit');
  return lines.join('\n');
}
