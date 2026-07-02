import { ref, push, get, query, orderByChild, startAt } from 'firebase/database';
import { database, auth } from './config';
import { awardPoints, type AwardResult } from './gamification';

export type ActivityType = 'checkin' | 'on_time' | 'late' | 'crowded' | 'empty' | 'not_running';

export const ACTIVITY_WINDOW_MS = 90 * 60 * 1000;

export function activityWindowStart(): number {
  return Date.now() - ACTIVITY_WINDOW_MS;
}

/**
 * Records a check-in or quick report for a stop. Client-enforced to allow
 * one entry of a given type per user per stop within the 90-minute window.
 * Awards +2 points on success.
 */
export async function submitActivity(stopId: string, type: ActivityType): Promise<AwardResult | null> {
  const user = auth.currentUser;
  if (!user) throw new Error('Not authenticated');

  const recentQuery = query(
    ref(database, `activity/${stopId}`),
    orderByChild('timestamp'),
    startAt(activityWindowStart()),
  );
  const snap = await get(recentQuery);
  let alreadyDone = false;
  snap.forEach((child) => {
    const val = child.val();
    if (val.uid === user.uid && val.type === type) alreadyDone = true;
  });
  if (alreadyDone) return null;

  await push(ref(database, `activity/${stopId}`), {
    uid: user.uid,
    type,
    timestamp: Date.now(),
  });

  return awardPoints('checkin');
}
