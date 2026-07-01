import { useState } from 'react';
import { ref, push } from 'firebase/database';
import { database } from '../../firebase/config';
import { useAuth } from '../Auth/AuthContext';
import { LoginModal } from '../Auth/LoginModal';
import { StarRating } from './StarRating';
import styles from './ReviewForm.module.css';

interface ReviewFormProps {
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export function ReviewForm({ targetType, targetId, onSuccess, onCancel }: ReviewFormProps) {
  const { user } = useAuth();
  const [showLogin, setShowLogin] = useState(false);
  const [rating, setRating] = useState(0);
  const [text, setText] = useState('');
  const [transitType, setTransitType] = useState('bus');
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  if (!user) {
    return (
      <>
        <div className={styles.prompt}>
          <p>Sign in to write a review</p>
          <button className={styles.signInBtn} onClick={() => setShowLogin(true)}>
            Sign In
          </button>
        </div>
        {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
      </>
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!user || rating === 0) return;

    setSubmitting(true);
    try {
      const displayName = isAnonymous
        ? 'Anonymous Rider'
        : user.displayName || 'Rider';
      const initials = isAnonymous
        ? '?'
        : displayName
            .split(' ')
            .slice(0, 2)
            .map((w: string) => w[0]?.toUpperCase() ?? 'R')
            .join('');

      const now = Date.now();
      await push(ref(database, `comments/${targetType}/${targetId}`), {
        userId: user.uid,
        displayName,
        isAnonymous,
        avatarInitials: initials,
        targetType,
        targetId,
        text,
        transitType,
        rating,
        helpfulCount: 0,
        flagged: false,
        createdAt: now,
        updatedAt: now,
      });
      setRating(0);
      setText('');
      onSuccess?.();
    } finally {
      setSubmitting(false);
    }
  }

  const transitTypes = ['bus', 'train', 'subway', 'ferry', 'tram'];

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <h3 className={styles.title}>Write a Review</h3>

      <div className={styles.field}>
        <label>Overall Rating *</label>
        <StarRating rating={rating} size={32} interactive onRate={setRating} />
      </div>

      <div className={styles.field}>
        <label>Your Experience</label>
        <textarea
          className={styles.textarea}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Share what it's like at this stop..."
          maxLength={2000}
          rows={4}
        />
      </div>

      <div className={styles.field}>
        <label>What did you ride?</label>
        <div className={styles.chips}>
          {transitTypes.map((t) => (
            <button
              key={t}
              type="button"
              className={`${styles.chip} ${transitType === t ? styles.chipSelected : ''}`}
              onClick={() => setTransitType(t)}
            >
              {t.charAt(0).toUpperCase() + t.slice(1)}
            </button>
          ))}
        </div>
      </div>

      <label className={styles.anonToggle}>
        <input
          type="checkbox"
          checked={isAnonymous}
          onChange={(e) => setIsAnonymous(e.target.checked)}
        />
        Post anonymously
      </label>

      <div className={styles.actions}>
        {onCancel && (
          <button type="button" className={styles.cancelBtn} onClick={onCancel}>
            Cancel
          </button>
        )}
        <button
          type="submit"
          className={styles.submitBtn}
          disabled={rating === 0 || submitting}
        >
          {submitting ? 'Posting...' : 'Post Review'}
        </button>
      </div>
    </form>
  );
}
