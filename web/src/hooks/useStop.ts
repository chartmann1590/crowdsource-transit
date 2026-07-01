import { useEffect, useState } from 'react';
import { ref, onValue } from 'firebase/database';
import { database } from '../firebase/config';
import type { Stop } from '../types/transit';

export function useStop(stopId: string | null) {
  const [stop, setStop] = useState<Stop | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!stopId) {
      setLoading(false);
      return;
    }
    const r = ref(database, `stops/${stopId}`);
    const unsub = onValue(r, (snap) => {
      setStop(snap.val() as Stop | null);
      setLoading(false);
    });
    return unsub;
  }, [stopId]);

  return { stop, loading };
}
