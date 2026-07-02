import { ref, get, set } from 'firebase/database';
import { database, auth } from './config';
import { firePointsAwarded } from '../utils/pointsFeedback';

export interface Level {
  name: string;
  min: number;
}

export const LEVELS: Level[] = [
  { name: 'Pedestrian', min: 0 },
  { name: 'Commuter', min: 50 },
  { name: 'Regular', min: 150 },
  { name: 'Conductor', min: 400 },
  { name: 'Transit Legend', min: 1000 },
];

export function getLevel(points: number): Level {
  let current = LEVELS[0];
  for (const lvl of LEVELS) {
    if (points >= lvl.min) current = lvl;
  }
  return current;
}

export function getLevelProgress(points: number): { current: Level; next: Level | null; progress: number } {
  const idx = LEVELS.findIndex((l) => l.name === getLevel(points).name);
  const current = LEVELS[idx];
  const next = LEVELS[idx + 1] ?? null;
  if (!next) return { current, next: null, progress: 1 };
  const progress = (points - current.min) / (next.min - current.min);
  return { current, next, progress: Math.min(1, Math.max(0, progress)) };
}

export const POINTS = {
  newStop: 10,
  review: 5,
  photo: 3,
  checkin: 2,
} as const;

export type PointsKind = keyof typeof POINTS;

export interface BadgeDef {
  label: string;
  icon: string;
  description: string;
}

export const BADGES: Record<string, BadgeDef> = {
  first_review: { label: 'First Review', icon: '📝', description: 'Posted your first review' },
  first_stop: { label: 'First Stop', icon: '📍', description: 'Added your first stop' },
  ten_stops: { label: '10 Stops', icon: '🏗️', description: 'Added 10 stops' },
  twentyfive_reviews: { label: '25 Reviews', icon: '⭐', description: 'Posted 25 reviews' },
  first_photo: { label: 'First Photo', icon: '📷', description: 'Uploaded your first photo' },
  streak_7: { label: '7-Day Streak', icon: '🔥', description: 'Contributed 7 days in a row' },
};

function localDateString(d: Date = new Date()): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export interface AwardResult {
  pointsAwarded: number;
  totalPoints: number;
  newBadges: string[];
  leveledUp: boolean;
}

/**
 * Awards points for a contribution and fans out updated stats to
 * /users/{uid}/stats and /leaderboard/{uid} in the same client write.
 * Also tracks contribution streaks and unlocks milestone badges.
 */
export async function awardPoints(kind: PointsKind): Promise<AwardResult | null> {
  const user = auth.currentUser;
  if (!user) return null;

  const points = POINTS[kind];
  const statsRef = ref(database, `users/${user.uid}/stats`);
  const snap = await get(statsRef);
  const stats = (snap.val() as Record<string, unknown>) || {};

  const prevPoints = (stats.points as number) ?? 0;
  const prevLevel = getLevel(prevPoints).name;

  const counts = {
    reviewCount: (stats.reviewCount as number) ?? 0,
    stopsAdded: (stats.stopsAdded as number) ?? 0,
    helpfulVotes: (stats.helpfulVotes as number) ?? 0,
    reportCount: (stats.reportCount as number) ?? 0,
    photoCount: (stats.photoCount as number) ?? 0,
    checkinCount: (stats.checkinCount as number) ?? 0,
  };
  if (kind === 'review') counts.reviewCount += 1;
  if (kind === 'newStop') counts.stopsAdded += 1;
  if (kind === 'photo') counts.photoCount += 1;
  if (kind === 'checkin') counts.checkinCount += 1;

  const today = localDateString();
  const lastDate = (stats.lastContributionDate as string | undefined) ?? null;
  let streakCount = (stats.streakCount as number) ?? 0;
  if (lastDate !== today) {
    if (lastDate) {
      const yesterday = localDateString(new Date(Date.now() - 86400000));
      streakCount = lastDate === yesterday ? streakCount + 1 : 1;
    } else {
      streakCount = 1;
    }
  }

  const newPoints = prevPoints + points;
  const badges: Record<string, boolean> = { ...((stats.badges as Record<string, boolean>) || {}) };
  const newBadges: string[] = [];

  const award = (id: string) => {
    if (!badges[id]) {
      badges[id] = true;
      newBadges.push(id);
    }
  };

  if (kind === 'review' && counts.reviewCount === 1) award('first_review');
  if (kind === 'newStop' && counts.stopsAdded === 1) award('first_stop');
  if (counts.stopsAdded >= 10) award('ten_stops');
  if (counts.reviewCount >= 25) award('twentyfive_reviews');
  if (kind === 'photo' && counts.photoCount === 1) award('first_photo');
  if (streakCount >= 7) award('streak_7');

  const newStats = {
    ...stats,
    points: newPoints,
    ...counts,
    streakCount,
    lastContributionDate: today,
    badges,
  };

  const displayName = user.isAnonymous ? 'Anonymous Rider' : user.displayName || 'Rider';

  await Promise.all([
    set(statsRef, newStats),
    set(ref(database, `leaderboard/${user.uid}`), {
      displayName,
      points: newPoints,
      isAnonymous: user.isAnonymous,
    }),
  ]);

  const result: AwardResult = {
    pointsAwarded: points,
    totalPoints: newPoints,
    newBadges,
    leveledUp: getLevel(newPoints).name !== prevLevel,
  };

  firePointsAwarded({
    points: result.pointsAwarded,
    totalPoints: result.totalPoints,
    newBadges: result.newBadges,
    leveledUp: result.leveledUp,
  });

  return result;
}
