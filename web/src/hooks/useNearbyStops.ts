import { useEffect, useState, useRef } from 'react';
import { getNearbyStops } from '../firebase/stops';
import type { Stop } from '../types/transit';

const MIN_RELOAD_DISTANCE_KM = 0.3;

export function useNearbyStops(
  lat: number | null,
  lng: number | null,
  forceKey: number = 0,
) {
  const [stops, setStops] = useState<Stop[]>([]);
  const [loading, setLoading] = useState(false);
  const lastFetchPos = useRef<{ lat: number; lng: number } | null>(null);
  const lastForceKey = useRef(forceKey);

  useEffect(() => {
    if (!lat || !lng) return;

    const forceRefetch = forceKey !== lastForceKey.current;
    lastForceKey.current = forceKey;

    const shouldRefetch =
      forceRefetch ||
      !lastFetchPos.current ||
      haversineDist(lat, lng, lastFetchPos.current.lat, lastFetchPos.current.lng) >= MIN_RELOAD_DISTANCE_KM;

    if (!shouldRefetch) return;

    lastFetchPos.current = { lat, lng };
    let cancelled = false;
    setLoading(true);
    getNearbyStops(lat, lng)
      .then((results) => {
        if (cancelled) return;
        setStops(results);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        console.error('useNearbyStops fetch failed:', err);
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [lat, lng, forceKey]);

  return { stops, loading };
}

function haversineDist(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}
