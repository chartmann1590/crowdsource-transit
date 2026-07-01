# Phase 4b: Live Transit Data via Transitland (replaces Firebase-hosted GTFS)

> Supersedes the "import every GTFS feed into Firebase" idea from Phase 4. Firebase Spark
> free tier (1GB storage / 10GB month transfer, no billing allowed per project constraints)
> cannot hold nationwide GTFS data. Instead, stop/route lookups are served live from
> Transitland's free public API, and Firebase is used only for user-generated content
> (ratings, comments, crowdsourced stops, favorites) keyed against Transitland's stable
> `onestop_id`.

## Why

- The original Phase 4 import (`scripts/import-all.js`) only covers 10 priority agencies.
  A real user anywhere outside those 10 metros sees "no stops found" even though transit
  exists there.
- Importing all ~2000+ US GTFS feeds into Firebase RTDB would blow past the Spark free
  tier and force enabling Blaze billing, which conflicts with the project's "Spark free
  tier ONLY" rule.
- Transitland (transit.land, by Interline) hosts a continuously-updated aggregation of
  nearly all US (and many international) GTFS feeds behind a free REST API. No hosting
  cost to us; always current; no import pipeline to maintain.

## Transitland API (researched facts, not assumptions)

- Base URL: `https://api.transit.land`
- Endpoint: `GET /api/v2/rest/stops`
  - `lat`, `lon`, `radius` (meters, max 10,000) — nearby query, all three required together
  - `search` — full text search on stop name
  - `onestop_id`, `stop_id` — direct lookup
  - `bbox` — `min_lon,min_lat,max_lon,max_lat`
  - `limit`, `after` — pagination
  - `format` — `json` (default), `geojson`, `geojsonl`
- Auth: `apikey` query param or `apikey` header. **Requires a free Interline account —
  sign-up is manual (email verification), cannot be automated.** User must sign up at
  transit.land and provide the key; it goes in `android/local.properties` as
  `transitland.apiKey=...` (gitignored), exposed to Kotlin via `BuildConfig.TRANSITLAND_API_KEY`.
- Response stop object: `id`, `stop_id`, `stop_name`, `stop_code`, `stop_desc`, `onestop_id`,
  `geometry` (GeoJSON Point, `[lon, lat]`), `place.adm0_name`/`adm1_name` (country/state —
  no city field), `feed_version`, `level`, `parent`, `alerts[]`.
  - **No `served_by_route_types` in the default stop response, and no working query
    parameter to fetch "routes serving this specific stop" either** (confirmed against
    the live `/routes` endpoint docs — its stop-related filters only go the other
    direction: filter *stops* by route/agency, not routes by stop). Rather than guess
    with an unreliable proximity-based workaround, Transitland-sourced stops show a
    single generic "Transit" badge instead of per-mode Bus/Train/Subway/Ferry badges.
    Locally crowdsourced stops (which store `transitTypes` directly, user-entered) keep
    their accurate badges.
  - No city name in the default schema; `place.adm1_name` (state/province) is available.
    City will be left blank or backfilled from the nearest agency's known metro until/unless
    a geocoding step is added later.

## Architecture Change

**Before:** `StopRepository` reads canonical stop data (name, lat/lng, ratings, comment
counts) all from Firebase `/stops/{stopId}`, populated by a one-time GTFS import script.

**After:**
- Canonical stop data (name, location, GTFS identifiers) comes live from Transitland,
  keyed by `onestop_id` (stable, used as our `stopId` everywhere downstream).
- Community data (rating sum/count, comment count) moves to a new Firebase node,
  `stopStats/{onestop_id}`, since we no longer own `/stops` as the source of truth.
  `RatingRepository`/`CommentRepository` already take `targetId` generically — only the
  aggregate-counter transaction target path changes from `stops/$targetId` to
  `stopStats/$targetId`.
- `crowdsourced/` (user-submitted new stops) is untouched — those are genuinely
  user-authored and still live fully in Firebase.
- GeoFire (`geofire/stops`) is no longer needed for stop discovery once Transitland's
  own `lat/lon/radius` query replaces it, and can eventually be removed from the import
  scripts. Not deleted this phase to avoid touching the working import pipeline further.

## Files Touched

- [ ] `android/app/build.gradle.kts` — add Retrofit, Moshi, OkHttp logging interceptor,
      Room (for offline cache), read `transitland.apiKey` from `local.properties` into
      `BuildConfig.TRANSITLAND_API_KEY`
- [ ] `android/local.properties` — add empty `transitland.apiKey=` placeholder (gitignored)
- [ ] `android/app/src/main/java/.../data/remote/TransitlandApi.kt` — Retrofit interface
- [ ] `android/app/src/main/java/.../data/remote/TransitlandDtos.kt` — Moshi response models
- [ ] `android/app/src/main/java/.../data/remote/TransitlandMapper.kt` — DTO → `Stop` mapping
- [ ] `android/app/src/main/java/.../di/NetworkModule.kt` — Retrofit/OkHttp/Moshi Hilt providers
- [ ] `android/app/src/main/java/.../data/repository/StopRepository.kt` — rewrite
      `getStopsNearby`/`getStop` to call Transitland + overlay `stopStats` from Firebase
- [ ] `android/app/src/main/java/.../ui/screens/search/SearchViewModel.kt` — call
      Transitland `search` param instead of Firebase `orderByChild("name")`
- [ ] `android/app/src/main/java/.../data/repository/RatingRepository.kt` — target path
      `stops/$targetId` → `stopStats/$targetId`
- [ ] `android/app/src/main/java/.../data/repository/CommentRepository.kt` — same path change
- [ ] `firebase/database.rules.json` — add `stopStats` node (public read, transaction-only
      write for the three counters, same shape as the old `stops/$stopId` counter rules);
      `stops` node's `.indexOn`/counter rules can stay (still used by crowdsourced/legacy
      imported data) but are no longer the primary path for search/nearby
- [ ] Offline download (fast-follow within this phase, not blocking the live-query fix):
      - [ ] Room `entities/CachedStop.kt`, `entities/CachedAgency.kt`
      - [ ] Room `dao/OfflineStopDao.kt`, `AppDatabase.kt`
      - [ ] `data/repository/OfflineRepository.kt` — download all stops for a chosen
            agency (`served_by_onestop_ids`) page by page via Transitland, store in Room
      - [ ] UI: a "Download for offline" action (Profile screen or a dedicated
            `DownloadsScreen`) to pick an agency and trigger the download, with progress
      - [ ] `StopRepository` falls back to Room when Transitland is unreachable
            (no network) and a cached copy exists

## Data Migration Note

Existing rating/comment aggregate counters written under the old `stops/{stopId}` path
(from the 10 already-imported agencies) will NOT automatically appear under
`stopStats/{onestop_id}`, because the previously-imported stop IDs (e.g. `cta_9962`) are
our own synthetic IDs, not Transitland `onestop_id`s. This is a clean break, not a
migration — ratings/comments submitted against the old synthetic IDs during this session's
testing are effectively orphaned. Acceptable since the app has no real users yet.

## Verification

1. Search "Kedzie" → results come back from Transitland (Chicago stops), not the old
   Firebase-imported dataset.
2. Nearby stops at the test device's real location (Schenectady, NY) → returns real
   CDTA-area stops (previously impossible, since CDTA was never imported).
3. Submitting a rating on a Transitland-sourced stop writes to `stopStats/{onestop_id}`
   and the aggregate updates correctly.
4. Airplane mode + a previously-downloaded agency → nearby/search still returns cached
   Room data for that agency.
