import { useEffect, useRef, useState } from 'react';
import { ref, onValue } from 'firebase/database';
import { database } from '../firebase/config';
import { ACTIVITY_WINDOW_MS } from '../firebase/activity';

/**
 * Watches the given stopIds for recent (last 90 min) activity and returns
 * the set of stopIds that currently have live activity, for driving the
 * pulsing halo layer on the map.
 */
export function useStopsActivity(stopIds: string[]): Set<string> {
  const [activeIds, setActiveIds] = useState<Set<string>>(new Set());
  const latestByStop = useRef<Map<string, number>>(new Map());
  const key = stopIds.slice().sort().join(',');

  useEffect(() => {
    const ids = key ? key.split(',') : [];
    const unsubs: Array<() => void> = [];

    ids.forEach((stopId) => {
      const r = ref(database, `activity/${stopId}`);
      const unsub = onValue(r, (snap) => {
        const now = Date.now();
        let latest = 0;
        snap.forEach((child) => {
          const ts = (child.val()?.timestamp as number) ?? 0;
          if (ts > latest) latest = ts;
        });
        if (latest >= now - ACTIVITY_WINDOW_MS) {
          latestByStop.current.set(stopId, latest);
        } else {
          latestByStop.current.delete(stopId);
        }
        setActiveIds(new Set(latestByStop.current.keys()));
      });
      unsubs.push(unsub);
    });

    return () => {
      unsubs.forEach((u) => u());
      latestByStop.current.clear();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  return activeIds;
}
