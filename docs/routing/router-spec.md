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

Inputs: from{lat,lng,name}, to{lat,lng,name}, departAtMs (default now) OR arriveByMs
(mutually exclusive).

### Arrive-by search

`planTrips` is a dispatcher: given `arriveByMs`, it repeatedly runs the depart-at search
with `departAtMs = arriveByMs − WINDOW_SEC·1000·(attempt+1)` for up to
ARRIVE_BY_MAX_ATTEMPTS=3 attempts, filtering each attempt's results to itineraries whose
final arrival is ≤ `arriveByMs`. The first attempt with any on-time results wins; among
those, the latest arrival ≤ target (least wait) is preferred. Bounded to cap Transitland
calls — a trip whose only itineraries take longer than 3·WINDOW_SEC (6 h) to reach the
target won't be found.

### Stop text search — location bias

Both platforms' free-text stop search (e.g. the trip planner's destination box) is biased
toward a known location when one is available (already-chosen origin/destination, or on
Android the last GPS fix): first try Transitland's `/stops?search=…&lat=…&lon=…&radius=50000`,
and only fall back to the unscoped `search`-only query if that returns nothing. Plain text
search matches stop names anywhere — e.g. "Schenectady" can match Brooklyn NYC's
"Schenectady Ave" stops ahead of the actual city of Schenectady, NY — so an unbiased search
can silently hand the router a wrong, distant destination that then correctly reports no
route.

Constants: DEFAULT_CANDIDATE_RADIUS_M=1600 (user-overridable via maxWalkToStopM),
CANDIDATE_PROBE_LIMIT=15, MAX_CANDIDATES=5, DEDUPE_M=25, WINDOW_SEC=7200,
DEPS_PER_ROUND=10, DEST_WALK_M=600, TRANSFER_MIN_SEC=120, TRANSFER_WALK_M=300,
FRONTIER=6, FRONTIER_DEDUPE_M=150, NEARBY_TRANSFER_FRONTIER=FRONTIER, MAX_TRANSFERS=2,
WALK_SPEED_MPS=1.33, WALK_DETOUR=1.3, MAX_RESULTS=3.

Origin candidates are **service-aware** (dead/zero-departure stops are skipped so they can't
crowd out a slightly-farther served stop) — see step 2. The radius defaults to 1600 m and is
user-settable (web planner selector / Android `UserPreferencesStore.maxWalkToStopM`).

### Frontier selection (transfer rounds) — dedup by distance, not just by stop

A single initial boarding often reaches dozens of stops along the same corridor (both
directions of travel, several nearby-but-distinct stop objects per intersection). Naively
taking the top FRONTIER=6 reached stops by arrival-time-plus-heuristic can fill the whole
shortlist with near-duplicate locations along that one corridor, starving out the one
stop that actually connects to a different route toward the destination — the search then
reports no route even though a real one exists. Fixed by: (1) when ranking reached stops
for a transfer round, skip any candidate within FRONTIER_DEDUPE_M of an already-kept
frontier entry, so the 6 slots span meaningfully different locations; (2) every frontier
stop (not just the top few) gets the nearby-stop walk-transfer check — a route's inbound
and outbound directions are frequently separate physical stop objects a few meters apart,
so the correct-direction boarding is often only reachable by that walk-transfer step, not
by re-boarding at the exact stop already reached.

1. Candidate radius = `req.maxWalkToStopM ?? DEFAULT_CANDIDATE_RADIUS_M` (default 1600 m;
   user-overridable). `nearbyStopsSorted` returns all stops within the radius, distance-sorted
   nearest-first and deduped at 25 m (NOT truncated, NOT service-filtered). The destination
   list is used only as a non-empty reachability guard — `tryEmit` matches the destination
   point directly (step 4), so destination candidate *quality* never matters.
2. Round 0 — **service-aware origin candidates**: walk the sorted origin stops nearest-first;
   for each, `departures(notBefore = departAt + straight-line walk × detour)`; **skip stops
   with zero upcoming departures** (a cluster of dead/discontinued stops must not consume the
   candidate budget — this is what made suburban origins whose nearest *served* stop sits just
   past the radius return no route). Stop after `MAX_CANDIDATES` (5) served stops **or**
   `CANDIDATE_PROBE_LIMIT` (15) probes, whichever first — the cap bounds API cost even at a
   large user-set radius; cached departures make the round-0 probe free. Keep earliest
   departure per (route onestop_id, headsign); merge, sort by depUtcMs, cap at 10.
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
