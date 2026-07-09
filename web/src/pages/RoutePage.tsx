import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Navbar } from '../components/UI/Navbar';
import { LoadingSpinner } from '../components/UI/LoadingSpinner';
import { getRouteWithStops, resolveStopIdNear, type RouteWithStops } from '../api/transitland';
import styles from './RoutePage.module.css';

export function RoutePage() {
  const { routeId } = useParams<{ routeId: string }>();
  const navigate = useNavigate();
  const [route, setRoute] = useState<RouteWithStops | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [resolvingIndex, setResolvingIndex] = useState<number | null>(null);

  useEffect(() => {
    if (!routeId) return;
    setLoading(true);
    setError(null);
    getRouteWithStops(routeId)
      .then((r) => {
        if (!r) setError('Route not found');
        setRoute(r);
      })
      .catch(() => setError('Failed to load route'))
      .finally(() => setLoading(false));
  }, [routeId]);

  const handleStopClick = async (index: number, lat: number, lng: number) => {
    setResolvingIndex(index);
    const stopId = await resolveStopIdNear(lat, lng);
    setResolvingIndex(null);
    if (stopId) navigate(`/stop/${encodeURIComponent(stopId)}`);
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.content}>
        <button className={styles.backBtn} onClick={() => navigate('/')}>
          ← Back to Map
        </button>

        {error || !route ? (
          <h1>{error ?? 'Route not found'}</h1>
        ) : (
          <>
            <div className={styles.header} style={{ borderColor: route.color }}>
              <span className={styles.badge} style={{ background: route.color, color: route.textColor }}>
                {route.shortName || route.transitType}
              </span>
              <h1>{route.longName || route.shortName}</h1>
            </div>
            {route.agencyName && (
              <p className={styles.agency}>Service Provider: {route.agencyName}</p>
            )}
            <h2 className={styles.stopsHeading}>{route.stops.length} stops</h2>
            <ul className={styles.stopList}>
              {route.stops.map((stop, index) => (
                <li
                  key={index}
                  className={styles.stopRow}
                  onClick={() => handleStopClick(index, stop.lat, stop.lng)}
                >
                  <span className={styles.stopDot} style={{ background: route.color }} />
                  <span className={styles.stopName}>{stop.name || `Stop ${index + 1}`}</span>
                  {resolvingIndex === index && <span className={styles.spinner} />}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}
