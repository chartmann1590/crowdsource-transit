/**
 * CrowdTransit walking-directions + geocoding proxy.
 *
 * Keeps the OpenRouteService API key out of the (public) APK and web bundle: clients call
 * this Worker's public URL and the key lives only as a Worker secret. Forwards
 * POST /walk to ORS foot-walking GeoJSON directions, and POST /geocode to ORS's Pelias
 * geocode search, both with request validation so the endpoint can't be used as a
 * general ORS relay.
 */

interface Env {
  ORS_API_KEY: string;
  ALLOWED_ORIGINS: string;
}

const ORS_URL = 'https://api.openrouteservice.org/v2/directions/foot-walking/geojson';
const ORS_GEOCODE_URL = 'https://api.openrouteservice.org/geocode/search';
// Walking legs are short (origin→stop, transfer, stop→destination); reject anything that
// isn't a plausible walking-leg request.
const MAX_WAYPOINTS = 5;
const MAX_LEG_DEGREES = 0.5; // ~50 km bounding box — far beyond any sane walking leg
const MAX_GEOCODE_TEXT_LENGTH = 200;

interface WalkRequest {
  coordinates: [number, number][]; // [lng, lat] pairs, in travel order
}

interface GeocodeRequest {
  text: string;
}

function corsHeaders(origin: string | null, env: Env): HeadersInit {
  const allowed = env.ALLOWED_ORIGINS.split(',').map((o) => o.trim());
  const headers: Record<string, string> = {
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Max-Age': '86400',
  };
  if (origin && allowed.includes(origin)) {
    headers['Access-Control-Allow-Origin'] = origin;
    headers['Vary'] = 'Origin';
  }
  return headers;
}

function validateGeocodeRequest(body: unknown): GeocodeRequest | null {
  if (typeof body !== 'object' || body === null) return null;
  const text = (body as { text?: unknown }).text;
  if (typeof text !== 'string') return null;
  const trimmed = text.trim();
  if (trimmed.length === 0 || trimmed.length > MAX_GEOCODE_TEXT_LENGTH) return null;
  return { text: trimmed };
}

function errorResponse(status: number, message: string, origin: string | null, env: Env): Response {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { 'Content-Type': 'application/json', ...corsHeaders(origin, env) },
  });
}

function validateWalkRequest(body: unknown): WalkRequest | null {
  if (typeof body !== 'object' || body === null) return null;
  const coords = (body as { coordinates?: unknown }).coordinates;
  if (!Array.isArray(coords) || coords.length < 2 || coords.length > MAX_WAYPOINTS) return null;
  for (const pair of coords) {
    if (
      !Array.isArray(pair) ||
      pair.length !== 2 ||
      typeof pair[0] !== 'number' ||
      typeof pair[1] !== 'number' ||
      pair[0] < -180 || pair[0] > 180 ||
      pair[1] < -90 || pair[1] > 90
    ) {
      return null;
    }
  }
  const lngs = coords.map((c) => c[0] as number);
  const lats = coords.map((c) => c[1] as number);
  if (
    Math.max(...lngs) - Math.min(...lngs) > MAX_LEG_DEGREES ||
    Math.max(...lats) - Math.min(...lats) > MAX_LEG_DEGREES
  ) {
    return null;
  }
  return { coordinates: coords as [number, number][] };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const origin = request.headers.get('Origin');
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(origin, env) });
    }
    // Browsers must come from an allowed origin; the Android app sends no Origin header.
    if (origin && !env.ALLOWED_ORIGINS.split(',').map((o) => o.trim()).includes(origin)) {
      return errorResponse(403, 'origin not allowed', origin, env);
    }

    if (url.pathname === '/walk' && request.method === 'POST') {
      let walk: WalkRequest | null = null;
      try {
        walk = validateWalkRequest(await request.json());
      } catch {
        walk = null;
      }
      if (!walk) {
        return errorResponse(400, 'expected {coordinates: [[lng,lat], ...]} within walking range', origin, env);
      }

      try {
        const orsResponse = await fetch(ORS_URL, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: env.ORS_API_KEY,
          },
          body: JSON.stringify({ coordinates: walk.coordinates, instructions: true, units: 'm' }),
        });
        // Stream the ORS body straight through; never buffer it here.
        return new Response(orsResponse.body, {
          status: orsResponse.status,
          headers: {
            'Content-Type': orsResponse.headers.get('Content-Type') ?? 'application/json',
            'Cache-Control': 'no-store',
            ...corsHeaders(origin, env),
          },
        });
      } catch (err) {
        console.log(JSON.stringify({ event: 'ors_fetch_failed', message: String(err) }));
        return errorResponse(502, 'walking directions unavailable', origin, env);
      }
    }

    if (url.pathname === '/geocode' && request.method === 'POST') {
      let geocode: GeocodeRequest | null = null;
      try {
        geocode = validateGeocodeRequest(await request.json());
      } catch {
        geocode = null;
      }
      if (!geocode) {
        return errorResponse(400, 'expected {text: string}', origin, env);
      }

      try {
        // Authorization header, not an api_key query param: keeps the key out of the
        // request URL entirely, so it can never end up in Worker observability logs or
        // any other place that captures URLs — same pattern as the /walk handler below.
        const searchUrl = new URL(ORS_GEOCODE_URL);
        searchUrl.searchParams.set('text', geocode.text);
        searchUrl.searchParams.set('size', '3');
        const orsResponse = await fetch(searchUrl.toString(), {
          headers: { Authorization: env.ORS_API_KEY },
        });
        return new Response(orsResponse.body, {
          status: orsResponse.status,
          headers: {
            'Content-Type': orsResponse.headers.get('Content-Type') ?? 'application/json',
            'Cache-Control': 'no-store',
            ...corsHeaders(origin, env),
          },
        });
      } catch (err) {
        // Fixed message only — never interpolate the raw error, since a thrown fetch
        // error could in principle echo back request details (including the URL, which
        // no longer carries the key, but this stays a safe habit regardless).
        console.log(JSON.stringify({ event: 'ors_geocode_fetch_failed', errorType: err instanceof Error ? err.name : 'unknown' }));
        return errorResponse(502, 'geocoding unavailable', origin, env);
      }
    }

    return errorResponse(404, 'not found', origin, env);
  },
} satisfies ExportedHandler<Env>;
