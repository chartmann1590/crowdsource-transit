import type { Comment } from '../../types/transit';
import { StarRating } from './StarRating';
import { TransitBadge } from '../Transit/TransitBadge';
import { formatDate } from '../../utils/format';
import styles from './ReviewCard.module.css';

interface ReviewCardProps {
  comment: Comment;
}

export function ReviewCard({ comment }: ReviewCardProps) {
  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <div className={styles.avatar}>{comment.avatarInitials || '?'}</div>
        <div className={styles.authorInfo}>
          <span className={styles.name}>
            {comment.isAnonymous ? 'Anonymous Rider' : comment.displayName}
          </span>
          <span className={styles.date}>{formatDate(comment.createdAt)}</span>
        </div>
        {comment.rating ? <StarRating rating={comment.rating} size={14} /> : null}
      </div>
      {comment.text && <p className={styles.text}>{comment.text}</p>}
      <div className={styles.footer}>
        {comment.transitType ? (
          <TransitBadge type={comment.transitType} size="sm" />
        ) : null}
        <span className={styles.helpful}>
          {comment.helpfulCount > 0
            ? `${comment.helpfulCount} found helpful`
            : ''}
        </span>
      </div>
    </div>
  );
}
