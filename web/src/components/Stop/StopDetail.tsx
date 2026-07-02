import { useRef, useState } from 'react';
import type { Stop } from '../../types/transit';
import { StarRating } from '../Review/StarRating';
import { TransitBadge } from '../Transit/TransitBadge';
import { LoginModal } from '../Auth/LoginModal';
import { formatDate, formatRelativeMinutes } from '../../utils/format';
import { useAuth } from '../Auth/AuthContext';
import { useActivity } from '../../hooks/useActivity';
import { usePhotos } from '../../hooks/usePhotos';
import { submitActivity, type ActivityType } from '../../firebase/activity';
import { uploadStopPhoto } from '../../firebase/photos';
import styles from './StopDetail.module.css';

interface StopDetailProps {
  stop: Stop;
}

const REPORT_TYPES: { type: ActivityType; label: string }[] = [
  { type: 'on_time', label: 'On time' },
  { type: 'late', label: 'Late' },
  { type: 'crowded', label: 'Crowded' },
  { type: 'empty', label: 'Empty' },
  { type: 'not_running', label: 'Not running' },
];

export function StopDetail({ stop }: StopDetailProps) {
  const { user } = useAuth();
  const activity = useActivity(stop.stopId);
  const photos = usePhotos(stop.stopId);
  const [showLogin, setShowLogin] = useState(false);
  const [pending, setPending] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [viewerIndex, setViewerIndex] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const avgRating = stop.ratingCount > 0 ? stop.ratingSum / stop.ratingCount : 0;

  const location = [stop.city, stop.state || stop.country]
    .filter(Boolean)
    .join(', ');

  const checkinCount = new Set(
    activity.filter((a) => a.type === 'checkin').map((a) => a.uid),
  ).size;
  const latestReport = activity.find((a) => a.type !== 'checkin');
  const reportLabel = REPORT_TYPES.find((r) => r.type === latestReport?.type)?.label;

  async function handleActivity(type: ActivityType) {
    if (!user) {
      setShowLogin(true);
      return;
    }
    setPending(type);
    try {
      await submitActivity(stop.stopId, type);
    } catch (err) {
      console.error('Failed to submit activity:', err);
    } finally {
      setPending(null);
    }
  }

  async function handlePhotoUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!user) {
      setShowLogin(true);
      return;
    }
    setUploading(true);
    try {
      await uploadStopPhoto(stop.stopId, file);
    } catch (err) {
      console.error('Failed to upload photo:', err);
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.name}>{stop.name}</h1>
      <p className={styles.location}>
        {[location, stop.code && `Stop #${stop.code}`].filter(Boolean).join(' • ') || '—'}
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

      <div className={styles.activitySection}>
        {(checkinCount > 0 || latestReport) && (
          <div className={styles.activityStrip}>
            <span className={styles.activityDot} />
            <span>
              {checkinCount > 0 ? `${checkinCount} ${checkinCount === 1 ? 'person' : 'people'} here` : 'Live activity'}
              {latestReport && reportLabel
                ? ` · reported ${reportLabel} ${formatRelativeMinutes(latestReport.timestamp)}`
                : ''}
            </span>
          </div>
        )}
        <div className={styles.checkinRow}>
          <button
            className={styles.checkinBtn}
            onClick={() => handleActivity('checkin')}
            disabled={pending === 'checkin'}
          >
            📍 I'm here
          </button>
          {REPORT_TYPES.map(({ type, label }) => (
            <button
              key={type}
              className={styles.reportChip}
              onClick={() => handleActivity(type)}
              disabled={pending === type}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

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

      <div className={styles.photoSection}>
        <div className={styles.photoSectionHeader}>
          <h3>Photos</h3>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            capture="environment"
            hidden
            onChange={handlePhotoUpload}
          />
          <button
            className={styles.photoUploadBtn}
            onClick={() => (user ? fileInputRef.current?.click() : setShowLogin(true))}
            disabled={uploading}
          >
            {uploading ? 'Uploading…' : '📷 Add photo'}
          </button>
        </div>
        {photos.length === 0 ? (
          <p className={styles.photoEmpty}>No photos yet. Be the first to add one!</p>
        ) : (
          <div className={styles.photoStrip}>
            {photos.map((photo, i) => (
              <img
                key={photo.id}
                src={photo.data}
                alt="Stop"
                className={styles.photoThumb}
                onClick={() => setViewerIndex(i)}
              />
            ))}
          </div>
        )}
      </div>

      {stop.crowdsourced && (
        <span className={styles.crowdsourced}>Community-added stop</span>
      )}

      <p className={styles.meta}>
        Added {formatDate(stop.addedAt)}
        {stop.verified ? ' • Verified' : ''}
      </p>

      {viewerIndex !== null && photos[viewerIndex] && (
        <div className={styles.viewerOverlay} onClick={() => setViewerIndex(null)}>
          <button className={styles.viewerClose} onClick={() => setViewerIndex(null)}>
            ×
          </button>
          {viewerIndex > 0 && (
            <button
              className={`${styles.viewerNav} ${styles.viewerPrev}`}
              onClick={(e) => {
                e.stopPropagation();
                setViewerIndex(viewerIndex - 1);
              }}
            >
              ‹
            </button>
          )}
          <img
            src={photos[viewerIndex].data}
            alt="Stop full view"
            className={styles.viewerImg}
            onClick={(e) => e.stopPropagation()}
          />
          {viewerIndex < photos.length - 1 && (
            <button
              className={`${styles.viewerNav} ${styles.viewerNext}`}
              onClick={(e) => {
                e.stopPropagation();
                setViewerIndex(viewerIndex + 1);
              }}
            >
              ›
            </button>
          )}
        </div>
      )}

      {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
    </div>
  );
}
