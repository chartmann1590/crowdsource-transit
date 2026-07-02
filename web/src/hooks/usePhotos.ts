import { useEffect, useState } from 'react';
import { ref, onValue, query, orderByChild, limitToLast } from 'firebase/database';
import { database } from '../firebase/config';
import type { StopPhoto } from '../firebase/photos';

export function usePhotos(stopId: string | null) {
  const [photos, setPhotos] = useState<StopPhoto[]>([]);

  useEffect(() => {
    if (!stopId) {
      setPhotos([]);
      return;
    }
    const q = query(ref(database, `photos/${stopId}`), orderByChild('timestamp'), limitToLast(10));
    const unsub = onValue(q, (snap) => {
      const list: StopPhoto[] = [];
      snap.forEach((child) => {
        const val = child.val() as { uid: string; data: string; timestamp: number };
        list.push({ id: child.key!, uid: val.uid, data: val.data, timestamp: val.timestamp });
      });
      list.sort((a, b) => b.timestamp - a.timestamp);
      setPhotos(list);
    });
    return unsub;
  }, [stopId]);

  return photos;
}
