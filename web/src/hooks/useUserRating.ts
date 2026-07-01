import { useEffect, useState } from 'react';
import { ref, onValue } from 'firebase/database';
import { database } from '../firebase/config';

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
      setRating(snap.val() as number | null);
    });
    return unsub;
  }, [userId, targetType, targetId]);

  return rating;
}
