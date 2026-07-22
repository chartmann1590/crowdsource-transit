# CrowdTransit Router Spec v1

One algorithm, two implementations (TypeScript `web/src/routing/`, Kotlin
`android .../app/domain/routing/`), verified against the same fixture network
(`fixtures/network.json`). The router is pure: all I/O goes through a `GtfsDataSource`.

## GtfsDataSource interface

```
stopsNear(lat, lng, radiusM, limit) -> [StopCandidate{key, name, lat, lng}]
departures(stopKey, notBeforeUtcMs, windowSec) -> [DepartureOption]
tripDetails(routeOnestopId, tripIntId) -> TripDetails | null
```

- `stopKey` is whatever key the source's departures lookup accepts. For Transitland this
  is a onestop_id OR the integer stop id (as a string) — trip stop_times only expose
  integer ids + GTFS stop_ids, never onestop_ids.
- `DepartureOption`: route metadata (onestop_id, names, color, type→mode, agency),
  headsign, `tripIntId`, `boardStopSequence`, `depUtcMs`, `depLocal` (HH:MM:SS).
- `TripDetails`: GTFS `trip_id`, headsign, ordered `stopTimes[{seq, gtfsStopId, name,
  lat, lng, arrivalSec, departureSec}]` (seconds since local service-day midnight, may
  exceed 86400), optional `shape` as [lng,lat][] (strip any 3rd element).

## Transitland mapping (verified by live probe 2026-07-22)

- Departures: `GET /stops/{stop_key}/departures?relative_date=TODAY&next={sec}&limit=200
  &include_alerts=false`. Each departure carries `departure.scheduled_utc` (authoritative
  instant), `stop_sequence` (board position in the trip), `trip.id` (INTEGER — this, not
  the GTFS `trip_id`, is the trips path key), `trip.route.*`, `trip.shape.shape_id`.
- **Stale-feed guard**: expired feeds return ancient service dates (observed: 2018) even
  with `relative_date=TODAY`. Discard departures with `|scheduled_utc − now| > 36 h`.
- Trip details: `GET /routes/{route_onestop_id}/trips/{trip.id}?include_geometry=true`
  → `trips[0].stop_times[]` (`arrival_time`/`departure_time` local HH:MM:SS,
  `stop.{stop_id, stop_name, geometry.coordinates}`, `stop_sequence`) and
  `trips[0].shape.geometry` (GeoJSON LineString; points may be `[lng,lat,ele]`).
- UTC times for non-board stops: `boardDepUtcMs + (stopTimeSec − boardDepSec)·1000`
  (pure second arithmetic; correct across midnight and >24:00:00 stop_times).

## Search (bounded, RAPTOR-flavored)

Inputs: from{lat,lng,name}, to{lat,lng,name}, departAtMs (default now).
Constants: CANDIDATE_RADIUS_M=800, MAX_CANDIDATES=5, DEDUPE_M=25, WINDOW_SEC=7200,
DEPS_PER_ROUND=10, DEST_WALK_M=600, TRANSFER_MIN_SEC=120, TRANSFER_WALK_M=300,
FRONTIER=6, MAX_TRANSFERS=2, WALK_SPEED_MPS=1.33, WALK_DETOUR=1.3, MAX_RESULTS=3.

1. Origin/destination candidates: `stopsNear` ≤5 each within 800 m, deduped at 25 m.
2. Round 0: for each origin candidate, `departures(notBefore = departAt + straight-line
   walk time × detour)`. Keep earliest departure per (route onestop_id, headsign);
   merge all candidates' departures, sort by depUtcMs, cap at 10.
3. For each kept departure: `tripDetails`; on null/failure skip (frequency-based trips
   may 404 — never crash). Board index = stopTime with seq == boardStopSequence
   (fallback: nearest stopTime within 100 m of the candidate stop; else skip).
4. For every later stopTime: if within 600 m of the destination point → emit
   itinerary (walk + ride + walk). Also record (stopTime, arrivalUtcMs, trip context)
   into the reached set for the next round.
5. Rounds 1..2: prune reached stops to the 6 best by arrivalUtcMs + straight-line
   time-to-destination heuristic; skip stops already visited earlier-or-equal. For each
   frontier stop: `departures(notBefore = arrival + 120 s)` at the same stop; plus for
   the top 2 frontier stops, one `stopsNear(300 m)` to allow walk transfers (walk time
   added to notBefore). Repeat 3–4. A leg must board a different (route, headsign) than
   the leg it arrived on.
6. Rank all emitted itineraries by arrivalUtcMs, then transfer count; return ≤3 distinct
   (dedupe by sequence of route onestop_ids).
7. Post-process winners only: walking legs via ORS proxy `POST /walk` (coordinates
   [[lng,lat],…]); on failure keep straight-line estimate (distance × 1.3 at 1.33 m/s).
   Ride leg `stops` = the contiguous stopTimes slice board→alight (spec correctness
   rule); `shape_poly` = shape sliced between nearest shape points to board/alight,
   encoded polyline precision 5 (omit if no shape).
8. Abort the whole search (surface retryable error) on a 429/403 rate-limit signal.

## Caching (source-level, both platforms)

departures 5 min · tripDetails 24 h · stopsNear 10 min. Keyed by full request args.
In-memory only for v1.

## Fixture network

`fixtures/network.json` defines synthetic stops/departures/trips exercised by both
implementations' unit tests: (a) direct ride, (b) one transfer at same stop,
(c) wrong-direction trip that passes the destination *before* the board stop and must be
rejected, (d) stale departure (year 2018) that must be filtered out.
