import { TRIP_PLAN_VERSION, type TripPlan } from '../types/itinerary';

/**
 * Wire codec from docs/routing/itinerary-spec.md:
 * blob = base64url_no_padding(deflate_raw(minified_json_utf8))
 * Uses the native CompressionStream API (Chrome 103+/Firefox 113+/Safari 16.4+), no deps.
 */

export class UnsupportedTripPlanVersionError extends Error {
  readonly version: number | undefined;

  constructor(version: number | undefined) {
    super(`Unsupported trip plan version: ${version}`);
    this.name = 'UnsupportedTripPlanVersionError';
    this.version = version;
  }
}

function validate(plan: TripPlan): TripPlan {
  if (typeof plan.v !== 'number' || plan.v < 1 || plan.v > TRIP_PLAN_VERSION) {
    throw new UnsupportedTripPlanVersionError(plan.v);
  }
  return plan;
}

export function tripPlanFromJson(json: string): TripPlan {
  return validate(JSON.parse(json) as TripPlan);
}

async function pipeThrough(
  bytes: Uint8Array<ArrayBuffer>,
  stream: { readable: ReadableStream<Uint8Array>; writable: WritableStream<BufferSource> },
): Promise<Uint8Array> {
  const writer = stream.writable.getWriter();
  void writer.write(bytes);
  void writer.close();
  const chunks: Uint8Array[] = [];
  const reader = stream.readable.getReader();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
  }
  const out = new Uint8Array(chunks.reduce((n, c) => n + c.length, 0));
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

function toBase64Url(bytes: Uint8Array): string {
  let bin = '';
  for (let i = 0; i < bytes.length; i += 0x8000) {
    bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fromBase64Url(blob: string): Uint8Array<ArrayBuffer> {
  const b64 = blob.trim().replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

export async function encodeTripPlan(plan: TripPlan): Promise<string> {
  const json = new TextEncoder().encode(JSON.stringify(validate(plan)));
  const deflated = await pipeThrough(json, new CompressionStream('deflate-raw'));
  return toBase64Url(deflated);
}

export async function decodeTripPlan(blob: string): Promise<TripPlan> {
  const deflated = fromBase64Url(blob);
  const json = await pipeThrough(deflated, new DecompressionStream('deflate-raw'));
  return tripPlanFromJson(new TextDecoder().decode(json));
}
