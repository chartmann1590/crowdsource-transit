import type { Comment } from '../../types/transit';
import { ReviewCard } from './ReviewCard';
import styles from './ReviewList.module.css';

interface ReviewListProps {
  comments: Comment[];
  loading: boolean;
}

export function ReviewList({ comments, loading }: ReviewListProps) {
  if (loading) {
    return <p className={styles.status}>Loading reviews...</p>;
  }

  if (comments.length === 0) {
    return <p className={styles.status}>No reviews yet. Be the first!</p>;
  }

  return (
    <div className={styles.list}>
      {comments.map((comment) => (
        <ReviewCard key={comment.commentId} comment={comment} />
      ))}
    </div>
  );
}
