import { useEffect, useState } from 'react';
import { ref, onValue, query, orderByChild } from 'firebase/database';
import { database } from '../firebase/config';
import type { Comment } from '../types/transit';

export function useComments(targetType: string, targetId: string | null) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!targetId) {
      setLoading(false);
      return;
    }
    const q = query(
      ref(database, `comments/${targetType}/${targetId}`),
      orderByChild('createdAt'),
    );
    const unsub = onValue(q, (snap) => {
      const data: Comment[] = [];
      snap.forEach((child) => {
        data.push({ commentId: child.key!, ...child.val() });
      });
      setComments(data.reverse());
      setLoading(false);
    });
    return unsub;
  }, [targetType, targetId]);

  return { comments, loading };
}
