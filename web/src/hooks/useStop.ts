import { useEffect, useState } from 'react';
import { observeStopStats } from '../firebase/stops';
import type { Stop } from '../types/transit';

export function useStop(stopId: string | null) {
  const [stop, setStop] = useState<Stop | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!stopId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    const cleanup = observeStopStats(stopId, (updated) => {
      setStop(updated);
      setLoading(false);
    });
    return cleanup;
  }, [stopId]);

  return { stop, loading };
}
