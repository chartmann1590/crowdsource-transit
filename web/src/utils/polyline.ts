/**
 * Google encoded polyline, precision 5 (docs/routing/itinerary-spec.md).
 * Points are [lng, lat] pairs (GeoJSON order) at the API boundary.
 */

export function encodePolyline(points: [number, number][]): string {
  let out = '';
  let prevLat = 0;
  let prevLng = 0;
  for (const [lng, lat] of points) {
    const latE5 = Math.round(lat * 1e5);
    const lngE5 = Math.round(lng * 1e5);
    out += encodeSigned(latE5 - prevLat) + encodeSigned(lngE5 - prevLng);
    prevLat = latE5;
    prevLng = lngE5;
  }
  return out;
}

export function decodePolyline(encoded: string): [number, number][] {
  const points: [number, number][] = [];
  let index = 0;
  let lat = 0;
  let lng = 0;
  while (index < encoded.length) {
    const dLat = decodeSigned();
    const dLng = decodeSigned();
    lat += dLat;
    lng += dLng;
    points.push([lng / 1e5, lat / 1e5]);
  }
  return points;

  function decodeSigned(): number {
    let result = 0;
    let shift = 0;
    let byte: number;
    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    return result & 1 ? ~(result >> 1) : result >> 1;
  }
}

function encodeSigned(value: number): string {
  let v = value < 0 ? ~(value << 1) : value << 1;
  let out = '';
  while (v >= 0x20) {
    out += String.fromCharCode((0x20 | (v & 0x1f)) + 63);
    v >>= 5;
  }
  return out + String.fromCharCode(v + 63);
}
