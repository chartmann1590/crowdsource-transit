import { describe, expect, it, vi } from 'vitest';
import { firePointsAwarded, onPointsAwarded } from './pointsFeedback';

describe('pointsFeedback', () => {
  const payload = { points: 10, totalPoints: 110, newBadges: ['first-report'], leveledUp: true };

  it('notifies subscribers and stops after unsubscribe', () => {
    const a = vi.fn();
    const b = vi.fn();
    const offA = onPointsAwarded(a);
    const offB = onPointsAwarded(b);

    firePointsAwarded(payload);
    expect(a).toHaveBeenCalledWith(payload);
    expect(b).toHaveBeenCalledWith(payload);

    offA();
    firePointsAwarded(payload);
    expect(a).toHaveBeenCalledTimes(1);
    expect(b).toHaveBeenCalledTimes(2);
    offB();
  });
});
