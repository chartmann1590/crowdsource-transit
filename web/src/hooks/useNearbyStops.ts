import { useEffect, useState } from 'react';
import { getNearbyStops } from '../firebase/stops';
import type { Stop } from '../types/transit';

export function useNearbyStops(
  lat: number | null,
  lng: number | null,
) {
  const [stops, setStops] = useState<Stop[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!lat || !lng) return;
    setLoading(true);
    getNearbyStops(lat, lng).then((results) => {
      setStops(results);
      setLoading(false);
    });
  }, [lat, lng]);

  return { stops, loading };
}
