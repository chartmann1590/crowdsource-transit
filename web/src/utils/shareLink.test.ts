import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { simplifyPoints } from './shareLink';
import { itineraryToText } from './itineraryText';
import { tripPlanFromJson } from './tripCodec';

const fixturesDir = join(dirname(fileURLToPath(import.meta.url)), '../../../docs/routing/fixtures');
const plan = tripPlanFromJson(readFileSync(join(fixturesDir, 'plan-direct.json'), 'utf8'));

describe('simplifyPoints', () => {
  it('keeps endpoints and drops collinear points', () => {
    const line: [number, number][] = [
      [-74.0, 40.7],
      [-74.0, 40.705], // collinear — should be dropped at 20 m tolerance
      [-74.0, 40.71],
    ];
    const simplified = simplifyPoints(line, 20);
    expect(simplified[0]).toEqual(line[0]);
    expect(simplified.at(-1)).toEqual(line.at(-1));
    expect(simplified).toHaveLength(2);
  });

  it('keeps significant corners', () => {
    const corner: [number, number][] = [
      [-74.0, 40.7],
      [-73.99, 40.705], // ~850 m off the straight line — must survive
      [-74.0, 40.71],
    ];
    expect(simplifyPoints(corner, 20)).toHaveLength(3);
  });
});

describe('itineraryToText', () => {
  it('renders numbered steps with board/alight stops and the link', () => {
    const text = itineraryToText(plan, 'https://example.test/trip#d=abc');
    expect(text).toContain('Herald Square → Wall Street');
    expect(text).toContain('1. Walk');
    expect(text).toContain('toward Bay Ridge - 95 St from 34 St - Herald Sq');
    expect(text).toContain('get off at Whitehall St - South Ferry');
    expect(text).toContain('Ride 10 stops');
    expect(text).toContain('https://example.test/trip#d=abc');
    expect(text).toContain('Planned with CrowdTransit');
  });
});
