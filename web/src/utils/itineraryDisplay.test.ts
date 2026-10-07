import { describe, expect, it } from 'vitest';
import { makePlan } from '../test/tripPlanFixture';
import { WALK_COLOR, formatLocalTime, planPolylines, planTimes, planWalkStepMarkers } from './itineraryDisplay';

describe('itineraryDisplay', () => {
  it('formatLocalTime handles missing / invalid input', () => {
    expect(formatLocalTime(undefined)).toBe('--:--');
    expect(formatLocalTime('not a date')).toBe('--:--');
    expect(formatLocalTime('2026-10-07T14:05:00Z')).toMatch(/\d{1,2}:\d{2}/);
  });

  it('planTimes spans first departure to last arrival', () => {
    const t = planTimes(makePlan());
    expect(t.duration).toBe('1h 10m');
    expect(t.dep).toBe(formatLocalTime('2026-10-07T14:00:00Z'));
    expect(t.arr).toBe(formatLocalTime('2026-10-07T15:10:00Z'));
  });

  it('planTimes formats sub-hour trips in minutes', () => {
    const plan = makePlan();
    plan.legs = [plan.legs[0]];
    expect(planTimes(plan).duration).toBe('5 min');
  });

  it('planTimes tolerates an empty plan', () => {
    const plan = makePlan();
    plan.legs = [];
    expect(planTimes(plan)).toEqual({ dep: '--:--', arr: '--:--', duration: '' });
  });

  it('planPolylines draws transit solid in route color and walks dashed', () => {
    const lines = planPolylines(makePlan());
    expect(lines).toHaveLength(3);
    expect(lines[0]).toMatchObject({ color: WALK_COLOR, dashed: true });
    expect(lines[0].points).toEqual([
      [-74.0, 40.7],
      [-73.999, 40.701],
    ]);
    expect(lines[1]).toMatchObject({ color: '#123456', dashed: false });
    expect(lines[1].points).toHaveLength(3);
    expect(lines[1].points[2][0]).toBeCloseTo(-73.981, 5);
    expect(lines[2].dashed).toBe(true);
  });

  it('planPolylines falls back to stop coordinates and default color', () => {
    const plan = makePlan();
    const leg = plan.legs[1];
    if (leg.t !== 'r') throw new Error('fixture');
    delete leg.shape_poly;
    leg.route.color = '';
    const transit = planPolylines(plan)[1];
    expect(transit.color).toBe('#00A862');
    expect(transit.points).toEqual(leg.stops.map((s) => [s.lng, s.lat]));
  });

  it('planWalkStepMarkers collects maneuver points from walk legs only', () => {
    expect(planWalkStepMarkers(makePlan())).toEqual([
      { lat: 40.7005, lng: -73.9995 },
      { lat: 40.701, lng: -73.999 },
    ]);
  });
});
