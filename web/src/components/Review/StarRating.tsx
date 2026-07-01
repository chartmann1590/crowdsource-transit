import { useState } from 'react';

interface StarRatingProps {
  rating: number;
  maxStars?: number;
  size?: number;
  interactive?: boolean;
  onRate?: (rating: number) => void;
}

export function StarRating({
  rating,
  maxStars = 5,
  size = 20,
  interactive = false,
  onRate,
}: StarRatingProps) {
  const [hovered, setHovered] = useState(0);

  return (
    <span style={{ display: 'inline-flex', gap: '2px' }}>
      {Array.from({ length: maxStars }, (_, i) => i + 1).map((star) => {
        const filled = star <= (interactive && hovered ? hovered : rating);
        return (
          <svg
            key={star}
            width={size}
            height={size}
            viewBox="0 0 24 24"
            fill={filled ? '#FFC107' : 'none'}
            stroke={filled ? '#FFC107' : '#9AA0A6'}
            strokeWidth="1.5"
            style={{ cursor: interactive ? 'pointer' : 'default' }}
            onMouseEnter={() => interactive && setHovered(star)}
            onMouseLeave={() => interactive && setHovered(0)}
            onClick={() => interactive && onRate?.(star)}
          >
            <polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26" />
          </svg>
        );
      })}
    </span>
  );
}
