import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { isTransitLeg, isWalkLeg } from '../types/itinerary';
import { UnsupportedTripPlanVersionError, decodeTripPlan, encodeTripPlan, tripPlanFromJson } from './tripCodec';

const fixturesDir = join(dirname(fileURLToPath(import.meta.url)), '../../../docs/routing/fixtures');
const fixture = (name: string) => readFileSync(join(fixturesDir, name), 'utf8');

describe.each(['plan-direct', 'plan-transfer'])('golden fixture %s', (base) => {
  it('decodes the golden blob to the fixture plan', async () => {
    const fromJson = tripPlanFromJson(fixture(`${base}.json`));
    const fromBlob = await decodeTripPlan(fixture(`${base}.blob.txt`));
    expect(fromBlob).toEqual(fromJson);
  });

  it('round-trips through encode/decode', async () => {
    const plan = tripPlanFromJson(fixture(`${base}.json`));
    expect(await decodeTripPlan(await encodeTripPlan(plan))).toEqual(plan);
  });
});

it('transit legs keep the full stop sequence', () => {
  const plan = tripPlanFromJson(fixture('plan-direct.json'));
  const transit = plan.legs.filter(isTransitLeg);
  expect(transit).toHaveLength(1);
  expect(transit[0].stops).toHaveLength(11);
  expect(transit[0].stops[0].id).toBe(transit[0].board.stop_id);
  expect(transit[0].stops.at(-1)?.id).toBe(transit[0].alight.stop_id);
  expect(plan.legs.filter(isWalkLeg)).toHaveLength(2);
});

it('rejects unsupported versions', () => {
  const future = fixture('plan-direct.json').replace('"v": 1', '"v": 99');
  expect(() => tripPlanFromJson(future)).toThrow(UnsupportedTripPlanVersionError);
});
