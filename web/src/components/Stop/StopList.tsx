import type { Stop } from '../../types/transit';
import { StopCard } from './StopCard';
import styles from './StopList.module.css';

interface StopListProps {
  stops: Stop[];
  selectedStopId?: string | null;
  onSelectStop: (id: string) => void;
  onViewDetail: (id: string) => void;
}

export function StopList({ stops, selectedStopId, onSelectStop, onViewDetail }: StopListProps) {
  return (
    <div className={styles.list}>
      {stops.length === 0 ? (
        <p className={styles.empty}>No stops found nearby</p>
      ) : (
        stops.map((stop) => (
          <StopCard
            key={stop.stopId}
            stop={stop}
            selected={stop.stopId === selectedStopId}
            onClick={() => onSelectStop(stop.stopId)}
            onViewDetail={() => onViewDetail(stop.stopId)}
          />
        ))
      )}
    </div>
  );
}
