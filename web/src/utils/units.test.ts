import { afterEach, describe, expect, it } from 'vitest';
import { formatDistance, getDistanceUnit, setDistanceUnit } from './units';

describe('units', () => {
  afterEach(() => localStorage.clear());

  it('defaults to imperial and persists the choice', () => {
    expect(getDistanceUnit()).toBe('imperial');
    setDistanceUnit('metric');
    expect(getDistanceUnit()).toBe('metric');
    setDistanceUnit('imperial');
    expect(getDistanceUnit()).toBe('imperial');
  });

  it('imperial: feet under a mile, miles above', () => {
    expect(formatDistance(100, 'imperial')).toBe('328 ft');
    expect(formatDistance(1609.344, 'imperial')).toBe('1.0 mi');
    expect(formatDistance(5000, 'imperial')).toBe('3.1 mi');
  });

  it('metric: metres under a kilometre, km above', () => {
    expect(formatDistance(250.4, 'metric')).toBe('250 m');
    expect(formatDistance(1000, 'metric')).toBe('1.0 km');
    expect(formatDistance(12345, 'metric')).toBe('12.3 km');
  });

  it('uses the stored preference when no unit is passed', () => {
    setDistanceUnit('metric');
    expect(formatDistance(500)).toBe('500 m');
  });
});
