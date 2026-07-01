import styles from './LoadingSpinner.module.css';

interface LoadingSpinnerProps {
  size?: number;
}

export function LoadingSpinner({ size = 32 }: LoadingSpinnerProps) {
  return (
    <div className={styles.container}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        className={styles.spinner}
      >
        <circle cx="12" cy="12" r="10" stroke="var(--color-surface-elevated)" strokeWidth="3" />
        <path
          d="M12 2a10 10 0 0 1 10 10"
          stroke="var(--color-primary)"
          strokeWidth="3"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}
