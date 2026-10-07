import { afterEach, describe, expect, it } from 'vitest';
import { makePlan } from '../test/tripPlanFixture';
import { itineraryToText } from './itineraryText';
import { setDistanceUnit } from './units';

describe('itineraryToText', () => {
  afterEach(() => localStorage.clear());

  it('renders numbered steps, the share link and the footer', () => {
    setDistanceUnit('metric');
    const text = itineraryToText(makePlan(), 'https://example.com/t/abc');
    const lines = text.split('\n');
    expect(lines[0]).toBe('Home → Office');
    expect(lines[1]).toContain('(1h 10m)');
    expect(text).toContain('1. Walk 400 m to 1st Ave');
    expect(text).toContain('2. Take bus M15 toward Harlem from 1st Ave');
    expect(text).toContain('Ride 2 stops, get off at 42nd St');
    expect(text).toContain('3. Walk 150 m to Office');
    expect(text).toContain('View this trip: https://example.com/t/abc');
    expect(lines[lines.length - 1]).toBe('Planned with CrowdTransit');
  });

  it('omits the share line when no url is given and singularizes one stop', () => {
    const plan = makePlan();
    const leg = plan.legs[1];
    if (leg.t !== 'r') throw new Error('fixture');
    leg.stops = [leg.stops[0], leg.stops[2]];
    const text = itineraryToText(plan);
    expect(text).not.toContain('View this trip');
    expect(text).toContain('Ride 1 stop,');
  });
});
