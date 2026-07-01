import { TRANSIT_COLORS } from '../../utils/transit-colors';

interface TransitBadgeProps {
  type: string;
  label?: string;
  size?: 'sm' | 'md';
}

export function TransitBadge({ type, label, size = 'md' }: TransitBadgeProps) {
  const color = TRANSIT_COLORS[type] || '#1565C0';
  const fontSize = size === 'sm' ? '10px' : '12px';
  const padding = size === 'sm' ? '2px 6px' : '3px 8px';

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        background: color,
        color: 'white',
        borderRadius: '9999px',
        padding,
        fontSize,
        fontWeight: 500,
        lineHeight: 1.4,
        textTransform: 'capitalize',
      }}
    >
      {label ?? type}
    </span>
  );
}
