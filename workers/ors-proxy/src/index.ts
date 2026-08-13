/**
 * CrowdTransit walking-directions + geocoding proxy, plus the "Report a Problem"
 * GitHub feedback relay (POST/GET /feedback/*).
 *
 * Keeps API keys out of the (public) APK and web bundle: clients call this Worker's
 * public URL and the keys live only as Worker secrets. Forwards POST /walk to ORS
 * foot-walking GeoJSON directions, POST /geocode to ORS's Pelias geocode search, and
 * /feedback/* to the GitHub REST API, all with request validation so the endpoints
 * can't be used as a general relay to their upstream.
 *
 * /feedback/* was previously implemented by embedding a GitHub personal access token
 * directly in the Android app (BuildConfig.GITHUB_API_TOKEN, sent as a client-side
 * Bearer header) so the app could call api.github.com to create issues/comments and
 * push screenshots via the Contents API. That token would have had issue-write and
 * repo-content-write scope — extractable from any downloaded APK/AAB in seconds (the
 * exact `strings`-on-the-dex technique used to verify the AdMob IDs in this app's
 * release build), handing every installer of the app write access to this repository.
 * It never shipped with a real value. Moved here instead: the token lives only as a
 * Worker secret, and the client sends plain issue/comment content with no credential
 * attached, same as the /walk and /geocode design just above.
 *
 * Trade-off worth knowing: like /walk and /geocode, these endpoints are unauthenticated
 * (a mobile client has no secret it could present that isn't just as extractable as the
 * PAT this replaces). That's an open write surface for issue/comment spam and pushes
 * to GITHUB_ASSETS_DIR — much lower severity than a leaked repo-write credential
 * (spam is noise you can lock/delete; a leaked PAT is a compromised repository), but
 * real. If it becomes a problem, add a Cloudflare Rate Limiting rule on this route from
 * the dashboard — no code change needed.
 */

interface Env {
  ORS_API_KEY: string;
  ALLOWED_ORIGINS: string;
  GITHUB_TOKEN: string;
}

const GITHUB_OWNER = 'chartmann1590';
const GITHUB_REPO = 'crowdsource-transit';
const GITHUB_ASSETS_DIR = 'feedback-assets';
const GITHUB_API = 'https://api.github.com';
const MAX_TITLE_LENGTH = 200;
const MAX_BODY_LENGTH = 8_000;
// ~5 MB of raw image bytes, base64-encoded (base64 runs ~1.33x the raw size).
const MAX_IMAGE_BASE64_LENGTH = 7_000_000;

interface CreateIssueRequest {
  title: string;
  body: string;
}

interface PostCommentRequest {
  body: string;
}

interface UploadImageRequest {
  filename: string;
  contentBase64: string;
}

function githubHeaders(env: Env): HeadersInit {
  return {
    Authorization: `Bearer ${env.GITHUB_TOKEN}`,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'CrowdTransit-ors-proxy',
    'Content-Type': 'application/json',
  };
}

async function proxyGithub(
  path: string,
  env: Env,
  origin: string | null,
  init?: RequestInit,
): Promise<Response> {
  try {
    const ghResponse = await fetch(`${GITHUB_API}${path}`, {
      ...init,
      headers: { ...githubHeaders(env), ...(init?.headers ?? {}) },
    });
    return new Response(ghResponse.body, {
      status: ghResponse.status,
      headers: {
        'Content-Type': ghResponse.headers.get('Content-Type') ?? 'application/json',
        'Cache-Control': 'no-store',
        ...corsHeaders(origin, env),
      },
    });
  } catch (err) {
    console.log(JSON.stringify({ event: 'github_fetch_failed', path, errorType: err instanceof Error ? err.name : 'unknown' }));
    return errorResponse(502, 'GitHub is unavailable', origin, env);
  }
}

function validateCreateIssueRequest(body: unknown): CreateIssueRequest | null {
  if (typeof body !== 'object' || body === null) return null;
  const title = (body as { title?: unknown }).title;
  const text = (body as { body?: unknown }).body;
  if (typeof title !== 'string' || typeof text !== 'string') return null;
  const trimmedTitle = title.trim();
  if (trimmedTitle.length === 0 || trimmedTitle.length > MAX_TITLE_LENGTH) return null;
  if (text.length === 0 || text.length > MAX_BODY_LENGTH) return null;
  return { title: trimmedTitle, body: text };
}

function validatePostCommentRequest(body: unknown): PostCommentRequest | null {
  if (typeof body !== 'object' || body === null) return null;
  const text = (body as { body?: unknown }).body;
  if (typeof text !== 'string' || text.length === 0 || text.length > MAX_BODY_LENGTH) return null;
  return { body: text };
}

function validateUploadImageRequest(body: unknown): UploadImageRequest | null {
  if (typeof body !== 'object' || body === null) return null;
  const filename = (body as { filename?: unknown }).filename;
  const contentBase64 = (body as { contentBase64?: unknown }).contentBase64;
  if (typeof filename !== 'string' || typeof contentBase64 !== 'string') return null;
  // Basename only — no path separators, so the client can never write outside
  // GITHUB_ASSETS_DIR regardless of what it sends.
  if (!/^[a-zA-Z0-9_.-]{1,120}$/.test(filename)) return null;
  if (contentBase64.length === 0 || contentBase64.length > MAX_IMAGE_BASE64_LENGTH) return null;
  return { filename, contentBase64 };
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

    if (url.pathname === '/feedback/issue' && request.method === 'POST') {
      let issueReq: CreateIssueRequest | null = null;
      try {
        issueReq = validateCreateIssueRequest(await request.json());
      } catch {
        issueReq = null;
      }
      if (!issueReq) {
        return errorResponse(400, 'expected {title: string, body: string}', origin, env);
      }
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues`, env, origin, {
        method: 'POST',
        body: JSON.stringify(issueReq),
      });
    }

    const issueMatch = url.pathname.match(/^\/feedback\/issue\/(\d+)$/);
    if (issueMatch && request.method === 'GET') {
      const issueNumber = issueMatch[1];
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}`, env, origin);
    }

    const commentsMatch = url.pathname.match(/^\/feedback\/issue\/(\d+)\/comments$/);
    if (commentsMatch && request.method === 'GET') {
      const issueNumber = commentsMatch[1];
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}/comments`, env, origin);
    }
    if (commentsMatch && request.method === 'POST') {
      const issueNumber = commentsMatch[1];
      let commentReq: PostCommentRequest | null = null;
      try {
        commentReq = validatePostCommentRequest(await request.json());
      } catch {
        commentReq = null;
      }
      if (!commentReq) {
        return errorResponse(400, 'expected {body: string}', origin, env);
      }
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueNumber}/comments`, env, origin, {
        method: 'POST',
        body: JSON.stringify(commentReq),
      });
    }

    if (url.pathname === '/feedback/upload-image' && request.method === 'POST') {
      let uploadReq: UploadImageRequest | null = null;
      try {
        uploadReq = validateUploadImageRequest(await request.json());
      } catch {
        uploadReq = null;
      }
      if (!uploadReq) {
        return errorResponse(400, 'expected {filename: string, contentBase64: string}', origin, env);
      }
      const path = `${GITHUB_ASSETS_DIR}/${uploadReq.filename}`;
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/contents/${path}`, env, origin, {
        method: 'PUT',
        body: JSON.stringify({ message: `Upload ${uploadReq.filename}`, content: uploadReq.contentBase64 }),
      });
    }

    return errorResponse(404, 'not found', origin, env);
  },
} satisfies ExportedHandler<Env>;
