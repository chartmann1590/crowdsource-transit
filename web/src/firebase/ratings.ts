import { ref, runTransaction, set, get } from 'firebase/database';
import { database } from './config';
import { auth } from './config';

export async function submitRating(params: {
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  rating: number;
}) {
  const user = auth.currentUser;
  if (!user) throw new Error('Not authenticated');

  const prevRatingRef = ref(database, `ratings/${params.targetType}/${params.targetId}/${user.uid}`);
  const prevSnap = await get(prevRatingRef);
  const prevRating = (prevSnap.val() as number) ?? 0;

  const now = Date.now();
  await set(prevRatingRef, {
    userId: user.uid,
    displayName: user.displayName || 'Rider',
    isAnonymous: user.isAnonymous,
    targetType: params.targetType,
    targetId: params.targetId,
    overall: params.rating,
    subcategories: {},
    createdAt: now,
    updatedAt: now,
  });

  const targetPath = params.targetType === 'stop'
    ? `stopStats/${params.targetId}`
    : `${params.targetType}s/${params.targetId}`;

  if (prevRating === 0) {
    await runTransaction(ref(database, `${targetPath}/ratingSum`), (current) =>
      (current || 0) + params.rating,
    );
    await runTransaction(ref(database, `${targetPath}/ratingCount`), (current) =>
      (current || 0) + 1,
    );
  } else {
    const delta = params.rating - prevRating;
    if (delta !== 0) {
      await runTransaction(ref(database, `${targetPath}/ratingSum`), (current) =>
        (current || 0) + delta,
      );
    }
  }
}
