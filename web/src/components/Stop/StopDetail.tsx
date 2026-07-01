import type { Stop } from '../../types/transit';
import { StarRating } from '../Review/StarRating';
import { TransitBadge } from '../Transit/TransitBadge';
import { formatDate } from '../../utils/format';
import styles from './StopDetail.module.css';

interface StopDetailProps {
  stop: Stop;
}

export function StopDetail({ stop }: StopDetailProps) {
  const avgRating = stop.ratingCount > 0 ? stop.ratingSum / stop.ratingCount : 0;

  return (
    <div className={styles.container}>
      <h1 className={styles.name}>{stop.name}</h1>
      <p className={styles.location}>
        {stop.city}, {stop.state}
        {stop.code ? ` • Stop #${stop.code}` : ''}
      </p>

      <div className={styles.badges}>
        {(stop.transitTypes || []).map((type) => (
          <TransitBadge key={type} type={type} />
        ))}
      </div>

      <div className={styles.ratingRow}>
        <StarRating rating={avgRating} size={20} />
        <span className={styles.ratingText}>
          {stop.ratingCount > 0
            ? `${avgRating.toFixed(1)} (${stop.ratingCount} reviews)`
            : 'No reviews yet'}
        </span>
      </div>

      {stop.desc && <p className={styles.desc}>{stop.desc}</p>}

      <div className={styles.features}>
        <h3>Features</h3>
        <div className={styles.featureGrid}>
          {Object.entries(stop.features || {}).map(([key, val]) => (
            <span
              key={key}
              className={`${styles.feature} ${val ? styles.enabled : styles.disabled}`}
            >
              {key.replace(/([A-Z])/g, ' $1').replace(/^./, (s) => s.toUpperCase())}
            </span>
          ))}
        </div>
      </div>

      {stop.crowdsourced && (
        <span className={styles.crowdsourced}>Community-added stop</span>
      )}

      <p className={styles.meta}>
        Added {formatDate(stop.addedAt)}
        {stop.verified ? ' • Verified' : ''}
      </p>
    </div>
  );
}
