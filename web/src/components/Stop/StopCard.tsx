import type { Stop } from '../../types/transit';
import { StarRating } from '../Review/StarRating';
import { TransitBadge } from '../Transit/TransitBadge';
import styles from './StopCard.module.css';

interface StopCardProps {
  stop: Stop;
  selected?: boolean;
  onClick: () => void;
  onViewDetail: () => void;
}

export function StopCard({ stop, selected, onClick, onViewDetail }: StopCardProps) {
  const avgRating = stop.ratingCount > 0 ? stop.ratingSum / stop.ratingCount : 0;

  const location = [stop.city, stop.state || stop.country]
    .filter(Boolean)
    .join(', ');

  return (
    <div
      className={`${styles.card} ${selected ? styles.selected : ''}`}
      onClick={onClick}
    >
      <div className={styles.header}>
        <span className={styles.name}>{stop.name}</span>
        {location && <span className={styles.city}>{location}</span>}
      </div>
      <div className={styles.badges}>
        {(stop.transitTypes || []).map((type) => (
          <TransitBadge key={type} type={type} size="sm" />
        ))}
      </div>
      <div className={styles.footer}>
        <StarRating rating={avgRating} size={14} />
        <span className={styles.reviewCount}>({stop.ratingCount})</span>
        <button
          className={styles.detailBtn}
          onClick={(e) => {
            e.stopPropagation();
            onViewDetail();
          }}
        >
          Details →
        </button>
      </div>
    </div>
  );
}
