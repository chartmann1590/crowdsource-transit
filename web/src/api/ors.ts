import type { WalkStep } from '../types/itinerary';
import { encodePolyline } from '../utils/polyline';

/**
 * Walking directions via the CrowdTransit ORS proxy Worker (workers/ors-proxy).
 * The Worker holds the OpenRouteService key; this URL is public and safe to hard-code.
 */
const ORS_PROXY_URL = 'https://crowdtransit-ors-proxy.chartmann1590.workers.dev/walk';

export interface WalkRoute {
  distanceM: number;
  durationSec: number;
  /** Encoded polyline, precision 5. */
  poly: string;
  steps: WalkStep[];
}

interface OrsGeoJsonResponse {
  features?: {
    geometry?: { coordinates?: number[][] };
    properties?: {
      summary?: { distance?: number; duration?: number };
      segments?: {
        steps?: { instruction?: string; distance?: number }[];
      }[];
    };
  }[];
}

/** null on any failure — callers keep their straight-line estimate. */
export async function fetchWalkRoute(points: { lat: number; lng: number }[]): Promise<WalkRoute | null> {
  try {
    const res = await fetch(ORS_PROXY_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ coordinates: points.map((p) => [p.lng, p.lat]) }),
    });
    if (!res.ok) return null;
    const data: OrsGeoJsonResponse = await res.json();
    const feature = data.features?.[0];
    const coords = feature?.geometry?.coordinates;
    const summary = feature?.properties?.summary;
    if (!coords?.length || summary?.distance == null || summary?.duration == null) return null;
    const steps: WalkStep[] =
      feature?.properties?.segments?.flatMap(
        (seg) =>
          seg.steps
            ?.filter((s) => s.instruction)
            .map((s) => ({ text: s.instruction!, dist_m: Math.round(s.distance ?? 0) })) ?? [],
      ) ?? [];
    return {
      distanceM: Math.round(summary.distance),
      durationSec: Math.round(summary.duration),
      poly: encodePolyline(coords.map((c) => [c[0], c[1]] as [number, number])),
      steps,
    };
  } catch {
    return null;
  }
}
