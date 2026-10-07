import type { TripPlan } from '../types/itinerary';
import { encodePolyline } from '../utils/polyline';

/** Walk → bus → walk itinerary used by the display/text tests. */
export function makePlan(): TripPlan {
  return {
    v: 1,
    planned_at: '2026-10-07T13:55:00Z',
    from: { name: 'Home', lat: 40.7, lng: -74.0 },
    to: { name: 'Office', lat: 40.75, lng: -73.98 },
    legs: [
      {
        t: 'w',
        from: { name: 'Home', lat: 40.7, lng: -74.0 },
        to: { name: '1st Ave', lat: 40.701, lng: -73.999 },
        dep: '2026-10-07T14:00:00Z',
        arr: '2026-10-07T14:05:00Z',
        dist_m: 400,
        steps: [
          { text: 'Head north', dist_m: 200, lat: 40.7005, lng: -73.9995 },
          { text: 'Turn right', dist_m: 200, lat: 40.701, lng: -73.999 },
        ],
      },
      {
        t: 'r',
        mode: 'bus',
        route: { onestop_id: 'r-dr5r-m15', short: 'M15', long: '1st Ave', color: '#123456', agency: 'MTA' },
        trip: { trip_id: 't1', headsign: 'Harlem' },
        board: { stop_id: 's1', name: '1st Ave', lat: 40.701, lng: -73.999, dep_utc: '2026-10-07T14:05:00Z' },
        alight: { stop_id: 's3', name: '42nd St', lat: 40.749, lng: -73.981, arr_utc: '2026-10-07T14:40:00Z' },
        stops: [
          { id: 's1', name: '1st Ave', lat: 40.701, lng: -73.999, arr_utc: '2026-10-07T14:05:00Z' },
          { id: 's2', name: '23rd St', lat: 40.73, lng: -73.99, arr_utc: '2026-10-07T14:20:00Z' },
          { id: 's3', name: '42nd St', lat: 40.749, lng: -73.981, arr_utc: '2026-10-07T14:40:00Z' },
        ],
        shape_poly: encodePolyline([
          [-73.999, 40.701],
          [-73.99, 40.73],
          [-73.981, 40.749],
        ]),
      },
      {
        t: 'w',
        from: { name: '42nd St', lat: 40.749, lng: -73.981 },
        to: { name: 'Office', lat: 40.75, lng: -73.98 },
        dep: '2026-10-07T14:40:00Z',
        arr: '2026-10-07T15:10:00Z',
        dist_m: 150,
      },
    ],
  };
}
