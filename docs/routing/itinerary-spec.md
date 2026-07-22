# CrowdTransit Itinerary Format v1

The itinerary ("TripPlan") is the shared interchange format between the Android app
(Kotlin/Moshi) and the website (TypeScript). It is used for: UI rendering, saved trips
in Firebase RTDB, share links, plain-text export, and the live-navigation engine.

Both implementations MUST round-trip the golden fixtures in `fixtures/` byte-for-byte
at the *semantic* level (parsed-structure equality; JSON key order and whitespace are
not significant).

## JSON structure

```jsonc
{
  "v": 1,                          // format version, required
  "planned_at": "2026-07-22T15:00:00Z", // ISO-8601 UTC instant the plan was computed
  "from": { "name": "Home", "lat": 40.7484, "lng": -73.9857 },
  "to":   { "name": "Work", "lat": 40.7061, "lng": -74.0087 },
  "legs": [ /* one or more Leg objects, in travel order */ ]
}
```

### Place

```jsonc
{ "name": "string (may be empty)", "lat": 0.0, "lng": 0.0 }
```

### Walk leg (`t: "w"`)

```jsonc
{
  "t": "w",
  "from": Place,
  "to": Place,
  "dep": "2026-07-22T15:02:00Z",   // ISO-8601 UTC departure
  "arr": "2026-07-22T15:08:00Z",   // ISO-8601 UTC arrival
  "dist_m": 420,                    // integer metres
  "poly": "optional encoded polyline (precision 5)",
  "steps": [                        // optional street-level instructions (from ORS)
    { "text": "Head north on Main St", "dist_m": 120, "lat": 40.749, "lng": -73.988 }
  ]
}
```

Each step's `lat`/`lng` is the maneuver point (where that instruction applies), taken from
the walking route geometry — this lets renderers plot turn-by-turn markers along the
walking line on a map, not just list the steps as text.

### Transit leg (`t: "r"`)

```jsonc
{
  "t": "r",
  "mode": "bus",                    // bus | subway | train | tram | ferry | transit
  "route": {
    "onestop_id": "r-dr5r-m15",
    "short": "M15",                 // route_short_name (may be empty)
    "long": "East Side Express",    // route_long_name (may be empty)
    "color": "#00A862",             // normalized #RRGGBB
    "agency": "MTA New York City Transit"
  },
  "trip": { "trip_id": "AB123", "headsign": "South Ferry" },
  "board":  { "stop_id": "s-dr5ru-stop1", "name": "1 Av / E 34 St", "lat": 40.0, "lng": -73.0,
              "dep_utc": "2026-07-22T15:10:00Z" },
  "alight": { "stop_id": "s-dr5rs-stop9", "name": "Water St / Wall St", "lat": 40.0, "lng": -74.0,
              "arr_utc": "2026-07-22T15:31:00Z" },
  "stops": [                        // EVERY stop from board to alight inclusive, in trip order.
    { "id": "s-dr5ru-stop1", "name": "1 Av / E 34 St", "lat": 40.0, "lng": -73.0,
      "arr_utc": "2026-07-22T15:10:00Z" }
    // ... intermediate stops ...
  ],
  "shape_poly": "optional encoded polyline (precision 5) of the vehicle path board→alight"
}
```

Correctness rule: `stops` is a contiguous slice of the real GTFS trip's stop sequence
(`board` first, `alight` last). Renderers/navigation MUST derive the vehicle path from
`shape_poly` when present, else from straight segments between consecutive `stops`
entries — never from anything that isn't this trip's own data.

## Times

All instants are ISO-8601 UTC with `Z` suffix, second precision
(`YYYY-MM-DDTHH:MM:SSZ`). Total trip duration = last leg arrival − first leg departure.

## Polylines

Google encoded polyline format, precision 5 (lat/lng × 1e5).

## Wire encoding (share links, RTDB storage)

```
blob = base64url_no_padding( deflate_raw( minified_json_utf8 ) )
```

- deflate-raw = DEFLATE without zlib header/trailer (RFC 1951).
  - Web encode/decode: `CompressionStream('deflate-raw')` / `DecompressionStream('deflate-raw')`.
  - Android: `java.util.zip.Deflater(BEST_COMPRESSION, /*nowrap=*/true)` and
    `Inflater(/*nowrap=*/true)`.
- base64url per RFC 4648 §5, **no padding**. Android: `android.util.Base64` with
  `URL_SAFE | NO_WRAP | NO_PADDING`.
- Compressed bytes may differ between encoders; only decode(encode(x)) == x and the
  ability to decode the golden fixture blobs are required.

Share URL: `https://<host>/crowdsource-transit/trip#d=<blob>` (payload in the hash).

## Versioning

Decoders MUST reject payloads whose `v` is missing or greater than the highest version
they know, with a user-visible "update required / unsupported link" error, never a crash.
