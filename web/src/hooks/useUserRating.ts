import { useEffect, useState } from 'react';
import { ref, onValue } from 'firebase/database';
import { database } from '../firebase/config';
import type { Rating } from '../types/transit';

export function useUserRating(
  userId: string | undefined,
  targetType: string,
  targetId: string | null,
) {
  const [rating, setRating] = useState<number | null>(null);

  useEffect(() => {
    if (!userId || !targetId) return;
    const r = ref(database, `ratings/${targetType}/${targetId}/${userId}`);
    const unsub = onValue(r, (snap) => {
      const val = snap.val() as Rating | null;
      setRating(val?.overall ?? null);
    });
    return unsub;
  }, [userId, targetType, targetId]);

  return rating;
}
