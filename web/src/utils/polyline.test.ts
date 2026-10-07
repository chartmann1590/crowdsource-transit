import { describe, expect, it } from 'vitest';
import { decodePolyline, encodePolyline } from './polyline';

describe('polyline (precision 5)', () => {
  // Google's documented example: (38.5,-120.2), (40.7,-120.95), (43.252,-126.453)
  const googleExample = '_p~iF~ps|U_ulLnnqC_mqNvxq`@';
  const googlePoints: [number, number][] = [
    [-120.2, 38.5],
    [-120.95, 40.7],
    [-126.453, 43.252],
  ];

  it('encodes the reference example', () => {
    expect(encodePolyline(googlePoints)).toBe(googleExample);
  });

  it('decodes the reference example as [lng, lat]', () => {
    const decoded = decodePolyline(googleExample);
    expect(decoded).toHaveLength(3);
    decoded.forEach(([lng, lat], i) => {
      expect(lng).toBeCloseTo(googlePoints[i][0], 5);
      expect(lat).toBeCloseTo(googlePoints[i][1], 5);
    });
  });

  it('round-trips arbitrary points', () => {
    const pts: [number, number][] = [
      [-73.98513, 40.75889],
      [-73.98, 40.76],
      [-74.0, 40.7],
      [0, 0],
    ];
    const back = decodePolyline(encodePolyline(pts));
    back.forEach(([lng, lat], i) => {
      expect(lng).toBeCloseTo(pts[i][0], 5);
      expect(lat).toBeCloseTo(pts[i][1], 5);
    });
  });

  it('handles empty input', () => {
    expect(encodePolyline([])).toBe('');
    expect(decodePolyline('')).toEqual([]);
  });
});
