import { useEffect, useState } from 'react';
import { ref, onValue } from 'firebase/database';
import { database } from '../firebase/config';
import { ACTIVITY_WINDOW_MS, type ActivityType } from '../firebase/activity';

export interface ActivityEntry {
  id: string;
  uid: string;
  type: ActivityType;
  timestamp: number;
}

export function useActivity(stopId: string | null) {
  const [entries, setEntries] = useState<ActivityEntry[]>([]);

  useEffect(() => {
    if (!stopId) {
      setEntries([]);
      return;
    }
    const r = ref(database, `activity/${stopId}`);
    const unsub = onValue(r, (snap) => {
      const now = Date.now();
      const list: ActivityEntry[] = [];
      snap.forEach((child) => {
        const val = child.val() as { uid: string; type: ActivityType; timestamp: number };
        if (val.timestamp >= now - ACTIVITY_WINDOW_MS) {
          list.push({ id: child.key!, uid: val.uid, type: val.type, timestamp: val.timestamp });
        }
      });
      list.sort((a, b) => b.timestamp - a.timestamp);
      setEntries(list);
    });
    return unsub;
  }, [stopId]);

  return entries;
}
