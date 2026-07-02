import { useEffect, useState } from 'react';
import confetti from 'canvas-confetti';
import { onPointsAwarded, type PointsFeedbackPayload } from '../../utils/pointsFeedback';
import { BADGES } from '../../firebase/gamification';
import styles from './PointsToast.module.css';

interface ToastState extends PointsFeedbackPayload {
  id: number;
}

export function PointsToast() {
  const [toasts, setToasts] = useState<ToastState[]>([]);

  useEffect(() => {
    return onPointsAwarded((payload) => {
      confetti({
        particleCount: payload.leveledUp ? 150 : 70,
        spread: 70,
        startVelocity: 35,
        origin: { y: 0.75 },
        colors: ['#00A862', '#FFB000', '#2563EB', '#8B2FC9'],
      });

      const id = Date.now() + Math.random();
      setToasts((prev) => [...prev, { id, ...payload }]);
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, 3400);
    });
  }, []);

  if (toasts.length === 0) return null;

  return (
    <div className={styles.container}>
      {toasts.map((t) => (
        <div key={t.id} className={styles.toast}>
          <span className={styles.points}>+{t.points} pts</span>
          {t.leveledUp && <span className={styles.levelUp}>Level up! 🎉</span>}
          {t.newBadges.map((badgeId) => (
            <span key={badgeId} className={styles.badge}>
              {BADGES[badgeId]?.icon} {BADGES[badgeId]?.label}
            </span>
          ))}
        </div>
      ))}
    </div>
  );
}
