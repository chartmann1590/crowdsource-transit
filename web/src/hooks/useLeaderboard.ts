import { useEffect, useState } from 'react';
import { ref, query, orderByChild, limitToLast, onValue } from 'firebase/database';
import { database } from '../firebase/config';

export interface LeaderboardEntry {
  uid: string;
  displayName: string;
  points: number;
}

export function useLeaderboard() {
  const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const q = query(ref(database, 'leaderboard'), orderByChild('points'), limitToLast(50));
    const unsub = onValue(q, (snap) => {
      const list: LeaderboardEntry[] = [];
      snap.forEach((child) => {
        const val = child.val() as { displayName?: string; points?: number; isAnonymous?: boolean };
        list.push({
          uid: child.key!,
          displayName: val.isAnonymous ? 'Anonymous Rider' : val.displayName || 'Rider',
          points: val.points ?? 0,
        });
      });
      list.sort((a, b) => b.points - a.points);
      setEntries(list);
      setLoading(false);
    });
    return unsub;
  }, []);

  return { entries, loading };
}
