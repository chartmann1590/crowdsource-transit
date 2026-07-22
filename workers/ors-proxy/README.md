# CrowdTransit ORS proxy (Cloudflare Worker)

Proxies walking-directions requests to OpenRouteService so the ORS API key never ships
in the public APK/AAB (GitHub Releases) or the web bundle (GitHub Pages). The key exists
ONLY as a Cloudflare Worker secret.

## Endpoint

`POST /walk` with body `{"coordinates": [[lng, lat], [lng, lat], ...]}` (2–5 waypoints,
within a ~50 km box). Returns the ORS `foot-walking` GeoJSON directions response.

Browser calls must come from an origin listed in the `ALLOWED_ORIGINS` var in
`wrangler.jsonc`; requests without an Origin header (the Android app) are allowed.

## Setup (one time)

```sh
cd workers/ors-proxy
npm install
npx wrangler login
npx wrangler secret put ORS_API_KEY   # paste the key when prompted — never commit it
npm run deploy
```

The deployed URL (e.g. `https://crowdtransit-ors-proxy.<account>.workers.dev`) is public
and safe to hard-code in the app and website.

## Key hygiene

- NEVER put the ORS key in this repo, `wrangler.jsonc`, CI variables, or client code.
- If the key ever leaks, regenerate it at https://openrouteservice.org/dev/ and re-run
  `npx wrangler secret put ORS_API_KEY` — no client update needed (that's the point of
  the proxy).
