import { ref, runTransaction } from 'firebase/database';
import { database } from './config';

export async function submitRating(params: {
  userId: string;
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  rating: number;
}) {
  const targetRef = ref(database, `${params.targetType}s/${params.targetId}`);
  await runTransaction(targetRef, (current) => {
    if (current) {
      current.ratingSum = (current.ratingSum || 0) + params.rating;
      current.ratingCount = (current.ratingCount || 0) + 1;
    }
    return current;
  });
}
