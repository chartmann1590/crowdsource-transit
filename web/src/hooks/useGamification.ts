import { useEffect, useState } from 'react';
import { ref, onValue } from 'firebase/database';
import { database } from '../firebase/config';
import { useAuth } from './useAuth';

export interface UserStats {
  points: number;
  reviewCount: number;
  stopsAdded: number;
  photoCount: number;
  checkinCount: number;
  streakCount: number;
  badges: Record<string, boolean>;
}

const EMPTY_STATS: UserStats = {
  points: 0,
  reviewCount: 0,
  stopsAdded: 0,
  photoCount: 0,
  checkinCount: 0,
  streakCount: 0,
  badges: {},
};

export function useGamification() {
  const { user } = useAuth();
  const [stats, setStats] = useState<UserStats>(EMPTY_STATS);

  useEffect(() => {
    if (!user) {
      setStats(EMPTY_STATS);
      return;
    }
    const r = ref(database, `users/${user.uid}/stats`);
    const unsub = onValue(r, (snap) => {
      const val = (snap.val() as Record<string, unknown>) || {};
      setStats({
        points: (val.points as number) ?? 0,
        reviewCount: (val.reviewCount as number) ?? 0,
        stopsAdded: (val.stopsAdded as number) ?? 0,
        photoCount: (val.photoCount as number) ?? 0,
        checkinCount: (val.checkinCount as number) ?? 0,
        streakCount: (val.streakCount as number) ?? 0,
        badges: (val.badges as Record<string, boolean>) ?? {},
      });
    });
    return unsub;
  }, [user]);

  return stats;
}
