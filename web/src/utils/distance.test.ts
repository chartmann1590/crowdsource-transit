import { describe, expect, it } from 'vitest';
import { haversineDistance } from './distance';

describe('haversineDistance', () => {
  it('is zero for identical points', () => {
    expect(haversineDistance(40.7128, -74.006, 40.7128, -74.006)).toBe(0);
  });

  it('matches the known NYC → LA great-circle distance (~3936 km)', () => {
    const km = haversineDistance(40.7128, -74.006, 34.0522, -118.2437);
    expect(km).toBeGreaterThan(3925);
    expect(km).toBeLessThan(3945);
  });

  it('is symmetric', () => {
    const a = haversineDistance(37.7749, -122.4194, 37.8044, -122.2712);
    const b = haversineDistance(37.8044, -122.2712, 37.7749, -122.4194);
    expect(a).toBeCloseTo(b, 10);
  });

  it('one degree of latitude is ~111 km', () => {
    expect(haversineDistance(0, 0, 1, 0)).toBeCloseTo(111.19, 1);
  });
});
