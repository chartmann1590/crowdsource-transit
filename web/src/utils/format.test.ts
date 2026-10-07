import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { formatDate, formatNumber, formatRating, formatRelativeMinutes, truncate } from './format';

const NOW = new Date('2026-10-07T12:00:00Z').getTime();
const DAY = 24 * 60 * 60 * 1000;

describe('format utils', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('formatDate buckets relative ages', () => {
    expect(formatDate(NOW)).toBe('Today');
    expect(formatDate(NOW - DAY)).toBe('Yesterday');
    expect(formatDate(NOW - 3 * DAY)).toBe('3d ago');
    expect(formatDate(NOW - 14 * DAY)).toBe('2w ago');
    expect(formatDate(NOW - 90 * DAY)).toBe('3mo ago');
    expect(formatDate(NOW - 400 * DAY)).toMatch(/2025/);
  });

  it('formatRating averages and handles no ratings', () => {
    expect(formatRating(0, 0)).toBe('No ratings');
    expect(formatRating(9, 2)).toBe('4.5 (2)');
    expect(formatRating(10, 3)).toBe('3.3 (3)');
  });

  it('formatNumber abbreviates thousands and millions', () => {
    expect(formatNumber(999)).toBe('999');
    expect(formatNumber(1500)).toBe('1.5K');
    expect(formatNumber(2_300_000)).toBe('2.3M');
  });

  it('truncate keeps short strings and ellipsizes long ones', () => {
    expect(truncate('short', 10)).toBe('short');
    expect(truncate('hello world again', 6)).toBe('hello...');
  });

  it('formatRelativeMinutes', () => {
    expect(formatRelativeMinutes(NOW)).toBe('just now');
    expect(formatRelativeMinutes(NOW + 60_000)).toBe('just now'); // future clamps to 0
    expect(formatRelativeMinutes(NOW - 60_000)).toBe('1 min ago');
    expect(formatRelativeMinutes(NOW - 25 * 60_000)).toBe('25 min ago');
    expect(formatRelativeMinutes(NOW - 60 * 60_000)).toBe('1 hr ago');
    expect(formatRelativeMinutes(NOW - 5 * 60 * 60_000)).toBe('5 hrs ago');
  });
});
