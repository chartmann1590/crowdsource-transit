import { ref, push, runTransaction } from 'firebase/database';
import { database } from './config';

export async function addComment(params: {
  userId: string;
  displayName: string;
  isAnonymous: boolean;
  avatarInitials: string;
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  text: string;
  transitType?: string;
  rating?: number;
}) {
  const now = Date.now();
  await push(ref(database, `comments/${params.targetType}/${params.targetId}`), {
    userId: params.userId,
    displayName: params.displayName,
    isAnonymous: params.isAnonymous,
    avatarInitials: params.avatarInitials,
    targetType: params.targetType,
    targetId: params.targetId,
    text: params.text,
    transitType: params.transitType || null,
    rating: params.rating || null,
    helpfulCount: 0,
    flagged: false,
    createdAt: now,
    updatedAt: now,
  });

  if (params.targetType === 'stop') {
    await runTransaction(ref(database, `stopStats/${params.targetId}/commentCount`), (current) =>
      (current || 0) + 1,
    );
  }

  await runTransaction(ref(database, `users/${params.userId}/stats/reviewCount`), (current) =>
    (current || 0) + 1,
  );
}
